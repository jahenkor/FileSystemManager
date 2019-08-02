package edu.gmu.cs475;

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.mock;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;

import org.easymock.IAnswer;
import org.easymock.Mock;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

public class P2HeartbeatClientTests {
	AbstractFileTagManagerClient fileManager;

	@Rule
	public Timeout globalTimeout = new Timeout(12000);
	
	@Mock
	private IFileTagManager server;

	private static final int N_FILES = 2;

	@Before
	public void setup() throws Exception {
		server = mock(IFileTagManager.class);
		fileManager = new FileTagManagerClient(server);
		expect(server.listAllFiles()).andAnswer(new IAnswer<Iterable<String>>() {

			@Override
			public Iterable<String> answer() throws Throwable {
				ArrayList<String> ret = new ArrayList<>();
				for (int i = 0; i < N_FILES; i++)
					ret.add("file" + i);
				return ret;
			}
		}).anyTimes();
	}

	/**
	 * This test will take a write lock, then wait 5 seconds. In that time,
	 * there should be 2 heartbeats. Then, it will unlock the file, and sleep
	 * another 5 seconds, in which there should be 0 heartbeats.
	 * 
	 * @throws Exception
	 */
	@Test
	public void testLockSleepHeartbeatUnlock() throws Exception {
		String fileName = "testLockSleepHeartbeatUnlock.file0";
		expect(server.lockFile(fileName, true)).andReturn(101L);
		server.heartbeat(fileName, 101L, true);
		expectLastCall().times(2);
		server.unLockFile(fileName, 101L, true);
		expectLastCall();
		replay(server);
		long stamp = fileManager.lockFile(fileName, true);
		Thread.sleep(5000);
		fileManager.unLockFile(fileName, stamp, true);
		Thread.sleep(5000);

		verify(server);
	}

	/**
	 * This test will try to lock a file twice for read, then wait 5 seconds. In
	 * that time, there should be 4 heartbeats -- two from each file. Then, it
	 * will unlock the file once, wait 1 second and expect a single heartbeat,
	 * then unlock the other.
	 * 
	 * @throws Exception
	 */
	@Test
	public void testLockSameFileTwice() throws Exception {
		String fileName = "testLockSameFileTwice.file0";
		expect(server.lockFile(fileName, false)).andReturn(101L).once();
		expect(server.lockFile(fileName, false)).andReturn(102L).once();
		server.heartbeat(fileName, 101L, false);
		expectLastCall().times(2);
		server.heartbeat(fileName, 102L, false);
		expectLastCall().times(3);
		server.unLockFile(fileName, 101L, false);
		expectLastCall().once();
		server.unLockFile(fileName, 102L, false);
		expectLastCall().once();
		replay(server);
		long stamp1 = fileManager.lockFile(fileName, false);
		long stamp2 = fileManager.lockFile(fileName, false);
		Thread.sleep(5000);
		fileManager.unLockFile(fileName, stamp1, false);
		Thread.sleep(1500);
		fileManager.unLockFile(fileName, stamp2, false);
		Thread.sleep(3000);

		verify(server);
	}

	/**
	 * This test will try to cat all files, but the server will throw an
	 * IOException on one of the reads. The test will assert that you still call
	 * unlock on all files.
	 * 
	 * @throws Exception
	 */
	@Test
	public void testCatAllExceptionsCauseUnlock() throws Exception {
		LinkedList<String> files = new LinkedList<>();
		String fileName1 = "testCatAllExceptionsCauseUnlock.file0";
		files.add(fileName1);
		String fileName2 = "testCatAllExceptionsCauseUnlock.file1";
		files.add(fileName2);
		expect(server.listFilesByTag("untagged")).andReturn(files).once();
		expect(server.lockFile(fileName1, false)).andReturn(101L).once();
		expect(server.lockFile(fileName2, false)).andReturn(102L).once();
		expect(server.readFile(fileName1)).andReturn("File 0 is ok!").once();
		expect(server.readFile(fileName2)).andThrow(new IOException()).once();
		server.unLockFile(fileName1, 101L, false);
		expectLastCall().once();
		server.unLockFile(fileName2, 102L, false);
		expectLastCall().once();

		replay(server);
		try {
			fileManager.catAllFiles("untagged");
		} catch (IOException ex) {
			// OK, IOException expected
		}
		verify(server);
	}

	/**
	 * This test will try to echo to all files, but the server will throw an
	 * IOException on one of the write. The test will assert that you still call
	 * unlock on all files.
	 * 
	 * @throws Exception
	 */
	@Test
	public void testEchoAllExceptionsCauseUnlock() throws Exception {
		LinkedList<String> files = new LinkedList<>();
		String fileName1 = "testEchoAllExceptionsCauseUnlock.file0";
		files.add(fileName1);
		String fileName2= "testEchoAllExceptionsCauseUnlock.file1";
		files.add(fileName2);
		expect(server.listFilesByTag("untagged")).andReturn(files).once();
		expect(server.lockFile(fileName1, true)).andReturn(101L).once();
		expect(server.lockFile(fileName2, true)).andReturn(102L).once();
		server.writeFile(fileName1, "content");
		expectLastCall().once();
		server.writeFile(fileName2, "content");
		expectLastCall().andThrow(new IOException());
		server.unLockFile(fileName1, 101L, true);
		expectLastCall().once();
		server.unLockFile(fileName2, 102L, true);
		expectLastCall().once();

		replay(server);
		try {
			fileManager.echoToAllFiles("untagged", "content");
		} catch (IOException ex) {
			// OK, IOException expected
		}
		verify(server);
	}

	/**
	 * This test will lock a file for read twice, then throw an
	 * IllegalMonitorStateException from one of the heartbeats and make sure
	 * that neither heartbeat nor unlock get called on that file; the other one
	 * should still heartbeat.
	 * 
	 * @throws Exception
	 */
	@Test
	public void testExceptionInHeartbeatCancelsHeartbeat() throws Exception {
		String fileName = "testExceptionInHeartbeatCancelsHeartbeat.file0";
		expect(server.lockFile(fileName, false)).andReturn(101L).once();
		expect(server.lockFile(fileName, false)).andReturn(102L).once();

		server.heartbeat(fileName, 101L, false);
		expectLastCall().andThrow(new IllegalMonitorStateException());
		server.heartbeat(fileName, 102L, false);
		expectLastCall().times(2);
		server.unLockFile(fileName, 102L, false);
		expectLastCall().once();
		replay(server);

		fileManager.lockFile(fileName, false);
		fileManager.lockFile(fileName, false);
		Thread.sleep(4500);
		fileManager.unLockFile(fileName, 102L, false);
		verify(server);

	}

	/**
	 * This test will lock a file for write, then throw an
	 * IllegalMonitorStateException the heartbeats and make sure that neither
	 * heartbeat nor unlock get called on that file.
	 * 
	 * @throws Exception
	 */
	@Test
	public void testExceptionInWriteHeartbeatCancelsHeartbeat() throws Exception {
		String fileName = "testExceptionInWriteHeartbeatCancelsHeartbeat.file0";
		expect(server.lockFile(fileName, true)).andReturn(101L).once();

		server.heartbeat(fileName, 101L, true);
		expectLastCall().andThrow(new IllegalMonitorStateException());
		replay(server);

		fileManager.lockFile(fileName, true);
		Thread.sleep(4500);
		verify(server);

	}
}
