package com.brooke.zipalign;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Arrays;

public class EndOfCentralDir {
	
	short mDiskNumber;
	short mDiskWithCentralDir;
	short mNumEntries;
	short mTotalNumEntries;
	private int mCentralDirSize;
	int mCentralDirOffset;
	private int mCommentLen;
	private String mComment = null;
	
	static final int kSignature = 0x06054b50;
	static final int kEOCDLen = 22;
	static final int kMaxCommentLen = 65535; // longest possible in ushort
	static final int kMaxEOCDSearch = kMaxCommentLen + kEOCDLen;
    
	/*
	 * Read the end-of-central-dir fields.
	 *
	 * "buf" should be positioned at the EOCD signature, and should contain
	 * the entire EOCD area including the comment.
	 */
	public boolean readBuf(byte[] buf, int len) {
	    /* don't allow re-use */
	    assert(mComment == null);

	    if (len < kEOCDLen) {
	        /* looks like ZIP file got truncated */
	        System.err.println(" Zip EOCD: expected >= " + kEOCDLen + " bytes, found " + len);
	        return false;
	    }

	    byte[] sig = Arrays.copyOfRange(buf, 0, 4);
	    // TODO is this right?
	    if (Integer.decode(new String(sig)) != kSignature) {
	    	System.err.println("Whoops: didn't find expected signature");
	        return false;
	    }

	    byte[] diskNum = Arrays.copyOfRange(buf, 4, 6);
	    mDiskNumber = Short.decode(new String(diskNum));
	    
	    byte[] diskWithCentralDir = Arrays.copyOfRange(buf, 6, 8);
	    mDiskWithCentralDir = Short.decode(new String(diskWithCentralDir));
	    
	    byte[] numEntries = Arrays.copyOfRange(buf, 8, 10);
	    mNumEntries = Short.decode(new String(numEntries));
	    
	    byte[] totalNumEntries = Arrays.copyOfRange(buf, 10, 12);
	    mTotalNumEntries = Short.decode(new String(totalNumEntries));
	    
	    byte[] centralDirSize = Arrays.copyOfRange(buf, 12, 16);
	    mCentralDirSize = Integer.decode(new String(centralDirSize));
	    
	    byte[] centralDirOffset = Arrays.copyOfRange(buf, 16, 20);
	    mCentralDirOffset = Integer.decode(new String(centralDirOffset));
	    
	    byte[] commentLen = Arrays.copyOfRange(buf, 20, 22);
	    mCommentLen = Short.decode(new String(commentLen));

	    // TODO: validate mCentralDirOffset

	    if (mCommentLen > 0) {
	        if (kEOCDLen + mCommentLen > len) {
	            System.err.println("EOCD(" + kEOCDLen + ") + comment(" + mCommentLen + ") exceeds len (" + len);
	            return false;
	        }
	        
	        byte[] comment = Arrays.copyOfRange(buf, kEOCDLen, (int) (kEOCDLen + mCommentLen));
	        mComment = new String(comment);
	    }

	    return true;
	}

	/*
	 * Write an end-of-central-directory section.
	 */
	public boolean write(File fp) throws IOException {
	    ByteArrayOutputStream baos = new ByteArrayOutputStream(22);
	    ObjectOutputStream oos = new ObjectOutputStream(baos);
	    FileOutputStream fos = new FileOutputStream(fp);
	    
	    try {
		    oos.writeInt(kSignature);
		    oos.writeShort(mDiskNumber);
		    oos.writeShort(mDiskWithCentralDir);
		    oos.writeShort(mNumEntries);
		    oos.writeShort(mTotalNumEntries);
		    oos.writeInt(mCentralDirSize);
		    oos.writeInt(mCentralDirOffset);
		    oos.writeShort(mCommentLen);
		    oos.flush();
		    
		    fos.write(baos.toByteArray());
		    fos.flush();
		    
		    if (mCommentLen > 0) {
		        assert(mComment != null);
		        fos.write(mComment.getBytes());
		    }
		    
		    return true;
	    } finally {
	    	fos.close();
	    	oos.close();
	    	baos.close();
	    }
	}

	/*
	 * Dump the contents of an EndOfCentralDir object.
	 */
	void dump() {
	    System.out.println(" EndOfCentralDir contents:");
	    System.out.println("  diskNum=" + mDiskNumber + " diskWCD=" + mDiskWithCentralDir + " numEnt=" + mNumEntries + " totalNumEnt=" + mTotalNumEntries);
	    System.out.println("  centDirSize=" + mCentralDirSize + " centDirOff=" + mCentralDirOffset + " commentLen=" + mCommentLen);
	}
}
