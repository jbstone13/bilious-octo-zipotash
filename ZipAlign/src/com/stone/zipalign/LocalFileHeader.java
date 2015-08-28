package com.brooke.zipalign;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Arrays;

public class LocalFileHeader {
	short mVersionToExtract;
	short mGPBitFlag;
	short mCompressionMethod;
	short mLastModFileTime;
	short mLastModFileDate;
	int  mCRC32;
	int  mCompressedSize;
	int  mUncompressedSize;
	short mFileNameLength;
	short mExtraFieldLength;
	String mFileName;
	String mExtraField;
    
    private static final int kSignature = 0x04034b50;
    static final int kLFHLen = 30; // LocalFileHdr len, excl. var fields
    
    /*
     * Read a local file header.
     *
     * On entry, "fp" points to the signature at the start of the header.
     * On exit, "fp" points to the start of data.
     */
    boolean read(FileInputStream fis) {
        byte[] buf = new byte[kLFHLen];

        assert(mFileName == null);
        assert(mExtraField == null);

	//	    ByteArrayInputStream bais = new ByteArrayInputStream(buf);
	//	    ObjectInputStream ois = new ObjectInputStream(fis);
	    
        try {
	    	if (fis.read(buf, 0, kLFHLen) != kLFHLen) {
	    		System.err.println("Error reading local file header bytes!");
	    		return false;
	    	}
	    	
	    	byte[] sig = Arrays.copyOfRange(buf, 0, 4);
	        if (Integer.decode(new String(sig)) != kSignature) {
		    	System.err.println("Whoops: didn't find expected signature");
		        return false;
		    }
	
	        byte[] versionToExtract = Arrays.copyOfRange(buf, 4, 6);
	        mVersionToExtract = Short.decode(new String(versionToExtract));
	        
	        byte[] gPBitFlag = Arrays.copyOfRange(buf, 6, 8);
	        mGPBitFlag = Short.decode(new String(gPBitFlag));
	        
	        byte[] compressionMethod = Arrays.copyOfRange(buf, 8, 10);
	        mCompressionMethod = Short.decode(new String(compressionMethod));
		    
	        byte[] lastModFileTime = Arrays.copyOfRange(buf, 10, 12);
	        mLastModFileTime = Short.decode(new String(lastModFileTime));
	        
	        byte[] lastModFileDate = Arrays.copyOfRange(buf, 12, 14);
	        mLastModFileDate = Short.decode(new String(lastModFileDate));
	        
	        byte[] cRC32 = Arrays.copyOfRange(buf, 14, 18);
	        mCRC32 = Integer.decode(new String(cRC32));
	        
	        byte[] compressedSize = Arrays.copyOfRange(buf, 18, 22);
	        mCompressedSize = Integer.decode(new String(compressedSize));
	        
	        byte[] uncompressedSize = Arrays.copyOfRange(buf, 22, 26);
	        mUncompressedSize = Integer.decode(new String(uncompressedSize));
	    	
	        byte[] fileNameLength = Arrays.copyOfRange(buf, 26, 28);
	        mFileNameLength = Short.decode(new String(fileNameLength));
	        
	        byte[] extraFieldLength = Arrays.copyOfRange(buf, 28, 30);
	        mExtraFieldLength = Short.decode(new String(extraFieldLength));
	        
	        // TODO: validate sizes
	
	        /* read filename */
		    if (mFileNameLength > 0) {
		    	byte[] fileName = new byte[mFileNameLength];
		    	if (fis.read(fileName, 0, mFileNameLength) != mFileNameLength) {
		    		System.err.println("Error reading file name!");
		    		return false;
		    	}
		    	mFileName = new String(fileName);
		    }
	
		    /* read "extra field" */
		    if (mExtraFieldLength > 0) {
		    	byte[] extraField = new byte[mExtraFieldLength];
		    	if (fis.read(extraField, 0, mExtraFieldLength) != mExtraFieldLength) {
		    		System.err.println("Error reading extra field!");
		    	}
		    	mExtraField = new String(extraField);
		    }
		    
		    return true;
        } catch (IOException e) {
			System.err.println("Error reading central dir entry!");
			e.printStackTrace();
			return false;
		} 
    }

    /*
     * Write a local file header.
     */
    boolean write(FileOutputStream fos) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(kLFHLen);
		ObjectOutputStream oos = null;
		
		try {
			oos = new ObjectOutputStream(baos);
			oos.writeInt(kSignature);
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
		    
		    return true;
		} catch (IOException e) {
			System.err.println("Error writing central dir entry to file!");
			e.printStackTrace();
			return false;
		} finally {
	    	try {
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
     * Dump the contents of a LocalFileHeader object.
     */
    void dump() {
        System.out.println(" LocalFileHeader contents:");
        System.out.println("  versToExt=" + mVersionToExtract + " gpBits=" + mGPBitFlag + " compression=" + mCompressionMethod);
        System.out.println("  modTime=" + mLastModFileTime + " modDate=" + mLastModFileDate + " crc32=" + mCRC32);
        System.out.println("  compressedSize=" + mCompressedSize + " uncompressedSize=" + mUncompressedSize);
        System.out.println("  filenameLen=" + mFileNameLength + " extraLen=" + mExtraFieldLength);
        if (mFileName != null)
        	System.out.println("  filename: " + mFileName);
    }
}
