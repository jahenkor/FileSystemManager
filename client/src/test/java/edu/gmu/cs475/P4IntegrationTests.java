package edu.gmu.cs475;

import static org.easymock.EasyMock.anyLong;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.partialMockBuilder;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.LinkedList;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

public class P4IntegrationTests {
	AbstractFileTagManagerClient fileManager;

	@Rule
	public Timeout globalTimeout = new Timeout(12000);

	static Registry rmiRegistry;

	static int port;

	/**
	 * Configure a single RMI Registry to use for all tests
	 * 
	 * @throws Exception
	 */
	@BeforeClass
	public static void setupRMI() throws Exception {
		// Get a free port and set up a server, client
		try (ServerSocket socket = new ServerSocket(0)) {
			socket.setReuseAddress(true);
			port = socket.getLocalPort();
		}
		rmiRegistry = LocateRegistry.createRegistry(port);
	}

	/**
	 * Create a new server and client
	 */
	@Before
	public void setup() throws Exception {
		Path basedir = Paths.get(System.getProperty("user.dir")).getParent().resolve("server").resolve("testdir");
		FileTagManagerServer lockServer = new FileTagManagerServer();
		lockServer.init(Files.walk(basedir).filter(Files::isRegularFile).collect(Collectors.toList()));
		IFileTagManager stub = (IFileTagManager) UnicastRemoteObject.exportObject(lockServer, 0);
		rmiRegistry.rebind(IFileTagManager.RMI_NAME, stub);
		fileManager = new FileTagManagerClient("localhost", port);
	}

	@After
	public void cleanup() throws Exception {
		rmiRegistry.unbind(IFileTagManager.RMI_NAME);
	}

	private String getAFile() throws RemoteException {
		return fileManager.listAllFiles().iterator().next();
	}

	/**
	 * Take out a write lock on a single file, make sure heartbeat works
	 */
	@Test
	public void testLockFileAutoHeartbeatWorks() {
		try {
			String file = getAFile();
			long stamp = fileManager.lockFile(file, true);
			Thread.sleep(5000);
			fileManager.unLockFile(file, stamp, true);
		} catch (IllegalMonitorStateException | NoSuchFileException | InterruptedException | RemoteException ex) {
			System.out.println("testThisFunctionFirst");
                        ex.printStackTrace();
			fail("Unexpected exception");
		}
	}

	/**
	 * Take out multiple read locks, make sure they all get their heartbeats in
	 */
	@Test
	public void testLockFileMultipleReadlocksAutoHeartbeatWorks() {
		try {
			String file = getAFile();
			long stamp = fileManager.lockFile(file, false);
			long stamp1 = fileManager.lockFile(file, false);
			long stamp2 = fileManager.lockFile(file, false);
			long stamp3 = fileManager.lockFile(file, false);
			Thread.sleep(5000);
			fileManager.unLockFile(file, stamp, false);
			fileManager.unLockFile(file, stamp1, false);
			fileManager.unLockFile(file, stamp2, false);
			fileManager.unLockFile(file, stamp3, false);
		} catch (IllegalMonitorStateException | NoSuchFileException | InterruptedException | RemoteException ex) {
			ex.printStackTrace();
			fail("Unexpected exception");
		}
	}

