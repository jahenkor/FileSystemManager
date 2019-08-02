package edu.gmu.cs475;

import edu.gmu.cs475.struct.NoSuchTagException;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.rmi.RemoteException;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.*;

import static org.hibernate.validator.internal.util.CollectionHelper.newArrayList;

/*
* Julius Ahenkora G00835346
* John Hunt 01030586
 */
public class FileTagManagerClient extends AbstractFileTagManagerClient {

	ConcurrentHashMap<Long, String> lockMap = new ConcurrentHashMap<>();//holds file and lock values
	ConcurrentHashMap<Long, Boolean> forWriteMap = new ConcurrentHashMap<>();//holds file and forWrite values
        ConcurrentHashMap<Long, Boolean> lockSuccess = new ConcurrentHashMap<>(); //holds file and whether locks succeeded

	public FileTagManagerClient(String host, int port) {
            
		super(host, port);
                                timerExecutorService.scheduleAtFixedRate( new HeartbeatTask(), 2, 2, TimeUnit.SECONDS);

                
                
              
	}

	/**
	 * Used for tests without a real server
	 * 
	 * @param server
	 */
	public FileTagManagerClient(IFileTagManager server) {
            
		super(server);
                timerExecutorService.scheduleAtFixedRate( new HeartbeatTask(), 2, 2, TimeUnit.SECONDS);
	}

	/* It is strongly suggested that you use the timerExecutorService to manage your timers, but not required*/
	private final ScheduledThreadPoolExecutor timerExecutorService = new ScheduledThreadPoolExecutor(2);

	//TODO - implement the following methods; the rest are automatically stubbed out to the server

	/**
	 * Prints out all files that have a given tqg. Must internally synchronize
	 * to guarantee that the list of files with the given tag does not change
	 * during its call, and that each file printed does not change during its
	 * execution (using a read/write lock). You should acquire all of the locks,
	 * then read all of the files and release the locks. Your could should not
	 * deadlock while waiting to acquire locks.
	 *
	 * @param tag Tag to query for
	 * @return The concatenation of all of the files
	 * @throws NoSuchTagException If no tag exists with the given name
	 * @throws IOException        if any IOException occurs in the underlying read, or if the
	 *                            read was unsuccessful (e.g. if it times out, or gets
	 *                            otherwise disconnected during the execution
	 */
	@Override
	public String catAllFiles(String tag) throws NoSuchTagException, IOException {
		 String catFiles = "";
        List<String> fileList = (List<String>) FileTagManagerClient.super.listFilesByTag(tag);//get list of files by tag
		for (String name : fileList) {//lock all files to be used
			lockFile(name, false);
		}
        try {
           
           if(!fileList.iterator().hasNext()){
                throw new NoSuchTagException();
            }

            //Find file content according to tag, and store in string
			System.out.println(fileList);
            for (String tagFile : fileList) {
                catFiles += FileTagManagerClient.super.readFile(tagFile);
            }
        } finally {
			unlockAll(fileList, false);//unlock all files
        }

        return catFiles;
	}

	/**
	 * Echos some content into all files that have a given tag. Must internally
	 * synchronize to guarantee that the list of files with the given tag does
	 * not change during its call, and that each file being printed to does not
	 * change during its execution (using a read/write lock)
	 * <p>
	 * Given two concurrent calls to echoToAllFiles, it will be indeterminate
	 * which call happens first and which happens last. But what you can (and
	 * must) guarantee is that all files will have the *same* value (and not
	 * some the result of the first, qnd some the result of the second). Your
	 * could should not deadlock while waiting to acquire locks.
	 *
	 * @param tag     Tag to query for
	 * @param content The content to write out to each file
	 * @throws NoSuchTagException If no tag exists with the given name
	 * @throws IOException        if any IOException occurs in the underlying write, or if the
	 *                            write was unsuccessful (e.g. if it times out, or gets
	 *                            otherwise disconnected during the execution)
	 */
	@Override
	public void echoToAllFiles(String tag, String content) throws NoSuchTagException, RemoteException, NoSuchFileException, IOException {

		int i = 0;
		List<String> fileList = (List<String>) listFilesByTag(tag);//get list of all files by tag
		for (String name : fileList) {//lock all files to be used
			lockFile(name, true);
		}

		try {
			if (!fileList.iterator().hasNext()) {
				throw new NoSuchTagException();
			}

			for (String tagFile : fileList) {//write to all files in list
				FileTagManagerClient.super.writeFile(tagFile, content);
			}
		} finally {//unlock files now that we are done
			unlockAll(fileList, true);
		}
	}

	/**
	 * A callback for you to implement to let you know that a lock was
	 * successfully acquired, and that you should start sending heartbeats for
	 * it
	 *
	 * @param name
	 * @param forWrite
	 * @param stamp
	 */
	@Override
	public void lockFileSuccess(String name, boolean forWrite, long stamp) {

		//store latest file into map
		lockMap.put(stamp, name);
		forWriteMap.put(stamp, forWrite);
                lockSuccess.put(stamp, true);
		//timerExecutorService.scheduleAtFixedRate( new HeartbeatTask(), 2, 2, TimeUnit.SECONDS);

	}

	/**
	 * A callback for you to implement to let you know a lock was relinquished,
	 * and that you should stop sending heartbeats for it.
	 *
	 * @param name
	 * @param forWrite
	 * @param stamp
	 */
	@Override
	public void unLockFileCalled(String name, boolean forWrite, long stamp) {

            lockSuccess.replace(stamp, false);
		lockMap.remove(stamp);//remove key on exception so no further heartbeats
		forWriteMap.remove(stamp);
                lockSuccess.remove(stamp);
	}
	private final class HeartbeatTask implements Runnable {

		HeartbeatTask() {
			
		}

		@Override
		public void run() {
			System.out.println(lockMap);
			for (Entry<Long, String> st : lockMap.entrySet()) {//iterate over map
				if ( st != null && lockSuccess.get(st.getKey()) == true) {
					Boolean write = forWriteMap.get(st.getKey());//get write value for file
					try {
						heartbeat(st.getValue(), st.getKey(), write);//send heartbeat
					} catch (RemoteException | IllegalMonitorStateException | NoSuchFileException e) {
					lockSuccess.remove(st.getKey());	
                                            lockMap.remove(st.getKey());//remove key on exception so no further heartbeats
						forWriteMap.remove(st.getKey());
                                                
					}
				}
			}
		}
	}
	private void unlockAll(List<String> fileList, boolean forWrite) throws NoSuchFileException, RemoteException {
		int i = 0;
		List<Long> toRemove = newArrayList();
		while( i < fileList.size()) {
			for (Entry<Long, String> st : lockMap.entrySet()) {//iterate over map
				if (fileList.get(i).equals( st.getValue())) {
					toRemove.add(st.getKey());//add files to remove to list
				}
			}
			i++;
		}
		for(Long stamp : toRemove){//remove them all now be we cant remove from a list while iterating it
			unLockFile(lockMap.get(stamp), stamp, forWrite);
		}
	}

}
