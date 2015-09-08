package com.stone.ziputil;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

public class ZipUtils {

	private static final int kReadBufSize = 32768;
	
	/*
	 * Utility function that expands zip/gzip "deflate" compressed data
	 * into a buffer.
	 *
	 * (This is a clone of the previous function, but it takes a FILE* instead
	 * of an fd.  We could pass fileno(fd) to the above, but we can run into
	 * trouble when "fp" has a different notion of what fd's file position is.)
	 *
	 * "fp" is an open file positioned at the start of the "deflate" data
	 * "buf" must hold at least "uncompressedLen" bytes.
	 */
	/*static*/ boolean inflateToBuffer(FileInputStream fis, byte[] buf,
	    long uncompressedLen, int compressedLen) throws DataFormatException, IOException {

//	    z_stream zstream;
//	    int zerr;
	    int compRemaining;

	    assert(uncompressedLen >= 0);
	    assert(compressedLen >= 0);

	    compRemaining = compressedLen;

	    /*
	     * Initialize the zlib stream.
	     */
	    Inflater decompresser = new Inflater(true); // param is "nowrap"
		
//	    memset(&zstream, 0, sizeof(zstream));
//	    zstream.zalloc = Z_NULL;
//	    zstream.zfree = Z_NULL;
//	    zstream.opaque = Z_NULL;
//	    zstream.next_in = NULL;
//	    zstream.avail_in = 0;
//	    zstream.next_out = (Bytef*) buf;
//	    zstream.avail_out = uncompressedLen;
//	    zstream.data_type = Z_UNKNOWN;

	    /*
	     * Use the undocumented "negative window bits" feature to tell zlib
	     * that there's no zlib header waiting for it.
	     */
	    // TODO need to do this, or covered by nowrap param??
	    /*zerr = inflateInit2(zstream, -MAX_WBITS);
	    if (zerr != Z_OK) {
	        if (zerr == Z_VERSION_ERROR) {
	            System.err.println("Installed zlib is not compatible with linked version (" + ZLIB_VERSION + ")");
	        } else {
	        	System.err.println("Call to inflateInit2 failed (zerr=" + zerr + ")");
	        }
	        return false;
	    }*/

	    /*
	     * Loop while we have data.
	     */
	    do {
	        int getSize;
		    
	        try {
	        /* read as much as we can */
//	        if (zstream.avail_in == 0) {
	            getSize = (compRemaining > kReadBufSize) ? kReadBufSize : compRemaining;
	            System.out.println("+++ reading " + getSize + " bytes (" + compRemaining + " left)");

	            byte[] nextBuffer = new byte[getSize];
	            int nextSize = fis.read(nextBuffer, 0, getSize);
				
	            if (nextSize < getSize) {
	                System.err.println("inflate read failed (" + nextSize + " vs " + getSize + ")");
	                decompresser.end();
	                return false;
	            }
	            
	            compRemaining -= nextSize;

//	            zstream.next_in = nextBuffer;
//	            zstream.avail_in = nextSize;
//	        }

	        /* uncompress the data */
	        decompresser.setInput(nextBuffer, 0, nextSize);
    		byte[] result = new byte[1024]; // TODO size matter??
    		int resultLength = decompresser.inflate(result);
    		
//	        zerr = inflate(zstream, Z_NO_FLUSH);
//	        if (zerr != Z_OK && zerr != Z_STREAM_END) {
//	            System.err.println("zlib inflate call failed (zerr=" + zerr + ")");
//                return false;
//	        }

	        /* output buffer holds all, so no need to write the output */
	    	} finally {
	    		decompresser.end();
	    	}
	    } while (compRemaining > 0);

	    decompresser.end();
	    
//	    assert(zerr == Z_STREAM_END);       /* other errors should've been caught */

	    if (decompresser.getBytesRead() != compressedLen) { // TODO is this right?
	    	System.err.println("Size mismatch on inflated file (" + decompresser.getBytesRead() + " vs " + compressedLen + ")");
            return false;
	    }

	    // success!
	    return true;

//	z_bail:
//	    inflateEnd(&zstream);        /* free up any allocated structures */
//
//	bail:
//	    return result;
	}
}
