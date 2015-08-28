package com.brooke.zipalign;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Vector;
import java.util.zip.CRC32;

public class ZipFile {

	public static final int kOpenReadOnly = 0x01;
	public static final int kOpenReadWrite = 0x02;
	public static final int kOpenCreate = 0x04; // create if it doesn't exist
	public static final int kOpenTruncate = 0x08; // if it exists, empty it
	
	/*
	 * Some environments require the "b", some choke on it.
	 */
	public static final String FILE_OPEN_RO = "rb";
	public static final String FILE_OPEN_RW = "r+b";
	public static final String FILE_OPEN_RW_CREATE = "w+b";	
	
	private File zipFile = null;
	private FileInputStream fis = null;
	private boolean needCDRewrite = false;
	private EndOfCentralDir mEOCD = new EndOfCentralDir();
	private Vector<ZipEntry> mEntries = new Vector<ZipEntry>();
	private boolean readOnly = false;
	
	public static void setFileInputStreamPosition(FileInputStream fis, long position) {
		FileChannel fc = fis.getChannel();
		fc.position(position);
	}
	
	public static long getFileInputStreamPosition(FileInputStream fis) {
		return fis.getChannel().position();
	}
	
	public static void setFileOutputStreamPosition(FileOutputStream fos, long position) {
		FileChannel fc = fos.getChannel();
		fc.position(position);
	}
	
	public static long getFileOutputStreamPosition(FileOutputStream fos) {
		return fos.getChannel().position();
	}
	
	public int getNumEntries() { 
		return mEntries.size(); 
	}
	
	/*
	 * Return the Nth entry in the archive.
	 */
	public ZipEntry getEntryByIndex(int idx) {
	    if (idx < 0 || idx >= (int) mEntries.size())
	        return null;

	    return mEntries.elementAt(idx);
	}
	
	/*
	 * Open a file and parse its guts.
	 */
	public boolean open(String zipFileName, int... flags) {
	    boolean newArchive = false;
	    
	    boolean readWrite = false;
	    boolean create = false;
	    boolean truncate  = false;
	    
	    assert(zipFile == null);     // no reopen

	    for (int flag : flags) {
	    	switch (flag) {
	    	case kOpenReadOnly:
	    		readOnly = true;
	    		break;
	    	case kOpenReadWrite:
	    		readWrite = true;
	    		break;
	    	case kOpenCreate:
	    		create = true;
	    		break;
	    	case kOpenTruncate:
	    		truncate = true;
	    		create = true; // truncate implies create
	    		break;
	    	}
	    }
	    
	    if (readOnly && readWrite) {
	    	System.err.println("Cannot specify both readOnly and readWrite flags");
	        return false;       // not both
	    }
	    if (!readOnly && !readWrite) {
	    	System.err.println("Must specify one of readOnly and readWrite flags");
	        return false;       // not neither
	    }
	    if (create && !readWrite) {
	    	System.err.println("Cannot specify create flag without readWrite flag");
	        return false;       // create requires write
	    }
	        
	        
	    if (truncate) {
	        newArchive = true;
	    } else {
	        newArchive = !(Files.exists(FileSystems.getDefault().getPath(zipFileName)));
	        if (!create && newArchive) {
	            /* not creating, must already exist */
	            System.err.println("File " + zipFileName + " does not exist");
	            return false;
	        }
	    }

	    /* open the file */
	    String openflags;
	    if (readWrite) {
	        if (newArchive)
	            openflags = FILE_OPEN_RW_CREATE;
	        else
	            openflags = FILE_OPEN_RW;
	    } else {
	        openflags = FILE_OPEN_RO;
	    }
	    
	    zipFile = new File(zipFileName, openflags);
	    // TODO <BL> need to close this at some point?? (or ok if program just exits??)
	    fis = new FileInputStream(zipFile);

	    if (!newArchive) {
	        /*
	         * Load the central directory.  If that fails, then this probably
	         * isn't a Zip archive.
	         */
	        return readCentralDir();
	    } else {
	        /*
	         * Newly-created.  The EndOfCentralDir constructor actually
	         * sets everything to be the way we want it (all zeroes).  We
	         * set mNeedCDRewrite so that we create *something* if the
	         * caller doesn't add any files.  (We could also just unlink
	         * the file if it's brand new and nothing was added, but that's
	         * probably doing more than we really should -- the user might
	         * have a need for empty zip files.)
	         */
	    	needCDRewrite = true;
	        
	        return true;
	    }
	}
	
