package com.brooke.zipalign;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;

public class CentralDirEntry {

	static final int kSignature = 0x02014b50;
	static final int kCDELen = 46; // CentralDirEnt len, excl. var fields
	
	short  mVersionMadeBy;
    short  mVersionToExtract;
    short  mGPBitFlag;
    short  mCompressionMethod;
    short  mLastModFileTime;
    short  mLastModFileDate;
    int   mCRC32;
    int   mCompressedSize;
    int   mUncompressedSize;
    short  mFileNameLength;
    short  mExtraFieldLength;
    short  mFileCommentLength;
    short  mDiskNumberStart;
    short  mInternalAttrs;
    int   mExternalAttrs;
    int   mLocalHeaderRelOffset;
    String mFileName = null;
    String mExtraField = null;
    String mFileComment = null;

	/*
	 * Read the central dir entry that appears next in the file.
	 *
	 * On entry, "fp" should be positioned on the signature bytes for the
	 * entry.  On exit, "fp" will point at the signature word for the next
	 * entry or for the EOCD.
	 */
	boolean read(FileInputStream fis) throws IOException {
	    byte[] buf = new byte[kCDELen];

	    /* no re-use */
	    assert(mFileName == null);
	    assert(mExtraField == null);
	    assert(mFileComment == null);

	    try {
	//	    ByteArrayInputStream bais = new ByteArrayInputStream(buf);
	//	    ObjectInputStream ois = new ObjectInputStream(fis);
	    
	    	fis.read(buf, 0, kCDELen);
	    } catch (IOException e) {
			System.err.println("Error reading central dir entry!");
			e.printStackTrace();
			return false;
		}
	    
    	byte[] sig = Arrays.copyOfRange(buf, 0, 4);
	    // TODO is this right?
	    if (Integer.decode(new String(sig)) != kSignature) {
	    	System.err.println("Whoops: didn't find expected signature");
	        return false;
	    }
	    
    	byte[] versionMadeBy = Arrays.copyOfRange(buf, 4, 6);
    	mVersionMadeBy = Short.decode(new String(versionMadeBy));
    	
    	byte[] versionToExtract = Arrays.copyOfRange(buf, 6, 8);
    	mVersionToExtract = Short.decode(new String(versionToExtract));
    	
    	byte[] gPBitFlag = Arrays.copyOfRange(buf, 8, 10);
    	mGPBitFlag = Short.decode(new String(gPBitFlag));
    	
    	byte[] compressionMethod = Arrays.copyOfRange(buf, 10, 12);
    	mCompressionMethod = Short.decode(new String(compressionMethod));
    	
    	byte[] lastModFileTime = Arrays.copyOfRange(buf, 12, 14);
    	mLastModFileTime = Short.decode(new String(lastModFileTime));
    	
    	byte[] lastModFileDate = Arrays.copyOfRange(buf, 14, 16);
    	mLastModFileDate = Short.decode(new String(lastModFileDate));
    	
    	byte[] crc32 = Arrays.copyOfRange(buf, 16, 20);
    	mCRC32 = Integer.decode(new String(crc32));
    	
    	byte[] compressedSize = Arrays.copyOfRange(buf, 20, 24);
    	mCompressedSize = Integer.decode(new String(compressedSize));
    	
    	byte[] uncompressedSize = Arrays.copyOfRange(buf, 24, 28);
    	mUncompressedSize = Integer.decode(new String(uncompressedSize));
    	
    	byte[] fileNameLength = Arrays.copyOfRange(buf, 28, 30);
    	mFileNameLength = Short.decode(new String(fileNameLength));
    	
    	byte[] extraFieldLength = Arrays.copyOfRange(buf, 30, 32);
    	mExtraFieldLength = Short.decode(new String(extraFieldLength));
    	
    	byte[] fileCommentLength = Arrays.copyOfRange(buf, 32, 34);
    	mFileCommentLength = Short.decode(new String(fileCommentLength));
    	
    	byte[] diskNumberStart = Arrays.copyOfRange(buf, 34, 36);
    	mDiskNumberStart = Short.decode(new String(diskNumberStart));
    	
    	byte[] internalAttrs = Arrays.copyOfRange(buf, 36, 38);
    	mInternalAttrs = Short.decode(new String(internalAttrs));
    	
    	byte[] externalAttrs = Arrays.copyOfRange(buf, 38, 42);
    	mExternalAttrs = Integer.decode(new String(externalAttrs));
    	
    	byte[] localHeaderRelOffset = Arrays.copyOfRange(buf, 42, 46);
    	mLocalHeaderRelOffset = Integer.decode(new String(localHeaderRelOffset));
		    
	    // TODO: validate sizes and offsets

	    /* read filename */
	    if (mFileNameLength > 0) {
	    	byte[] fileName = new byte[mFileNameLength];
	    	fis.read(fileName, 0, mFileNameLength);
	    	mFileName = new String(fileName);
	    }

	    /* read "extra field" */
	    if (mExtraFieldLength > 0) {
	    	byte[] extraField = new byte[mExtraFieldLength];
	    	fis.read(extraField, 0, mExtraFieldLength);
	    	mExtraField = new String(extraField);
	    }

	    /* read comment, if any */
	    if (mFileCommentLength > 0) {
	    	byte[] comment = new byte[mFileCommentLength];
	    	fis.read(comment, 0, mFileCommentLength);
	    	mFileComment = new String(comment);
	    }
	    
	    return true;
	}

