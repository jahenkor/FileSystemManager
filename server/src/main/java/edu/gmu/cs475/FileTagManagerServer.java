package edu.gmu.cs475;

import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import edu.gmu.cs475.internal.ServerMain;
import edu.gmu.cs475.struct.ITag;
import edu.gmu.cs475.struct.ITaggedFile;
import edu.gmu.cs475.struct.NoSuchTagException;
import edu.gmu.cs475.struct.TagExistsException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.StampedLock;



/*
* Julius Ahenkora G00835346
* John Hunt 01030586
 */
public class FileTagManagerServer implements IFileTagManager {

    List<Tag> tags = new CopyOnWriteArrayList<>();
    List<TaggedFile> taggedFiles = new CopyOnWriteArrayList<>();// list of tagged files
    private final StampedLock lock = new StampedLock();
    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    private final Lock readLock = readWriteLock.readLock();
    private final Lock writeLock = readWriteLock.writeLock();
    private ConcurrentHashMap<Long, TagFile> readLocked = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Long, TagFile> writeLocked = new ConcurrentHashMap<>();
    public ScheduledFuture<?> timer = null;
    
    
    private final ScheduledThreadPoolExecutor timerExecutorService = new ScheduledThreadPoolExecutor(2);

    
    private class TagFile{ //Class for file and time values
        private int Time = 0;
        private String fileName = "";
        
        public void setTime(int time){
            synchronized (this){
            this.Time = time;
            }
        }
        
        public void setFileName(String fileName){
            synchronized (this){
            this.fileName = fileName;
            }
        }
        
        public int getTime(){
            synchronized (this){
            return this.Time;
            }
        }
        
        public String getFileName(){
            synchronized (this){
            return this.fileName;
            }
        }
        
        public void incrementTime(){
            synchronized (this){
            this.Time++;}
        }
        
        
    }
	@Override
	public String readFile(String file) throws RemoteException, IOException {
		return new String(Files.readAllBytes(Paths.get(file)));
	}
        
          


	//TODO - implement all of the following methods:

	/**
	 * Initialize your FileTagManagerServer with files
	 * Each file should start off with the special "untagged" tag
	 * @param files
	 */
	public void init(List<Path> files) {
            
            
            timerExecutorService.setRemoveOnCancelPolicy(true);
            this.timer = timerExecutorService.scheduleAtFixedRate( new HeartbeatTask(), 0, 1, TimeUnit.SECONDS); //Start timer
            TaggedFile currentFile = null;
        Tag unTagged = null;//create untagged Tag

        unTagged = new Tag("untagged");//call add tag the Tag object is returned as iTag and cast to Tag
        tags.add(unTagged);

        for (Path file : files) {//iterate thru files
            currentFile = new TaggedFile(file, unTagged);//each files is a new object with untagged as its first tag
            taggedFiles.add(currentFile);//add to our files list
            unTagged.files.add(currentFile);//add file to untagged file list

        }
        
       
            
	}