	/**
	 * This test will prevent your client heartbeat from sending out, and then
	 * will make sure that the client's lock is removed, and that the client can
	 * re-acquire the lock
	 * 
	 */
	@Test
	public void testStallHeartbeatCanReLockLater() {
		try {
			String file = getAFile();
			AbstractFileTagManagerClient mockedClient = partialMockBuilder(FileTagManagerClient.class).withConstructor("localhost", port).addMockedMethod("heartbeat").createMock();
			mockedClient.heartbeat(eq(file), anyLong(), eq(true));
			expectLastCall().times(2,3);
			replay(mockedClient);
			long stamp = mockedClient.lockFile(file, true);
			Thread.sleep(5000);
			verify(mockedClient);

			boolean caught = false;
			try {
				mockedClient.tagServer.heartbeat(file, stamp, true);
			} catch (IllegalMonitorStateException ex) {
				caught = true;
			}
			assertTrue("Expected IllegalMonitorStateException", caught);

			caught = false;
			try {
				mockedClient.unLockFile(file, stamp, true);
			} catch (IllegalMonitorStateException ex) {
				caught = true;
			}
			assertTrue("Expected IllegalMonitorStateException", caught);

			stamp = mockedClient.lockFile(file, true); // should now be able to
														// re-acquire
			mockedClient.unLockFile(file, stamp, true);
		} catch (IllegalMonitorStateException | NoSuchFileException | InterruptedException | RemoteException ex) {
			ex.printStackTrace();
			fail("Unexpected exception");
		}
	}

	/**
	 * This test will create multiple clients, all read locking the same file.
	 * Some will be mocked to prevent their heartbeat from being delivered. We
	 * assert that the ones that succeeded in their heartbeats can unlock and
	 * the rest can't.
	 */
	@Test
	public void testMultipleClientsLockSameFileSomeFailingToHeartbeat() {
		try {
			String file = getAFile();

			AbstractFileTagManagerClient[] clients = new AbstractFileTagManagerClient[5];
			for (int i = 0; i < clients.length; i++)
				clients[i] = new FileTagManagerClient("localhost", port);
			AbstractFileTagManagerClient[] mockedClients = new AbstractFileTagManagerClient[5];
			for (int i = 0; i < mockedClients.length; i++) {
				AbstractFileTagManagerClient mockedClient = partialMockBuilder(FileTagManagerClient.class).withConstructor("localhost", port).addMockedMethod("heartbeat").createMock();
				mockedClient.heartbeat(eq(file), anyLong(), eq(true));
				expectLastCall().times(2,3);
				replay(mockedClient);
				mockedClients[i] = mockedClient;
			}
			long[] stamps = new long[clients.length];
			long[] badStamps = new long[mockedClients.length];

			for (int i = 0; i < clients.length; i++) {
				stamps[i] = clients[i].lockFile(file, false);
			}
			for (int i = 0; i < mockedClients.length; i++) {
				badStamps[i] = mockedClients[i].lockFile(file, false);
			}
			Thread.sleep(5000);
			for (int i = 0; i < clients.length; i++) {
				clients[i].unLockFile(file, stamps[i], false);
			}
			for (int i = 0; i < mockedClients.length; i++) {
				boolean caught = false;
				try {
					mockedClients[i].unLockFile(file, badStamps[i], false);
				} catch (IllegalMonitorStateException ex) {
					caught = true;
				}
				assertTrue("IllegalMonitorStateException expected from clients who didn't send heartbeat", caught);
			}
		} catch (IllegalMonitorStateException | NoSuchFileException | InterruptedException | RemoteException ex) {
			ex.printStackTrace();
			fail("Unexpected exception");
		}
	}

	/**
	 * This test will create multiple clients, all read locking the same file.
	 * Then, they will all try to unlock.
	 */
	@Test
	public void testMultipleClientsLockSameFile() {
		try {
			String file = getAFile();

			AbstractFileTagManagerClient[] clients = new AbstractFileTagManagerClient[10];
			for (int i = 0; i < clients.length; i++)
				clients[i] = new FileTagManagerClient("localhost", port);
			LinkedList<Long> stamps = new LinkedList<>();
			for (AbstractFileTagManagerClient c : clients) {
				stamps.add(c.lockFile(file, false));
			}
			Thread.sleep(5000);
			for (AbstractFileTagManagerClient c : clients) {
				c.unLockFile(file, stamps.pop(), false);
			}
		} catch (IllegalMonitorStateException | NoSuchFileException | InterruptedException | RemoteException ex) {
			ex.printStackTrace();
			fail("Unexpected exception");
		}
	}

}
