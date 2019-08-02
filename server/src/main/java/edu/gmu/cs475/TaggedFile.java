package edu.gmu.cs475;

import edu.gmu.cs475.struct.ITag;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.concurrent.locks.StampedLock;

import edu.gmu.cs475.struct.ITaggedFile;
import java.util.ArrayList;
import java.util.List;

public class TaggedFile implements ITaggedFile {

	public List<ITag> tags = new ArrayList<>();
	public StampedLock lock = new StampedLock();
	
	public StampedLock getLock() {
		return lock;
	}
	private Path path;
	public TaggedFile(Path path, Tag tag)
	{
		this.path = path;
		this.tags.add(tag);
	}
	@Override
	public String getName() {
		return path.toString();
	}
	@Override
	public String toString() {
		return getName();
	}
}
