package com.brooke.zipalign;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class ZipEntry {
	
	static final int kCompressStored = 0; // no compression
	static final int kCompressDeflated = 8; // standard deflate

	CentralDirEntry mCDE = new CentralDirEntry();
	LocalFileHeader mLFH = new LocalFileHeader();
	
	/*
	 * Initialize a new ZipEntry structure from a FILE* positioned at a
	 * CentralDirectoryEntry.
	 *
	 * On exit, the file pointer will be at the start of the next CDE or
	 * at the EOCD.
	 */
	public boolean initFromCDE(FileInputStream fis) throws IOException {
	    long posn;
//	    boolean hasDD;

	    //ALOGV("initFromCDE ---\n");

	    /* read the CDE */
	    if (!mCDE.read(fis)) {
	        System.err.println("mCDE.read failed");
	        return false;
	    }

	    //mCDE.dump();

	    /* using the info in the CDE, go load up the LFH */
	    posn = fis.getChannel().position();
	    if (fis.skip(mCDE.mLocalHeaderRelOffset) != mCDE.mLocalHeaderRelOffset) {
	        System.err.println("local header seek failed: " + mCDE.mLocalHeaderRelOffset);
	        return false;
	    }

	    if (!mLFH.read(fis)) {
	        System.err.println("mLFH.read failed");
	        return false;
	    }

	    ZipFile.setFilePointerPosition(fis, posn);

	    //mLFH.dump();

	    /*
	     * Sanity-check the LFH.  Note that this will fail if the "kUsesDataDescr"
	     * flag is set, because the LFH is incomplete.  (Not a problem, since we
	     * prefer the CDE values.)
	     */
//	    if (!compareHeaders()) {
//	        System.err.println("WARNING: header mismatch!");
	        // keep going?
//	    }

	    /*
	     * If the mVersionToExtract is greater than 20, we may have an
	     * issue unpacking the record -- could be encrypted, compressed
	     * with something we don't support, or use Zip64 extensions.  We
	     * can defer worrying about that to when we're extracting data.
	     */
	    return true;
	}
	
	public boolean isCompressed() {
        return mCDE.mCompressionMethod != kCompressStored;
    }
	
	/*
     * Return the uncompressed length.
     */
    public int getUncompressedLen() { 
    	return mCDE.mUncompressedSize; 
    }
    
    /*
     * Return the compressed length.  For uncompressed data, this returns
     * the same thing as getUncompresesdLen().
     */
    public int getCompressedLen() { 
    	return mCDE.mCompressedSize; 
    }

	/*
     * Return the absolute file offset of the start of the compressed or
     * uncompressed data.
     */
    public int getFileOffset() {
        return mCDE.mLocalHeaderRelOffset +
                LocalFileHeader.kLFHLen +
                mLFH.mFileNameLength +
                mLFH.mExtraFieldLength;
    }
    
    /*
     * Return the archived file name.
     */
    public String getFileName() { 
    	return mCDE.mFileName; 
    }
    
    /*
     * Initialize a new entry, starting with the ZipEntry from a different
     * archive.
     *
     * Initializes the CDE and the LFH.
     */
    boolean initFromExternal(ZipFile zipFile, ZipEntry entry) {
        /*
         * Copy everything in the CDE over, then fix up the hairy bits.
         */
        mCDE = entry.mCDE;

        if (mCDE.mFileNameLength > 0) {
            mCDE.mFileName = entry.mCDE.mFileName;
        }
        
        if (mCDE.mFileCommentLength > 0) {
            mCDE.mFileComment = entry.mCDE.mFileComment;
        }
        
        if (mCDE.mExtraFieldLength > 0) {
            /* TODO <BL> what if this is not a string?? */
            mCDE.mExtraField = entry.mCDE.mExtraField;
        }

        /* construct the LFH from the CDE */
        copyCDEtoLFH();

        /*
         * The LFH "extra" field is independent of the CDE "extra", so we
         * handle it here.
         */
        assert(mLFH.mExtraField == null);
        mLFH.mExtraFieldLength = entry.mLFH.mExtraFieldLength;
        if (mLFH.mExtraFieldLength > 0) {
            mLFH.mExtraField = entry.mLFH.mExtraField;
        }

        return true;
    }
    
    /*
     * Set the fields in the LFH equal to the corresponding fields in the CDE.
     *
     * This does not touch the LFH "extra" field.
     */
    private void copyCDEtoLFH() {
        mLFH.mVersionToExtract  = mCDE.mVersionToExtract;
        mLFH.mGPBitFlag         = mCDE.mGPBitFlag;
        mLFH.mCompressionMethod = mCDE.mCompressionMethod;
        mLFH.mLastModFileTime   = mCDE.mLastModFileTime;
        mLFH.mLastModFileDate   = mCDE.mLastModFileDate;
        mLFH.mCRC32             = mCDE.mCRC32;
        mLFH.mCompressedSize    = mCDE.mCompressedSize;
        mLFH.mUncompressedSize  = mCDE.mUncompressedSize;
        mLFH.mFileNameLength    = mCDE.mFileNameLength;
        // the "extra field" is independent
        mLFH.mFileName = mCDE.mFileName;
    }
}
