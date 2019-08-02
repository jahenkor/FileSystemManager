package edu.gmu.cs475;

import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.NoSuchFileException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;

import edu.gmu.cs475.struct.NoSuchTagException;
import edu.gmu.cs475.struct.TagExistsException;

public abstract class AbstractFileTagManagerClient {

	public IFileTagManager tagServer;

	public Iterable<String> listTags() throws RemoteException {
		return tagServer.listTags();
	}

	public String addTag(String name) throws RemoteException, TagExistsException {
		return tagServer.addTag(name);
	}

	public String editTag(String oldTagName, String newTagName) throws RemoteException, TagExistsException, NoSuchTagException {
		return tagServer.editTag(oldTagName, newTagName);
	}

	public String deleteTag(String tagName) throws RemoteException, NoSuchTagException, DirectoryNotEmptyException {
		return tagServer.deleteTag(tagName);
	}

	public Iterable<String> listAllFiles() throws RemoteException {
		return tagServer.listAllFiles();
	}

	public Iterable<String> listFilesByTag(String tag) throws RemoteException, NoSuchTagException {
		return tagServer.listFilesByTag(tag);
	}

	public boolean tagFile(String file, String tag) throws RemoteException, NoSuchFileException, NoSuchTagException {
		return tagServer.tagFile(file, tag);
	}

	public boolean removeTag(String file, String tag) throws RemoteException, NoSuchFileException, NoSuchTagException {
		return tagServer.removeTag(file, tag);
	}

	public Iterable<String> getTags(String file) throws RemoteException, NoSuchFileException {
		return tagServer.getTags(file);
	}

	public String readFile(String file) throws RemoteException, IOException {
		return tagServer.readFile(file);
	}

	public void writeFile(String file, String content) throws RemoteException, IOException {
		tagServer.writeFile(file, content);
	}

	protected AbstractFileTagManagerClient(String host, int port) {
		try {
			Registry registry = LocateRegistry.getRegistry(host, port);
			tagServer = (IFileTagManager) registry.lookup(IFileTagManager.RMI_NAME);
		} catch (Exception e) {
			System.err.println("Client exception connecting to lock server: " + e.toString());
			e.printStackTrace();
		}
	}

	protected AbstractFileTagManagerClient(IFileTagManager server) {
		this.tagServer = server;
	}

	/**
	 * Prints out all files that have a given tqg. Must internally synchronize
	 * to guarantee that the list of files with the given tag does not change
	 * during its call, and that each file printed does not change during its
	 * execution (using a read/write lock). You should acquire all of the locks,
	 * then read all of the files and release the locks. Your could should not
	 * deadlock while waiting to acquire locks.
	 *
	 * @param tag
	 *            Tag to query for
	 * @return The concatenation of all of the files
	 * @throws NoSuchTagException
	 *             If no tag exists with the given name
	 * @throws IOException
	 *             if any IOException occurs in the underlying read, or if the
	 *             read was unsuccessful (e.g. if it times out, or gets
	 *             otherwise disconnected during the execution
	 * 
	 */
	public abstract String catAllFiles(String tag) throws NoSuchTagException, IOException;

	/**
	 * Echos some content into all files that have a given tag. Must internally
	 * synchronize to guarantee that the list of files with the given tag does
	 * not change during its call, and that each file being printed to does not
	 * change during its execution (using a read/write lock)
	 * 
	 * Given two concurrent calls to echoToAllFiles, it will be indeterminate
	 * which call happens first and which happens last. But what you can (and
	 * must) guarantee is that all files will have the *same* value (and not
	 * some the result of the first, qnd some the result of the second). Your
	 * could should not deadlock while waiting to acquire locks.
	 * 
	 * @param tag
	 *            Tag to query for
	 * @param content
	 *            The content to write out to each file
	 * @throws NoSuchTagException
	 *             If no tag exists with the given name
	 * @throws IOException
	 *             if any IOException occurs in the underlying write, or if the
	 *             write was unsuccessful (e.g. if it times out, or gets
	 *             otherwise disconnected during the execution)
	 * 
	 */
	public abstract void echoToAllFiles(String tag, String content) throws NoSuchTagException, IOException;

	/**
	 * A callback for you to implement to let you know that a lock was
	 * successfully acquired, and that you should start sending heartbeats for
	 * it
	 * 
	 * @param name
	 * @param forWrite
	 * @param stamp
	 */
	public abstract void lockFileSuccess(String name, boolean forWrite, long stamp);

	/**
	 * A callback for you to implement to let you know a lock was relinquished,
	 * and that you should stop sending heartbeats for it.
	 * 
	 * @param name
	 * @param forWrite
	 * @param stamp
	 */
	public abstract void unLockFileCalled(String name, boolean forWrite, long stamp);

	public final long lockFile(String name, boolean forWrite) throws RemoteException, NoSuchFileException {
		long ret = tagServer.lockFile(name, forWrite);
		lockFileSuccess(name, forWrite, ret);
		return ret;
	}

	public final void unLockFile(String name, long stamp, boolean forWrite) throws RemoteException, NoSuchFileException, IllegalMonitorStateException {
		unLockFileCalled(name, forWrite, stamp);
		tagServer.unLockFile(name, stamp, forWrite);
	}

	public void heartbeat(String file, long stampId, boolean isWrite) throws RemoteException, IllegalMonitorStateException, NoSuchFileException {
		tagServer.heartbeat(file, stampId, isWrite);
	}

	public List<String> getWriteLockedFiles() throws RemoteException {
		return tagServer.getWriteLockedFiles();
	}

	public List<String> getReadLockedFiles() throws RemoteException {
		return tagServer.getReadLockedFiles();
	}

}
