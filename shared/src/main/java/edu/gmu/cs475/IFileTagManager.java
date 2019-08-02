package edu.gmu.cs475;

import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.NoSuchFileException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

import edu.gmu.cs475.struct.NoSuchTagException;
import edu.gmu.cs475.struct.TagExistsException;

public interface IFileTagManager extends Remote {
	public static final String RMI_NAME = "cs475IFileTagManager";

	/**
	 * List all currently known tags.
	 * 
	 * @return List of tags (in any order)
	 */
	public Iterable<String> listTags() throws RemoteException;

	/**
	 * Add a new tag to the list of known tags
	 * 
	 * @param name
	 *            Name of tag
	 * @return The newly created Tag name
	 * @throws TagExistsException
	 *             If a tag already exists with this name
	 */
	public String addTag(String name) throws RemoteException, TagExistsException;

	/**
	 * Update the name of a tag, also updating any references to that tag to
	 * point to the new one
	 * 
	 * @param oldTagName
	 *            Old name of tag
	 * @param newTagName
	 *            New name of tag
	 * @return The newly updated Tag name
	 * @throws TagExistsException
	 *             If a tag already exists with the newly requested name
	 * @throws NoSuchTagException
	 *             If no tag exists with the old name
	 */
	public String editTag(String oldTagName, String newTagName) throws RemoteException, TagExistsException, NoSuchTagException;

	/**
	 * Delete a tag by name
	 * 
	 * @param tagName
	 *            Name of tag to delete
	 * @return The tag name that was deleted
	 * @throws NoSuchTagException
	 *             If no tag exists with that name
	 * @throws DirectoryNotEmptyException
	 *             If tag currently has files still associated with it
	 */
	public String deleteTag(String tagName) throws RemoteException, NoSuchTagException, DirectoryNotEmptyException;

	/**
	 * List all files, regardless of their tag
	 * 
	 * @return A list of all files. Each file must appear exactly once in this
	 *         list.
	 */
	public Iterable<String> listAllFiles() throws RemoteException;

	/**
	 * List all files that have a given tag
	 * 
	 * @param tag
	 *            Tag to look for
	 * @return A list of all files that have been labeled with the specified tag
	 * @throws NoSuchTagException
	 *             If no tag exists with that name
	 */
	public Iterable<String> listFilesByTag(String tag) throws RemoteException, NoSuchTagException;

	/**
	 * Label a file with a tag
	 * 
	 * Files can have any number of tags - this tag will simply be appended to
	 * the collection of tags that the file has. However, files can be tagged
	 * with a given tag exactly once: repeatedly tagging a file with the same
	 * tag should return "false" on subsequent calls.
	 * 
	 * If the file currently has the special tag "untagged" then that tag should
	 * be removed - otherwise, this tag is appended to the collection of tags on
	 * this file.
	 * 
	 * @param file
	 *            Path to file to tag
	 * @param tag
	 *            The desired tag
	 * @returns true if succeeding tagging the file, false if the file already
	 *          has that tag
	 * @throws NoSuchFileException
	 *             If no file exists with the given name/path
	 * @throws NoSuchTagException
	 *             If no tag exists with the given name
	 */
	public boolean tagFile(String file, String tag) throws RemoteException, NoSuchFileException, NoSuchTagException;

	/**
	 * Remove a tag from a file
	 * 
	 * If removing this tag causes the file to no longer have any tags, then the
	 * special "untagged" tag should be added.
	 * 
	 * The "untagged" tag can not be removed (return should be false)
	 * 
	 * @param file
	 *            Path to file to untag
	 * @param tag
	 *            The desired tag to remove from that file
	 * @returns True if the tag was successfully removed, false if there was no
	 *          tag by that name on the specified file
	 * @throws NoSuchFileException
	 *             If no file exists with the given name/path
	 * @throws NoSuchTagException
	 *             If no tag exists with the given name
	 */
	public boolean removeTag(String file, String tag) throws RemoteException, NoSuchFileException, NoSuchTagException;

	/**
	 * List all of the tags that are applied to a file
	 * 
	 * @param file
	 *            The file to inspect
	 * @return A list of all tags that have been applied to that file in any
	 *         order
	 * @throws NoSuchFileException
	 *             If the file specified does not exist
	 */
	public Iterable<String> getTags(String file) throws RemoteException, NoSuchFileException;

	/**
	 * Return a file as a byte array.
	 * 
	 * @param file
	 *            Path to file requested
	 * @return String representing the file
	 * @throws IOException
	 *             if any IOException occurs in the underlying read
	 */
	public String readFile(String file) throws RemoteException, IOException;

	/**
	 * Write (or overwrite) a file
	 *
	 * @param file
	 *            Path to file to write out
	 * @param content
	 *            String representing the content desired
	 * @throws IOException
	 *             if any IOException occurs in the underlying write
	 */
	public void writeFile(String file, String content) throws RemoteException, IOException;

	/**
	 * Acquires a read or write lock for a given file.
	 * 
	 * @param name
	 *            File to lock
	 * @param forWrite
	 *            True if a write lock is requested, else false
	 * @return A stamp representing the lock owner (e.g. from a StampedLock)
	 * @throws NoSuchFileException
	 *             If the file doesn't exist
	 */
	public long lockFile(String name, boolean forWrite) throws RemoteException, NoSuchFileException;

	/**
	 * Releases a read or write lock for a given file.
	 * 
	 * @param name
	 *            File to lock
	 * @param stamp
	 *            the Stamp representing the lock owner (returned from lockFile)
	 * @param forWrite
	 *            True if a write lock is requested, else false
	 * @throws NoSuchFileException
	 *             If the file doesn't exist
	 * @throws IllegalMonitorStateException
	 *             if the stamp specified is not (or is no longer) valid
	 */
	public void unLockFile(String name, long stamp, boolean forWrite) throws RemoteException, NoSuchFileException, IllegalMonitorStateException;

	/**
	 * Notifies the server that the client is still alive and well, still using
	 * the lock specified by the stamp provided
	 * 
	 * @param file
	 *            The filename (same exact name passed to lockFile) that we are
	 *            reporting in on
	 * @param stampId
	 *            Stamp returned from lockFile that we are reporting in on
	 * @param isWrite
	 *            if the heartbeat is for a write lock
	 * @throws IllegalMonitorStateException
	 *             if the stamp specified is not (or is no longer) valid, or if
	 *             the stamp is not valid for the given read/write state
	 * @throws NoSuchFileException
	 *             if the file specified doesn't exist
	 */
	public void heartbeat(String file, long stampId, boolean isWrite) throws RemoteException, IllegalMonitorStateException, NoSuchFileException;

	/**
	 * Get a list of all of the files that are currently write locked
	 */
	public List<String> getWriteLockedFiles() throws RemoteException;

	/**
	 * Get a list of all of the files that are currently write locked
	 */
	public List<String> getReadLockedFiles() throws RemoteException;

}