	/*
	 * Find the central directory and read the contents.
	 *
	 * The fun thing about ZIP archives is that they may or may not be
	 * readable from start to end.  In some cases, notably for archives
	 * that were written to stdout, the only length information is in the
	 * central directory at the end of the file.
	 *
	 * Of course, the central directory can be followed by a variable-length
	 * comment field, so we have to scan through it backwards.  The comment
	 * is at most 64K, plus we have 18 bytes for the end-of-central-dir stuff
	 * itself, plus apparently sometimes people throw random junk on the end
	 * just for the fun of it.
	 *
	 * This is all a little wobbly.  If the wrong value ends up in the EOCD
	 * area, we're hosed.  This appears to be the way that everybody handles
	 * it though, so we're in pretty good company if this fails.
	 */
	private boolean readCentralDir() {
	    byte[] buf = null;
	    long fileLength;
		long seekStart;
	    long readAmount;
	    int i;

	    fileLength = zipFile.length();

	    /* too small to be a ZIP archive? */
	    if (fileLength < EndOfCentralDir.kEOCDLen) {
	        System.err.println("Length is " + fileLength + " -- too small");
	        return false;
	    }

	    buf = new byte[EndOfCentralDir.kMaxEOCDSearch];

	    if (fileLength > EndOfCentralDir.kMaxEOCDSearch) {
	        seekStart = fileLength - EndOfCentralDir.kMaxEOCDSearch;
	        readAmount = EndOfCentralDir.kMaxEOCDSearch;
	    } else {
	        seekStart = 0;
	        readAmount = fileLength;
	    }

	    /* read the last part of the file into the buffer */
	    try {
	    	ZipFile.setFileInputStreamPosition(fis, seekStart);
	    	
		    if (fis.read(buf, 0, (int)readAmount) != readAmount) {
		        System.err.println("short file? wanted " + readAmount);
		        return false;
		    }
	
		    /* find the end-of-central-dir magic */
		    for (i = (int) (readAmount - 4); i >= 0; i--) {
		        if (buf[i] == 0x50) {
		        	byte[] sigBytes = Arrays.copyOfRange(buf, i, i+2);
		    	    int sig = Integer.decode(new String(sigBytes));
		        	if (sig == EndOfCentralDir.kSignature) {
		        		//ALOGV("+++ Found EOCD at buf+%d\n", i);
		        		break;
		        	}
		        }
		    }
		    
		    if (i < 0) {
		        System.err.println("EOCD not found, not Zip");
		        return false;
		    }
	
		    /* extract eocd values */
		    byte[] eocd = Arrays.copyOfRange(buf, i, (int) (readAmount - i)); 
	//	    result = mEOCD.readBuf(buf + i, readAmount - i);
	//	    if (result != NO_ERROR) {
		        //ALOGD("Failure reading %ld bytes of EOCD values", readAmount - i);
	//	        goto bail;
	//	    }
		    //mEOCD.dump();
	
		    if (mEOCD.mDiskNumber != 0 || mEOCD.mDiskWithCentralDir != 0 ||
		        mEOCD.mNumEntries != mEOCD.mTotalNumEntries) {
		        System.err.println("Archive spanning not supported");
		        return false;
		    }
	
		    /*
		     * So far so good.  "mCentralDirSize" is the size in bytes of the
		     * central directory, so we can just seek back that far to find it.
		     * We can also seek forward mCentralDirOffset bytes from the
		     * start of the file.
		     *
		     * We're not guaranteed to have the rest of the central dir in the
		     * buffer, nor are we guaranteed that the central dir will have any
		     * sort of convenient size.  We need to skip to the start of it and
		     * read the header, then the other goodies.
		     *
		     * The only thing we really need right now is the file comment, which
		     * we're hoping to preserve.
		     */

		    ZipFile.setFileInputStreamPosition(fis, mEOCD.mCentralDirOffset);
		    
		    /*
		     * Loop through and read the central dir entries.
		     */
		    //ALOGV("Scanning %d entries...\n", mEOCD.mTotalNumEntries);
		    int entryIndex;
		    for (entryIndex = 0; entryIndex < mEOCD.mTotalNumEntries; entryIndex++) {
		        ZipEntry entry = new ZipEntry();
	
		        if (!entry.initFromCDE(fis)) {
		            System.err.println("initFromCDE failed");
		            return false;
		        }
	
		        mEntries.add(entry);
		    }
	
		    /*
		     * If all went well, we should now be back at the EOCD.
		     */
	        byte[] checkBuf = new byte[4];
	        if (fis.read(checkBuf) != 4) {
	            System.err.println("EOCD check read failed");
	            // TODO <BL> have to close fis! (What happens if I don't? Program is exiting, so...)
	            return false;
	        }
	        
	        if (Integer.decode(new String(checkBuf)) != EndOfCentralDir.kSignature) {
	        	System.err.println("EOCD check read failed");
	            // TODO <BL> have to close fis! (What happens if I don't? Program is exiting, so...)
	            return false;
	        }
	        //ALOGV("+++ EOCD read check passed\n");
	        return true;
		} catch (FileNotFoundException e) {
	    	System.err.println("File not found: " + zipFile.getAbsolutePath());
	    	return false;
	    } catch (IOException e) {
	    	System.err.println("Error reading central dir!");
			e.printStackTrace();
			return false;
		}
	}
	
