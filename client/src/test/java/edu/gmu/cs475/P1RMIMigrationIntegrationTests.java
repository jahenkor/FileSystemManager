package edu.gmu.cs475;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.net.ServerSocket;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import edu.gmu.cs475.struct.NoSuchTagException;
import edu.gmu.cs475.struct.TagExistsException;

public class P1RMIMigrationIntegrationTests {
	AbstractFileTagManagerClient fileManager;

	@Rule
	public Timeout globalTimeout = new Timeout(1000);

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

	@Test
	public void testAddTag() throws Exception {
		fileManager.addTag("foo");
		Iterable<String> res = fileManager.listTags();
		boolean foundFoo = false;
		for (String t : res) {
			if (t.equals("foo")) {
				foundFoo = true;
			} else if (!t.equals("untagged")) {
				fail("Unexpected tag found: " + t);
			}
		}
		assertTrue("Created tag exists", foundFoo);
	}

	@Test
	public void testAddTagAlreadyExists() throws Exception {
		fileManager.addTag("foo");
		boolean exception = false;
		try {
			fileManager.addTag("foo");
		} catch (TagExistsException ex) {
			exception = true;
		}
		assertTrue("Expected an exception to be thrown", exception);
	}

	@Test
	public void testEditTag() throws Exception {
		fileManager.addTag("foo");
		fileManager.editTag("foo", "bar");
		Iterable<String> res = fileManager.listTags();
		HashSet<String> tags = new HashSet<>();
		for(String s : res)
			tags.add(s);
		assertTrue("Expected tag 'bar' to exist",tags.remove("bar"));
		assertTrue("Expected tag 'untagged' to exist",tags.remove("untagged"));
		assertTrue("Expected no tags but bar and untagged, found " + tags, tags.isEmpty());

	}

	@Test(expected = NoSuchTagException.class)
	public void testEditNonExistantTag() throws Exception {
		fileManager.editTag("foo", "bar");
	}

	@Test
	public void testEditTagAlreadyExists() throws Exception {
		fileManager.addTag("foo");
		fileManager.addTag("bar");
		boolean exception = false;
		try {
			fileManager.editTag("foo", "bar");
		} catch (TagExistsException ex) {
			exception = true;
		}
		assertTrue("Expected an exception to be thrown", exception);
	}

	@Test
	public void testDeleteTag() throws Exception {
		fileManager.addTag("foo");
		fileManager.deleteTag("foo");
		Iterable<String> res = fileManager.listTags();
		Iterator<String> iterator = res.iterator();
		assertEquals("untagged",iterator.next());
		assertFalse(iterator.hasNext());
	}

	@Test(expected = NoSuchTagException.class)
	public void testDeleteTagDoesntExist() throws Exception {
		fileManager.deleteTag("foo");
	}

	@Test
	public void testDeleteTagNotEmpty() throws Exception {
		fileManager.addTag("foo");
		String fileName = getAFile();
		fileManager.tagFile(fileName, "foo");
		boolean caught = false;
		try {
			fileManager.deleteTag("foo");
		} catch (DirectoryNotEmptyException ex) {
			caught = true;
		}
		boolean found = false;
		for (String t : fileManager.listTags())
			if (t.equals("foo"))
				found = true;
		assertTrue("DirectoryNotEmptyException expected", caught);
		assertTrue("Not empty tag should not have been deleted", found);
	}


	@Test
	public void testTagFile() throws Exception {
		String fileName = getAFile();
		fileManager.addTag("foo");
		fileManager.tagFile(fileName, "foo");
		Iterator<String> tags = fileManager.getTags(fileName).iterator();
		assertEquals("foo", tags.next());
		assertFalse(tags.hasNext());

		Iterable<String> files = fileManager.listFilesByTag("foo");
		Iterator<String> iter = files.iterator();
		String file = iter.next();
		assertEquals(file, fileName);
		assertFalse(iter.hasNext());

	}

	@Test
	public void testTagFileAlreadyExists() throws Exception {
		String fileName = getAFile();
		fileManager.addTag("foo");
		fileManager.tagFile(fileName, "foo");
		assertFalse(fileManager.tagFile(fileName, "foo"));
	}

	@Test(expected = NoSuchTagException.class)
	public void testTagFileNoSuchTag() throws Exception {
		String fileName = getAFile();
		fileManager.tagFile(fileName, "foo");
	}

	@Test
	public void testRemoveTag() throws Exception {
		String fileName = getAFile();
		fileManager.addTag("foo");
		fileManager.tagFile(fileName, "foo");
		fileManager.removeTag(fileName, "foo");

		Iterator<String> tags = fileManager.getTags(fileName).iterator();
		assertEquals("untagged", tags.next());
		assertFalse(tags.hasNext());
	}

	@Test
	public void testRemoveUntagged() throws Exception {
		String fileName = getAFile();
		assertFalse("Removing the untagged tag should be impossible", fileManager.removeTag(fileName, "untagged"));
	}

	@Test(expected = NoSuchTagException.class)
	public void testRemoveTagDoesntExist() throws Exception {
		String fileName = getAFile();
		assertFalse(fileManager.removeTag(fileName, "foo"));
	}

	@Test(expected = NoSuchFileException.class)
	public void testRemoveTagNoFileExist() throws Exception {
		fileManager.addTag("foo");
		assertFalse(fileManager.removeTag("blah", "foo"));
	}

}
