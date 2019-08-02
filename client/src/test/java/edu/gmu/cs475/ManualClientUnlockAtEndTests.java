package edu.gmu.cs475;

import org.easymock.*;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static org.easymock.EasyMock.*;

public class ManualClientUnlockAtEndTests {

	AbstractFileTagManagerClient fileManager;

	@Rule
	public Timeout globalTimeout = new Timeout(12000);

	@Mock
	private IFileTagManager server;

	private static final int N_FILES = 2;

	@Before
	public void setup() throws Exception {
		IMocksControl mocker = EasyMock.createControl();
		mocker.resetToStrict();
		server = mocker.createMock(IFileTagManager.class);
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

	@Test
	public void testEchoAllLocksThenWritesThenUnlocks() throws Exception
	{
		LinkedList<String> files = new LinkedList<>();
		String fileName1 = "testManual1.file0";
		files.add(fileName1);
		String fileName2= "testManual1.file1";
		files.add(fileName2);
		expect(server.listFilesByTag("untagged")).andReturn(files).once();
		expect(server.lockFile(anyString(), eq(true))).andReturn(101L).once();
		expect(server.lockFile(anyString(), eq(true))).andReturn(102L).once();
		server.writeFile(anyString(), eq("content"));
		expectLastCall().once();
		server.writeFile(anyString(), eq("content"));
		expectLastCall().once();
		server.unLockFile(anyString(), anyLong(), eq(true));
		expectLastCall().once();
		server.unLockFile(anyString(), anyLong(), eq(true));
		expectLastCall().once();

		replay(server);
		try {
			fileManager.echoToAllFiles("untagged", "content");
		} catch (IOException ex) {
			// OK, IOException expected
		}
		verify(server);
	}

	@Test
	public void testCatAllLocksThenReadsThenUnlocks() throws Exception
	{
		LinkedList<String> files = new LinkedList<>();
		String fileName1 = "testManual1.file0";
		files.add(fileName1);
		String fileName2= "testManual1.file1";
		files.add(fileName2);
		expect(server.listFilesByTag("untagged")).andReturn(files).once();
		expect(server.lockFile(anyString(), eq(false))).andReturn(101L).once();
		expect(server.lockFile(anyString(), eq(false))).andReturn(102L).once();
		expect(server.readFile(anyString())).andReturn("foo").once();
		expect(server.readFile(anyString())).andReturn("foo").once();
		server.unLockFile(anyString(), anyLong(), eq(false));
		expectLastCall().once();
		server.unLockFile(anyString(), anyLong(), eq(false));
		expectLastCall().once();

		replay(server);
		try {
			fileManager.catAllFiles("untagged");
		} catch (IOException ex) {
			// OK, IOException expected
		}
		verify(server);
	}

	static class anyStringMatchingString implements IArgumentMatcher
	{
		private Capture<String> expected;
		int i;
		boolean called;
		public anyStringMatchingString(Capture<String> s,int i){
			this.expected = s;

		}

		private String m;
		@Override
		public boolean matches(Object argument) {
			this.m = (String)argument;
			this.called = true;
			return expected.getValue().equals(argument);
		}

		@Override
		public void appendTo(StringBuffer buffer) {
			if(called)
				buffer.append("expected "+expected.getValue() +" as locked file #"+i+", not "+m);
			else
				buffer.append("any string");

		}
	}
	private static String anyStringMatchingString(Capture<String> s,int i){
		EasyMock.reportMatcher(new anyStringMatchingString(s,i));
		return null;
	}
	@Test
	public void testCatAllLocksSameOrder() throws Exception
	{
		LinkedList<String> files = new LinkedList<>();
		for(int i =0;i<100;i++)
			files.add("File"+i);
		expect(server.listFilesByTag("untagged")).andReturn(files).once();
		LinkedList<Capture<String>> locks = new LinkedList<>();
		for(int i = 0; i < 100; i++) {

			Capture<String> file = EasyMock.newCapture();
			expect(server.lockFile(capture(file), eq(false))).andReturn(100L + i).once();
			locks.add(file);
		}
		expect(server.readFile(anyString())).andReturn("foo").times(100);
		server.unLockFile(anyString(), anyLong(), eq(false));
		expectLastCall().times(100);


		List<String> files2 = new LinkedList<String>(files);
		Collections.shuffle(files2);
		expect(server.listFilesByTag("untagged")).andReturn(files2).once();
		for(int i = 0; i < 100; i++) {
			expect(server.lockFile(anyStringMatchingString(locks.get(i),i),eq(false))).andReturn(500L+i).once();
		}
		expect(server.readFile(anyString())).andReturn("foo").times(100);
		server.unLockFile(anyString(), anyLong(), eq(false));
		expectLastCall().times(100);

		replay(server);
		try {
			fileManager.catAllFiles("untagged");
		} catch (IOException ex) {
			// OK, IOException expected
		}
		try {
			fileManager.catAllFiles("untagged");
		} catch (IOException ex) {
			// OK, IOException expected
		}
		try {
			verify(server);
		} catch (AssertionError er) {
			String[] lines = er.getMessage().split("\n");
			for (int i = 0; i < Math.min(5, lines.length); i++) {
				System.err.println(lines[i]);
			}
			if (lines.length > 5)
				System.err.println("\t... and " + (lines.length - 5) + " more");
			Assert.fail();
		}
	}
	@Test
	public void testEchoAllLocksSameOrder() throws Exception
	{
		LinkedList<String> files = new LinkedList<>();
		for(int i =0;i<100;i++)
			files.add("File"+i);
		expect(server.listFilesByTag("untagged")).andReturn(files).once();
		LinkedList<Capture<String>> locks = new LinkedList<>();
		for(int i = 0; i < 100; i++) {

			Capture<String> file = EasyMock.newCapture();
			expect(server.lockFile(capture(file), eq(true))).andReturn(100L + i).once();
			locks.add(file);
		}
		server.writeFile(anyString(), eq("content"));
		expectLastCall().times(100);
		server.unLockFile(anyString(), anyLong(), eq(true));
		expectLastCall().times(100);


		List<String> files2 = new LinkedList<String>(files);
		Collections.shuffle(files2);
		expect(server.listFilesByTag("untagged")).andReturn(files2).once();
		for(int i = 0; i < 100; i++) {
			expect(server.lockFile(anyStringMatchingString(locks.get(i),i),eq(true))).andReturn(500L+i).once();
		}
		server.writeFile(anyString(), eq("content"));
		expectLastCall().times(100);
		server.unLockFile(anyString(), anyLong(), eq(true));
		expectLastCall().times(100);

		replay(server);
		try {
			fileManager.echoToAllFiles("untagged","content");
		} catch (IOException ex) {
			// OK, IOException expected
		}
		try {
			fileManager.echoToAllFiles("untagged","content");
		} catch (IOException ex) {
			// OK, IOException expected
		}
		try {
			verify(server);
		} catch (AssertionError er) {
			String[] lines = er.getMessage().split("\n");
			for (int i = 0; i < Math.min(5, lines.length); i++) {
				System.err.println(lines[i]);
			}
			if (lines.length > 5)
				System.err.println("\t... and " + (lines.length - 5) + " more");
			Assert.fail();
		}
	}



}