	public byte[] uncompress(ZipEntry entry) {
	    int unlen = entry.getUncompressedLen();
	    int clen = entry.getCompressedLen();

	    byte[] buf = new byte[unlen];

	    ZipFile.setFileInputStreamPosition(fis, entry.getFileOffset());

	    switch (entry.mCDE.mCompressionMethod) {
	        case ZipEntry.kCompressStored:
	        	if (fis.read(buf) != unlen) {
	                return null;
	            }
	            break;
	        case ZipEntry.kCompressDeflated:
	            if (!ZipUtils.inflateToBuffer(zipFile, buf, unlen, clen)) {
	                return null;
	            }
	            break;
	        default:
	            return null;
	    } // end switch
	    return buf;
	}
	
	/*
	 * Add an entry by copying it from another zip file, recompressing with
	 * Zopfli if already compressed.
	 */
	public ZipEntry addRecompress(ZipFile sourceZip, ZipEntry sourceEntry) {
		ZipEntry entry = new ZipEntry();
		long lfhPosn, startPosn, endPosn, uncompressedLen;
		
		if (readOnly) {
	        return null;
		}
		
		/* make sure we're in a reasonable state */
	    assert(zipFile != null);
	    assert(mEntries.size() == mEOCD.mTotalNumEntries);
	    
	    if (!entry.initFromExternal(sourceZip, sourceEntry)) {
	    	// TODO need error output??
	    	return null;
	    }
	    	
	    FileOutputStream fos = new FileOutputStream(zipFile);
	    ZipFile.setFileOutputStreamPosition(fos, mEOCD.mCentralDirOffset);
	    
	    /*
	     * From here on out, failures are more interesting.
	     */
	    needCDRewrite = true;
	    
	    /*
	     * Write the LFH, even though it's still mostly blank.  We need it
	     * as a place-holder.  In theory the LFH isn't necessary, but in
	     * practice some utilities demand it.
	     */
	    lfhPosn = fos.getChannel().position();
	    entry.mLFH.write(zipFile);
	    startPosn = fos.getChannel().position();
	    
	    /*
	     * Copy the data over.
	     *
	     * If the "has data descriptor" flag is set, we want to copy the DD
	     * fields as well.  This is a fixed-size area immediately following
	     * the data.
	     */
	    ZipFile.setFileInputStreamPosition(sourceZip.fis, sourceEntry.getFileOffset());
	    
	    uncompressedLen = sourceEntry.getUncompressedLen();
	    
	    if (sourceEntry.isCompressed()) {
	    	sourceZip.uncompress(sourceEntry);

	        byte[] buf = sourceZip.uncompress(sourceEntry);
	        if (buf == null) {
	            return null;
	        }
	        
	        long startPosn2 = fis.getChannel().position();
	        
	        long crc = compressFpToFp(fos, null, buf, uncompressedLen);
	    } // TODO <BL> remove this to continue....
	        
	        
	        if (crc != NO_ERROR) {
	            //ALOGW("recompress of '%s' failed\n", pEntry->mCDE.mFileName);
	            result = UNKNOWN_ERROR;
	            free(buf);
	            goto bail;
	        }
	        long endPosn = ftell(mZipFp);
	        pEntry->setDataInfo(uncompressedLen, endPosn - startPosn,
	            pSourceEntry->getCRC32(), ZipEntry::kCompressDeflated);
	        free(buf);
	    } else {
	        off_t copyLen;
	        copyLen = pSourceEntry->getCompressedLen();
	        if ((pSourceEntry->mLFH.mGPBitFlag & ZipEntry::kUsesDataDescr) != 0)
	            copyLen += ZipEntry::kDataDescriptorLen;

	        if (copyPartialFpToFp(mZipFp, pSourceZip->mZipFp, copyLen, NULL)
	            != NO_ERROR)
	        {
	            //ALOGW("copy of '%s' failed\n", pEntry->mCDE.mFileName);
	            result = UNKNOWN_ERROR;
	            goto bail;
	        }
	    }