	/**
	 * List all currently known tags.
	 *
	 * @return List of tags (in any order)
	 */
	@Override
	public Iterable<String> listTags() throws RemoteException {
		long stamp = lock.tryOptimisticRead();
        List<String> tagList = tagsToStringList(tags);
        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try {
                tagList = tagsToStringList(tags);
            } finally {
                lock.unlockRead(stamp);
            }
        }
        return tagList;
	}

	/**
	 * Add a new tag to the list of known tags
	 *
	 * @param name Name of tag
	 * @return The newly created Tag name
	 * @throws TagExistsException If a tag already exists with this name
	 */
	@Override
	public String addTag(String name) throws RemoteException, TagExistsException {
		Tag temp = new Tag(name);
        long stamp = lock.readLock();
        try {
            if (tagExists(name)) {
                throw new TagExistsException();
            }
            long ws = lock.tryConvertToWriteLock(stamp);
            if (ws != 0L) {
                stamp = ws;
                tags.add(temp);
            } else {
                lock.unlockRead(stamp);
                stamp = lock.writeLock();
                tags.add(temp);
            }
        } finally {
            lock.unlock(stamp);
        }

        return temp.getName();
	}

	/**
	 * Update the name of a tag, also updating any references to that tag to
	 * point to the new one
	 *
	 * @param oldTagName Old name of tag
	 * @param newTagName New name of tag
	 * @return The newly updated Tag name
	 * @throws TagExistsException If a tag already exists with the newly requested name
	 * @throws NoSuchTagException If no tag exists with the old name
	 */
	@Override
	public String editTag(String oldTagName, String newTagName) throws RemoteException, TagExistsException, NoSuchTagException {
		Tag oldTag = null;
        long stamp = lock.readLock();
        try {
            if (tagExists(newTagName)) {
                throw new TagExistsException();
            }

            oldTag = findTag(oldTagName);

            if (oldTag == null) {
                throw new NoSuchTagException();
            }
            long ws = lock.tryConvertToWriteLock(stamp);
            if (ws != 0L) {
                stamp = ws;
                oldTag.setName(newTagName);
            } else {
                lock.unlockRead(stamp);
                stamp = lock.writeLock();
                oldTag.setName(newTagName);
            }
        } finally {
            lock.unlock(stamp);
        }
        return oldTag.getName();
	}

	/**
	 * Delete a tag by name
	 *
	 * @param tagName Name of tag to delete
	 * @return The tag name that was deleted
	 * @throws NoSuchTagException         If no tag exists with that name
	 * @throws DirectoryNotEmptyException If tag currently has files still associated with it
	 */
	@Override
	public String deleteTag(String tagName) throws RemoteException, NoSuchTagException, DirectoryNotEmptyException {
		 long stamp = lock.readLock();
        try {
            for (Tag x : tags) {//search for tag match param
                if (x.getName().equals(tagName)) {
                    if (x.hasFiles()) {//if the tag has files attached to it throw erro
                        throw new DirectoryNotEmptyException(x.getName());
                    } else {//otherwise complete the deletion
                        long ws = lock.tryConvertToWriteLock(stamp);
                        if (ws != 0L) {
                            stamp = ws;
                            tags.remove(x);
                            return x.getName();
                        } else {
                            lock.unlockRead(stamp);
                            stamp = lock.writeLock();
                            tags.remove(x);
                            return x.getName();
                        }
                    }
                }
            }
        } finally {
            lock.unlock(stamp);
        }

        throw new NoSuchTagException();
	}

	/**
	 * List all files, regardless of their tag
	 *
	 * @return A list of all files. Each file must appear exactly once in this
	 * list.
	 */
	@Override
	public Iterable<String> listAllFiles() throws RemoteException {
		long stamp = lock.tryOptimisticRead();
        List<String> fileList = taggedFilesToStringList(taggedFiles);
        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try {
                fileList = taggedFilesToStringList(taggedFiles);
            } finally {
                lock.unlockRead(stamp);
            }
        }
        return fileList;
	}

	/**
	 * List all files that have a given tag
	 *
	 * @param tag Tag to look for
	 * @return A list of all files that have been labeled with the specified tag
	 * @throws NoSuchTagException If no tag exists with that name
	 */
	@Override
	public Iterable<String> listFilesByTag(String tag) throws RemoteException, NoSuchTagException {
	long stamp = lock.tryOptimisticRead();
        for (Tag x : tags) {//search for tag match param
            if (x.getName().equals(tag)) {
                return taggedFilesToStringList(x.files);//return list of files attached to tage
            }
        }
        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try {
                for (Tag x : tags) {//search for tag match param
                    if (x.getName().equals(tag)) {
                        return taggedFilesToStringList(x.files);//return list of files attached to tage
                    }
                }
            } finally {
                lock.unlockRead(stamp);
            }
        }
        throw new NoSuchTagException();//tag param wasnt found
	}

	/**
	 * Label a file with a tag
	 * <p>
	 * Files can have any number of tags - this tag will simply be appended to
	 * the collection of tags that the file has. However, files can be tagged
	 * with a given tag exactly once: repeatedly tagging a file with the same
	 * tag should return "false" on subsequent calls.
	 * <p>
	 * If the file currently has the special tag "untagged" then that tag should
	 * be removed - otherwise, this tag is appended to the collection of tags on
	 * this file.
	 *
	 * @param file Path to file to tag
	 * @param tag  The desired tag
	 * @throws NoSuchFileException If no file exists with the given name/path
	 * @throws NoSuchTagException  If no tag exists with the given name
	 * @returns true if succeeding tagging the file, false if the file already
	 * has that tag
	 */
	@Override
	public boolean tagFile(String file, String tag) throws RemoteException, NoSuchFileException, NoSuchTagException {
		 long stamp = lock.writeLock();
        try {

            Tag tagObj = null;
            TaggedFile fileObj = null;

            if (tag.equals("untagged")) {
                return false;
            } //Cannot use untagged as tag

            if (!tagExists(tag)) {
                throw new NoSuchTagException();
            } //No tag found

            tagObj = findTag(tag);

            if (!fileExists(file)) {
                throw new NoSuchFileException(file);
            } //File not found in untagged files

            fileObj = findFile(file);

            if (fileExists(file) && tagObj.files.contains(fileObj)) {
                return false;
            } //File already has tag

            fileObj.tags.remove(findTag("untagged")); //Remove untagged from file tag
            findTag("untagged").files.remove(fileObj);
            taggedFiles.get(taggedFiles.indexOf(fileObj)).tags.remove(findTag("untagged"));

            fileObj.tags.add(tagObj);

            tagObj.files.add(fileObj);
            return true;
        } finally {
            lock.unlockWrite(stamp);
        }
	}

	/**
	 * Remove a tag from a file
	 * <p>
	 * If removing this tag causes the file to no longer have any tags, then the
	 * special "untagged" tag should be added.
	 * <p>
	 * The "untagged" tag can not be removed (return should be false)
	 *
	 * @param file Path to file to untag
	 * @param tag  The desired tag to remove from that file
	 * @throws NoSuchFileException If no file exists with the given name/path
	 * @throws NoSuchTagException  If no tag exists with the given name
	 * @returns True if the tag was successfully removed, false if there was no
	 * tag by that name on the specified file
	 */
	@Override
	public boolean removeTag(String file, String tag) throws RemoteException, NoSuchFileException, NoSuchTagException {
		long stamp = lock.writeLock();
        try {
            if (tag.equals("untagged")) {
                return false;
            }

            Tag tagObj = null;
            TaggedFile fileObj = null;

            tagObj = findTag(tag);

            if (tagObj == null) {
                throw new NoSuchTagException();
            } //No tag found

            fileObj = findFile(file);

            if (fileObj == null) {
                throw new NoSuchFileException(file);
            } // No file found

            tagObj.files.remove(fileObj); //Remove file from tag list
            fileObj.tags.remove(tagObj); //Remove tag from file tag list

            if (fileObj.tags.isEmpty()) {
                fileObj.tags.add(findTag("untagged"));
            } //Add untagged to file if tag lists empty
            return true;
        } finally {
            lock.unlockWrite(stamp);
        }
	}

	/**
	 * List all of the tags that are applied to a file
	 *
	 * @param file The file to inspect
	 * @return A list of all tags that have been applied to that file in any
	 * order
	 * @throws NoSuchFileException If the file specified does not exist
	 */
	@Override
	public Iterable<String> getTags(String file) throws RemoteException, NoSuchFileException {

        long stamp = lock.tryOptimisticRead();

        TaggedFile fileObj = findFile(file);
        List<String> fileObjList = tagsToStringList(fileObj.tags);

        if (fileObj == null) {
            throw new NoSuchFileException(file);
        }

        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try {
                fileObj = findFile(file);
                fileObjList = tagsToStringList(fileObj.tags);
            } finally {
                lock.unlockRead(stamp);
            }
        }

        return fileObjList;
	}

	/**
	 * Acquires a read or write lock for a given file.
	 *
	 * @param name     File to lock
	 * @param forWrite True if a write lock is requested, else false
	 * @return A stamp representing the lock owner (e.g. from a StampedLock)
	 * @throws NoSuchFileException If the file doesn't exist
	 */
	@Override
	public long lockFile(String name, boolean forWrite) throws RemoteException, NoSuchFileException {
            
            if(!fileExists(name)){
                throw new NoSuchFileException(name);
            }
            
            
            //File with timer
            TagFile taggedF = new TagFile();
            taggedF.setTime(0);
            taggedF.setFileName(name);
            
	long stamp = 0;
        if (forWrite) {
            stamp = lock.writeLock();  
            writeLocked.put(stamp, taggedF);
            
        } else {
            stamp = lock.readLock();
            readLocked.put(stamp, taggedF);
               
            
        }
        return stamp;
	}

	/**
	 * Releases a read or write lock for a given file.
	 *
	 * @param name     File to lock
	 * @param stamp    the Stamp representing the lock owner (returned from lockFile)
	 * @param forWrite True if a write lock is requested, else false
	 * @throws NoSuchFileException          If the file doesn't exist
	 * @throws IllegalMonitorStateException if the stamp specified is not (or is no longer) valid
	 */
	@Override
	public void unLockFile(String name, long stamp, boolean forWrite) throws RemoteException, NoSuchFileException, IllegalMonitorStateException {

            
            if(!fileExists(name)){
                throw new NoSuchFileException(name);
            }
            
           
         
            
             if (forWrite) {
                 
                 
                 if(!writeLocked.containsKey(stamp)){ //Timeout or stamp not in mapping
                  throw new IllegalMonitorStateException();
              }
                 
                 if((writeLocked.get(stamp).getTime()) > 3){ //Heartbeat timeout
                     lock.unlockWrite(stamp);
                     writeLocked.remove(stamp);
                 throw new IllegalMonitorStateException();
             }
              
                 System.out.println(writeLocked.get(stamp).getTime() + " time of stamp Name: " + name + "\n");
               
                 writeLocked.remove(stamp, writeLocked.get(stamp));
            lock.unlockWrite(stamp);
        } else {
                 
                 
                  if(!readLocked.containsKey(stamp)){ //Timeout or stamp not in mapping
                  throw new IllegalMonitorStateException();
              }
                  
                  if(readLocked.get(stamp).getTime() > 3){
                   lock.unlockRead(stamp);
                     readLocked.remove(stamp);
                 throw new IllegalMonitorStateException();
             }
                  
                  System.out.println(readLocked.get(stamp).getTime() + " time of stamp Name: " + name + "\n");
                 readLocked.remove(stamp, readLocked.get(stamp));
            lock.unlockRead(stamp);
        }
             
	}

	/**
	 * Notifies the server that the client is still alive and well, still using
	 * the lock specified by the stamp provided
	 *
	 * @param file    The filename (same exact name passed to lockFile) that we are
	 *                reporting in on
	 * @param stampId Stamp returned from lockFile that we are reporting in on
	 * @param isWrite if the heartbeat is for a write lock
	 * @throws IllegalMonitorStateException if the stamp specified is not (or is no longer) valid, or if
	 *                                      the stamp is not valid for the given read/write state
	 * @throws NoSuchFileException          if the file specified doesn't exist
	 */
	@Override
	public void heartbeat(String file, long stampId, boolean isWrite) throws RemoteException, IllegalMonitorStateException, NoSuchFileException {
            
            if(!fileExists(file)){
                throw new NoSuchFileException(file);
            }
            
          
             
          
              
              
              if(isWrite){
                   if(!writeLocked.containsKey(stampId)){ //Timeout or stamp not in mapping
                       
                  throw new IllegalMonitorStateException();
              }
               
                   if((writeLocked.get(stampId).getTime()) > 3){
                       lock.unlockWrite(stampId);
                       writeLocked.remove(stampId);
                       throw new IllegalMonitorStateException();
                   }
                   writeLocked.get(stampId).setTime(0);
              }
              
              else{
                  if(!readLocked.containsKey(stampId)){ //Timeout or stamp not in mapping
                  throw new IllegalMonitorStateException();
              }
                  
                 if(readLocked.get(stampId).getTime() > 3){
                     readLocked.remove(stampId);
                     lock.unlockRead(stampId);
                     throw new IllegalMonitorStateException();
                 }
                  readLocked.get(stampId).setTime(0);
              }
             
            //  timerExecutorService.scheduleAtFixedRate( new HeartbeatTask(false,isWrite,stampId), 0, 1, TimeUnit.SECONDS);
            
          
            
	}
        
        private final class HeartbeatTask implements Runnable { //Increment time for all stamps
	         
            @Override
		public void run() {
                    for (Map.Entry<Long, TagFile> st : writeLocked.entrySet()) {//iterate over map
                       st.getValue().incrementTime();
                    }
                    for (Map.Entry<Long, TagFile> st : readLocked.entrySet()) {//iterate over map
                       st.getValue().incrementTime();
                    }
                    }
                    
		
	}

	/**
	 * Get a list of all of the files that are currently write locked
	 */
	@Override
	public List<String> getWriteLockedFiles() throws RemoteException {
		 Collection<TagFile> writeLockedFiles = writeLocked.values();
                 List<String> writeLockedList = new ArrayList<>();
                 
                 for(TagFile x : writeLockedFiles){
                     writeLockedList.add(x.fileName);
                 }
                 
                 return writeLockedList;
	}

	/**
	 * Get a list of all of the files that are currently write locked
	 */
	@Override
	public List<String> getReadLockedFiles() throws RemoteException {
		Collection<TagFile> readLockedFiles = readLocked.values();
                 List<String> readLockedList = new ArrayList<>();
                 
                 for(TagFile x : readLockedFiles){
                     readLockedList.add(x.fileName);
                 }
                 
                 return readLockedList;
	}

	@Override
	public void writeFile(String file, String content) throws RemoteException, IOException {
		Path path = Paths.get(file);
		if (!path.startsWith(ServerMain.BASEDIR))
			throw new IOException("Can only write to files in " + ServerMain.BASEDIR);
		Files.write(path, content.getBytes());

	}
        
        public List<String> tagsToStringList(List<? extends ITag> x){
            
            ArrayList<String> tagStringList = new ArrayList<>();
            for(ITag tag : x){
                tagStringList.add(tag.getName());
            }
            
            return tagStringList;
        }
        
        public List<String> taggedFilesToStringList(List<? extends ITaggedFile> x){
            
            ArrayList<String> tagStringList = new ArrayList<>();
            for(ITaggedFile tagFile : x){
                tagStringList.add(tagFile.getName());
            }
            
            return tagStringList;
        }
        
           /**
     *
     * @param tag
     * @return Tag found in list of tags
     */
    public Tag findTag(String tag) {

        for (Tag tagObj : tags) { //Find tag
            if (tagObj.getName().equals(tag)) {
                return tagObj;
            }
        }

        return null;
    }

    public boolean tagExists(String tag) {

        for (Tag tagObj : tags) { //Find tag
            if (tagObj.getName().equals(tag)) {
                return true;
            }
        }

        return false;
    }

    /**
     *
     * @param file
     * @return File found in list of taggedfiles
     */
    public TaggedFile findFile(String file) {

        for (TaggedFile fileObj : taggedFiles) { //Find file from list of tagged files
            if (fileObj.getName().equals(file)) {
                return fileObj;
            }
        }

        return null;

    }

    public boolean fileExists(String file) {

        for (TaggedFile fileObj : taggedFiles) { //Find file from list of tagged files
            if (fileObj.getName().equals(file)) {
                return true;
            }
        }

        return false;

    }
}