	/*
	 * Write a central dir entry.
	 */
	boolean write(File fp) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream(kCDELen);
		ObjectOutputStream oos = null;
		FileOutputStream fos = null;
		
		try {
			oos = new ObjectOutputStream(baos);
			fos = new FileOutputStream(fp);
	    
		    oos.writeInt(kSignature);
		    oos.writeShort(mVersionMadeBy);
		    oos.writeShort(mVersionToExtract);
		    oos.writeShort(mGPBitFlag);
		    oos.writeShort(mCompressionMethod);
		    oos.writeShort(mLastModFileTime);
		    oos.writeShort(mLastModFileDate);
		    oos.writeInt(mCRC32);
		    oos.writeInt(mCompressedSize);
		    oos.writeInt(mUncompressedSize);
		    oos.writeShort(mFileNameLength);
		    oos.writeShort(mExtraFieldLength);
		    oos.writeShort(mFileCommentLength);
		    oos.writeShort(mDiskNumberStart);
		    oos.writeShort(mInternalAttrs);
		    oos.writeInt(mExternalAttrs);
		    oos.writeInt(mLocalHeaderRelOffset);
		    oos.flush();
		    
		    fos.write(baos.toByteArray());
		    fos.flush();
		    
		    /* write filename */
		    if (mFileNameLength > 0) {
		    	fos.write(mFileName.getBytes());
		    }
	
		    /* write "extra field" */
		    if (mExtraFieldLength != 0) {
		    	fos.write(mExtraField.getBytes());
		    }
	
		    /* write comment */
		    if (mFileCommentLength != 0) {
		    	fos.write(mFileComment.getBytes());
		    }
		    
		    return true;
	    } catch (IOException e) {
			System.err.println("Error writing central dir entry to file!");
			e.printStackTrace();
			return false;
		} finally {
	    	try {
	    		if (fos != null) {
	    			fos.close(); 
	    		}
	    		if (oos != null) {
	    			oos.close();
	    		}
		    	baos.close();
			} catch (IOException e) {
				System.err.println("Error closing output stream(s); continuing.");
			}
	    }
	}

	/*
	 * Dump the contents of a CentralDirEntry object.
	 */
	public void dump() {
	    System.out.println(" CentralDirEntry contents:");
	    System.out.println("  versMadeBy=" + mVersionMadeBy + " versToExt=" + mVersionToExtract + " gpBits=" + mGPBitFlag + " compression=" + mCompressionMethod);
	    System.out.println("  modTime=" + mLastModFileTime + " modDate=" + mLastModFileDate + " crc32=" + mCRC32);
	    System.out.println("  compressedSize=" + mCompressedSize + " uncompressedSize=" + mUncompressedSize);
	    System.out.println("  filenameLen=" + mFileNameLength + " extraLen=" + mExtraFieldLength + " commentLen=" + mFileCommentLength);

	    if (mFileName != null)
	    	System.out.println("  filename: '" + mFileName + "'");
	    if (mFileComment != null)
	    	System.out.println("  comment: '" + mFileComment + "'");
	}
}