	    /*
	     * Update file offsets.
	     */
	    endPosn = ftell(mZipFp);

	    /*
	     * Success!  Fill out new values.
	     */
	    pEntry->setLFHOffset(lfhPosn);
	    mEOCD.mNumEntries++;
	    mEOCD.mTotalNumEntries++;
	    mEOCD.mCentralDirSize = 0;      // mark invalid; set by flush()
	    mEOCD.mCentralDirOffset = endPosn;

	    /*
	     * Go back and write the LFH.
	     */
	    if (fseek(mZipFp, lfhPosn, SEEK_SET) != 0) {
	        result = UNKNOWN_ERROR;
	        goto bail;
	    }
	    pEntry->mLFH.write(mZipFp);

	    /*
	     * Add pEntry to the list.
	     */
	    mEntries.add(pEntry);
	    if (ppEntry != NULL)
	        *ppEntry = pEntry;
	    pEntry = NULL;

	    result = NO_ERROR;
	}

	/*
	 * Compress all of the data in "srcFp" and write it to "dstFp".
	 *
	 * On exit, "srcFp" will be seeked to the end of the file, and "dstFp"
	 * will be seeked immediately past the compressed data.
	 */
	private static long compressFpToFp(FileOutputStream dstFp, FileInputStream srcFp,
	    byte[] data, long size) {
	    final int kBufSize = 1024 * 1024;
	    
	    /*
         * Create an input buffer and an output buffer.
         */
	    byte[] inBuf = new byte[kBufSize];
	    byte[] outBuf = new byte[kBufSize]; // TODO <BL> is this correct size for outBuf? (check ZopfliDeflate)
	    long outSize = 0;
	    boolean atEof = false;     // no feof() available yet
	    long crc;
	    ZopfliOptions options;
	    unsigned char bp = 0;
	
	    ZopfliInitOptions(options);
	
	    CRC32 crc32 = new CRC32();
	
	    if (data != null) {
	    	crc32.update(data);
	    	crc = crc32.getValue();

	    	ZopfliDeflate(options, 2, true, data, size, &bp,
	            &outBuf, &outSize);
	    } else {
	        /*
	         * Loop while we have data.
	         */
	        do {
	        	int getSize = srcFp.read(inBuf);
	        	if (getSize < kBufSize) {
	                //ALOGV("+++  got %d bytes, EOF reached\n",
	                  //  (int)getSize);
	                atEof = true;
	            }
	
	        	crc32.update(inBuf);
		    	crc = crc32.getValue();
		    	
	            ZopfliDeflate(options, 2, atEof, inBuf, getSize, &bp, &outBuf, &outSize);
	        } while (!atEof);
	    }
	
	    //ALOGV("+++ writing %d bytes\n", (int)outSize);
	    dstFp.write(outBuf);
	
	    return crc;
	}
	
	/*
     * Add a file to the end of the archive.  Specify whether you want the
     * library to try to store it compressed.
     *
     * If "storageName" is specified, the archive will use that instead
     * of "fileName".
     *
     * If there is already an entry with the same name, the call fails.
     * Existing entries with the same name must be removed first.
     *
     * If "ppEntry" is non-NULL, a pointer to the new entry will be returned.
     */
    private ZipEntry add(String fileName, int compressionMethod) {
        return add(fileName, fileName, compressionMethod);
    }
    
    private ZipEntry add(String fileName, String storageName, int compressionMethod) {
        return addCommon(fileName, null, 0, storageName,
                         ZipEntry.kCompressStored,
                         compressionMethod);
    }
    
    /*
     * Add a new file to the archive.
     *
     * This requires creating and populating a ZipEntry structure, and copying
     * the data into the file at the appropriate position.  The "appropriate
     * position" is the current location of the central directory, which we
     * casually overwrite (we can put it back later).
     *
     * If we were concerned about safety, we would want to make all changes
     * in a temp file and then overwrite the original after everything was
     * safely written.  Not really a concern for us.
     */
    private ZipEntry addCommon(String fileName, byte[] data, int size,
        String storageName, int sourceType, int compressionMethod) {
        ZipEntry pEntry = new ZipEntry;
        long lfhPosn, startPosn, endPosn, uncompressedLen;
        FILE* inputFp = NULL;
        long crc;
        time_t modWhen;

        if (readOnly)
            return null;

        assert(compressionMethod == ZipEntry.kCompressDeflated ||
               compressionMethod == ZipEntry.kCompressStored);

        /* make sure we're in a reasonable state */
        assert(fis != null);
        assert(mEntries.size() == mEOCD.mTotalNumEntries);

        /* make sure it doesn't already exist */
        if (getEntryByName(storageName) != null)
            return null;

        if (data == null) {
            inputFp = fopen(fileName, FILE_OPEN_RO);
            if (inputFp == null)
                return errnoToStatus(errno);
        }

        ZipFile.setFilePointerPosition(fis, mEOCD.mCentralDirOffset);

        pEntry.initNew(storageName, null);

        /*
         * From here on out, failures are more interesting.
         */
        needCDRewrite = true;

        /*
         * Write the LFH, even though it's still mostly blank.  We need it
         * as a place-holder.  In theory the LFH isn't necessary, but in
         * practice some utilities demand it.
         */
        lfhPosn = fis.getChannel().position();
        pEntry.mLFH.write(fis);
        startPosn = fis.getChannel().position();

        /*
         * Copy the data in, possibly compressing it as we go.
         */
        if (sourceType == ZipEntry.kCompressStored) {
            if (compressionMethod == ZipEntry.kCompressDeflated) {
                boolean failed = false;
                if (compressFpToFp(mZipFp, inputFp, data, size, &crc) == null) {
                    System.err.println("compression failed, storing");
                    failed = true;
                } else {
                    /*
                     * Make sure it has compressed "enough".  This probably ought
                     * to be set through an API call, but I don't expect our
                     * criteria to change over time.
                     */
                    long src = inputFp ? ftell(inputFp) : size;
                    long dst = ftell(mZipFp) - startPosn;
                    if (dst + (dst / 10) > src) {
                        //ALOGD("insufficient compression (src=%ld dst=%ld), storing\n",
                          //  src, dst);
                        failed = true;
                    }
                }

                if (failed) {
                    compressionMethod = ZipEntry.kCompressStored;
                    if (inputFp) rewind(inputFp);
                    fseek(mZipFp, startPosn, SEEK_SET);
                    /* fall through to kCompressStored case */
                }
            }
            /* handle "no compression" request, or failed compression from above */
            if (compressionMethod == ZipEntry.kCompressStored) {
                if (inputFp) {
                    result = copyFpToFp(mZipFp, inputFp, &crc);
                } else {
                    result = copyDataToFp(mZipFp, data, size, &crc);
                }
                if (result != NO_ERROR) {
                    // don't need to truncate; happens in CDE rewrite
                    cout << "failed copying data in\n";
                    goto bail;
                }
            }

            // currently seeked to end of file
            uncompressedLen = inputFp ? ftell(inputFp) : size;
        } else if (sourceType == ZipEntry.kCompressDeflated) {
            /* we should support uncompressed-from-compressed, but it's not
             * important right now */
            assert(compressionMethod == ZipEntry.kCompressDeflated);

            boolean scanResult;
            int method;
            long compressedLen;

            scanResult = ZipUtils.examineGzip(inputFp, &method, &uncompressedLen,
                            &compressedLen, &crc);
            if (!scanResult || method != ZipEntry.kCompressDeflated) {
               	System.err.println("this isn't a deflated gzip file?");
                return null;
            }

            result = copyPartialFpToFp(mZipFp, inputFp, compressedLen, NULL);
            if (result != NO_ERROR) {
                cout << "failed copying gzip data in\n";
                goto bail;
            }
        } else {
        	System.err.println("Unexpected sourceType: " + sourceType);
            return false;
        }

        /*
         * We could write the "Data Descriptor", but there doesn't seem to
         * be any point since we're going to go back and write the LFH.
         *
         * Update file offsets.
         */
        endPosn = ftell(mZipFp);            // seeked to end of compressed data

        /*
         * Success!  Fill out new values.
         */
        pEntry.setDataInfo(uncompressedLen, endPosn - startPosn, crc,
            compressionMethod);
        modWhen = getModTime(inputFp ? fileno(inputFp) : fileno(mZipFp));
        pEntry.setModWhen(modWhen);
        pEntry.setLFHOffset(lfhPosn);
        mEOCD.mNumEntries++;
        mEOCD.mTotalNumEntries++;
        mEOCD.mCentralDirSize = 0;      // mark invalid; set by flush()
        mEOCD.mCentralDirOffset = endPosn;

        /*
         * Go back and write the LFH.
         */
        ZipFile.setFilePointerPosition(mZipFp, lfhPosn);
        pEntry.mLFH.write(mZipFp);

        /*
         * Add pEntry to the list.
         */
        mEntries.add(pEntry);
        
        // TODO why need to return this??
        return pEntry;
    }

}
