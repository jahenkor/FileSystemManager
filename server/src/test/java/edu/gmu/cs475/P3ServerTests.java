package edu.gmu.cs475;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.rmi.RemoteException;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import edu.gmu.cs475.internal.ServerMain;

public class P3ServerTests {
	FileTagManagerServer server;
	@Rule
	public Timeout globalTimeout = new Timeout(12000);
	
	@Before
	public void setup() throws IOException {
		server = new FileTagManagerServer();
		server.init(Files.walk(ServerMain.BASEDIR).filter(Files::isRegularFile).collect(Collectors.toList()));
	}

	/**
	 * This test will see that a lock expires after the stated time of no
	 * heartbeats
	 * 
	 */
	@Test
	public void testLockExpires() throws RemoteException {
		try {
			String file = server.listAllFiles().iterator().next();
			long stamp = server.lockFile(file, true);
			Thread.sleep(4000);
			boolean caught = false;
			try {
				server.unLockFile(file, stamp, true);
			} catch (IllegalMonitorStateException ex) {
				/* Expected */
				caught = true;
			}
			assertTrue("IllegalMonitorStateException should be thrown after lock times out", caught);
			assertTrue("There should be no active locks", server.getWriteLockedFiles().isEmpty());
			assertTrue("There should be no active locks", server.getReadLockedFiles().isEmpty());
		} catch (IllegalMonitorStateException | NoSuchFileException | InterruptedException | RemoteException ex) {
			ex.printStackTrace();
			fail("Unexpected exception");
		}

	}

	/**
	 * This test will try to keep a lock alive by sending heartbeats at the
	 * appropriate interval
	 * 
	 */
	@Test
	public void testLockHeartbeatsRenewLock() throws RemoteException {
		try {
			String file = server.listAllFiles().iterator().next();
			long stamp = server.lockFile(file, true);
			Thread.sleep(2000);
			server.heartbeat(file, stamp, true);
			Thread.sleep(2000);
			server.heartbeat(file, stamp, true);
			Thread.sleep(2000);
			server.unLockFile(file, stamp, true);
			assertTrue("There should be no active locks", server.getWriteLockedFiles().isEmpty());
			assertTrue("There should be no active locks", server.getReadLockedFiles().isEmpty());
		} catch (IllegalMonitorStateException | NoSuchFileException | InterruptedException | RemoteException ex) {
			ex.printStackTrace();
			fail("Unexpected exception");
		}
	}

	/**
	 * This test will send heartbeats at an irregular interval (every 1 second),
	 * testing that each heartbeat renews the countdown timer to expire the lock
	 * by the full amount
	 * 
	 */
	@Test
	public void testIrregularHeartbeatStillRenewLock() throws RemoteException  {
		try {
			String file = server.listAllFiles().iterator().next();
			long stamp = server.lockFile(file, true);
			Thread.sleep(1000);
			server.heartbeat(file, stamp, true);
			Thread.sleep(1000);
			server.heartbeat(file, stamp, true);
			Thread.sleep(2500);
			server.unLockFile(file, stamp, true);
			assertTrue("There should be no active locks", server.getWriteLockedFiles().isEmpty());
			assertTrue("There should be no active locks", server.getReadLockedFiles().isEmpty());
		} catch (IllegalMonitorStateException | NoSuchFileException | InterruptedException | RemoteException ex) {
			ex.printStackTrace();
			fail("Unexpected exception");
		}
	}

	/**
	 * This test will take out several read locks on the same file and let some
	 * expire. It will check that the correct lock got canceled.
	 * 
	 */
	@Test
	public void testHeartbeatOnlyExpiresCorrectLock() throws RemoteException {
		try {
			String file = server.listAllFiles().iterator().next();
			long stamp1 = server.lockFile(file, false);
			long stamp2 = server.lockFile(file, false);
			long stamp3 = server.lockFile(file, false);
			long stamp4 = server.lockFile(file, false);
			Thread.sleep(2000);
			server.heartbeat(file, stamp2, false);
			server.heartbeat(file, stamp3, false);
			server.heartbeat(file, stamp4, false);
			Thread.sleep(2000);
			server.heartbeat(file, stamp2, false);
			server.heartbeat(file, stamp3, false);
			server.heartbeat(file, stamp4, false);
			server.unLockFile(file, stamp2, false);
			boolean caught = false;
			try {
				server.unLockFile(file, stamp1, false);
			} catch (IllegalMonitorStateException ex) {
				// expected
				caught = true;
			}
			server.unLockFile(file, stamp3, false);
			server.unLockFile(file, stamp4, false);
			assertTrue("stamp1 should have become invalid", caught);
		} catch (IllegalMonitorStateException | NoSuchFileException | InterruptedException | RemoteException ex) {
			ex.printStackTrace();
			fail("Unexpected exception");
		}
	}

	/**
	 * This test will try to send a variety of invalid heartbeats, making sure
	 * they throw the appropriate exceptions
	 * 
	 * @throws Exception
	 */
	@Test
	public void testHeartbeatInvalidStamp() throws RemoteException {
		String file = server.listAllFiles().iterator().next();
		long stamp1 = -1;
		try {
			stamp1 = server.lockFile(file, false);
			boolean caught = false;
			try {
				server.heartbeat(file, stamp1 + 1, false);
			} catch (IllegalMonitorStateException ex) {
				caught = true;
			}
			assertTrue("Expected illegal monitor state exception to be thrown", caught);
			try {
				server.heartbeat(file, stamp1, true);
			} catch (IllegalMonitorStateException ex) {
				caught = true;
			}
			assertTrue("Expected illegal monitor state exception to be thrown", caught);
			try {
				server.heartbeat(file + ".fake", stamp1, false);
			} catch (NoSuchFileException ex) {
				caught = true;
			}
			assertTrue("Expected NoSuchFileException to be thrown", caught);
		} catch (IllegalMonitorStateException | NoSuchFileException | RemoteException ex) {
			ex.printStackTrace();
			fail("Unexpected exception");
		} finally {
			if (stamp1 >= 0)
				try {
					server.unLockFile(file, stamp1, false);
				} catch (NoSuchFileException e) {
					e.printStackTrace();
					fail("Unexpected exception");
				}
		}
	}

	/**
	 * This test will try to send a variety of invalid unlocks, making sure
	 * they throw the appropriate exceptions
	 * 
	 * @throws Exception
	 */
	@Test
	public void testUnlockInvalidStamp() throws RemoteException {
		String file = server.listAllFiles().iterator().next();
		long stamp1 = -1;
		try {
			stamp1 = server.lockFile(file, false);
			boolean caught = false;
			try {
				server.unLockFile(file, stamp1 + 1, false);
			} catch (IllegalMonitorStateException ex) {
				caught = true;
			}
			assertTrue("Expected illegal monitor state exception to be thrown", caught);
			try {
				server.unLockFile(file, stamp1, true);
			} catch (IllegalMonitorStateException ex) {
				caught = true;
			}
			assertTrue("Expected illegal monitor state exception to be thrown", caught);
			try {
				server.unLockFile(file + ".fake", stamp1, false);
			} catch (NoSuchFileException ex) {
				caught = true;
			}
			assertTrue("Expected NoSuchFileException to be thrown", caught);
		} catch (IllegalMonitorStateException | NoSuchFileException ex) {
			ex.printStackTrace();
			fail("Unexpected exception");
		} finally {
			if (stamp1 >= 0)
				try {
					server.unLockFile(file, stamp1, false);
				} catch (NoSuchFileException e) {
					e.printStackTrace();
					fail("Unexpected exception");
				}
		}
	}
}
