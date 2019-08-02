package edu.gmu.cs475;

import java.util.HashSet;
import java.util.List;
import java.util.concurrent.locks.StampedLock;

import edu.gmu.cs475.struct.ITag;
import java.util.ArrayList;

public class Tag implements ITag {
	public List<TaggedFile> files = new ArrayList<>();

	private StampedLock lock = new StampedLock();

	private String name;

	public Tag(String name) {
		this.name = name;

	}

	

	@Override
	public String getName() {
		
		try{
			return name;
		}finally {
			
		}

	}

	public void setName(String newTagName) {
		
		
			this.name = newTagName;
		
		
		

	}

	
        
        

	public Boolean hasFiles() {
		
			if(this.files.isEmpty()){
				return false;}
                        else{
				return true;
                        }
		
	}
}
