package com.brooke.zipalign;

import java.nio.file.FileSystems;
import java.nio.file.Files;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;

public class ZipAlign {

	private static final int kPageAlignment = 4096;
	
	public static void main(String[] args) {
		boolean wantUsage = false;
	    boolean check = false;
	    boolean force = false;
	    boolean verbose = false;
	    boolean zopfli = false;
	    boolean pageAlignSharedLibs = false;
	    int result = 1;
	    int alignment;

	    if (args.length < 4) {
	        bail(true);
	    }
	    
	    CommandLineParser parser = new DefaultParser();
	    CommandLine cmd = parser.parse(getOptions(), args);
	    
	    if (cmd.hasOption('c')) {
	    	check = true;
	    }
	    if (cmd.hasOption('f')) {
	    	force = true;
	    }
	    if (cmd.hasOption('v')) {
	    	verbose = true;
	    }
	    if (cmd.hasOption('z')) {
	    	zopfli = true;
	    }
	    if (cmd.hasOption('p')) {
	    	pageAlignSharedLibs = true;
	    }

	    if (!cmd.hasOption("align")) {
	    	bail(true);
	    }
	    if (!cmd.hasOption("infile")) {
	    	bail(true);
	    }
	    if(!check && !cmd.hasOption("outfile")) {
	    	bail(true);
	    }

	    try {
	    	alignment = Integer.valueOf(cmd.getOptionValue("align"));
	    } catch (Exception e) {
	    	bail(true);
	    }

	    String inFileName = cmd.getOptionValue("infile");
	    
	    if (check) {
	        /* check existing archive for correct alignment */
	        if (!verify(inFileName, alignment, verbose, pageAlignSharedLibs)) {
	        	System.exit(1);
	        }
	    } else {
	        /* create the new archive */
	    	String outFileName = cmd.getOptionValue("outfile");
	    	
	        if (!process(inFileName, outFileName, alignment, force, zopfli, pageAlignSharedLibs) {
	        	System.exit(1);
	        }

	        /* trust, but verify */
	        if (result == 0) {
	            if (!verify(outFileName, alignment, verbose, pageAlignSharedLibs)) {
	            	System.exit(1);
	            }
	        }
	    }
	}

	private static Options getOptions() {
		Options options = new Options();
		options.addOption("c", false, "check");
		options.addOption("f", false, "force");
		options.addOption("v", false, "verbose");
		options.addOption("z", false, "zopfli");
		options.addOption("p", false, "page align shared libs");
		options.addOption("align", true, "alignment in bytes, e.g. '4' provides 32-bit alignment");
		
		return options;
	}
	
	private static void bail(boolean wantUsage) {
		if (wantUsage) {
	        usage();
	        System.exit(2);
	    }
	}
	
	private static void usage() {
	    System.out.println("Zip alignment utility");
	    System.out.println("Usage: zipalign [-f] [-p] [-v] [-z] -align <align> -infile <infile.zip> -outfile <outfile.zip>");
	    System.out.println("       zipalign -c [-v] -align <align> -infile <infile.zip>" );
	    System.out.println();
	    System.out.println("  -align: alignment in bytes, e.g. '4' provides 32-bit alignment");
	    System.out.println("  -infile: the input jar");
	    System.out.println("  -outfile: the output jar");
	    System.out.println("  -c: check alignment only (does not modify file)");
	    System.out.println("  -f: overwrite existing outfile.zip");
	    System.out.println("  -p: page align stored shared object files");
	    System.out.println("  -v: verbose output");
	    System.out.println("  -z: recompress using Zopfli");
	}
	
	/*
	 * Verify the alignment of a zip archive.
	 */
	private static boolean verify(String fileName, int alignment, boolean verbose,
			boolean pageAlignSharedLibs) {
		
		ZipFile zipFile = new ZipFile();
	    boolean foundBad = false;

	    if (verbose)
	        System.out.println("Verifying alignment of " + fileName + "(" + alignment + ")...");

	    if (!zipFile.open(fileName, ZipFile.kOpenReadOnly)) {
	        System.err.println("Unable to open '" + fileName + "' for verification");
	        return false;
	    }

	    int numEntries = zipFile.getNumEntries();
	    ZipEntry entry;

	    for (int i = 0; i < numEntries; i++) {
	        entry = zipFile.getEntryByIndex(i);
	        if (entry.isCompressed()) {
	            /*if (verbose) {
	                printf("%8ld %s (OK - compressed)\n",
	                    (long) pEntry->getFileOffset(), pEntry->getFileName());
	            }*/
	        } else {
	            long offset = entry.getFileOffset();
	            int alignTo = getAlignment(pageAlignSharedLibs, alignment, entry);
	            if ((offset % alignTo) != 0) {
	                /*if (verbose) {
	                    printf("%8ld %s (BAD - %ld)\n",
	                        (long) offset, pEntry->getFileName(),
	                        offset % alignTo);
	                }*/
	                foundBad = true;
	            } else {
	                /*if (verbose) {
	                    printf("%8ld %s (OK)\n",
	                        (long) offset, pEntry->getFileName());
	                }*/
	            }
	        }
	    }

	    System.out.println("Verification " + (foundBad ? "FAILED" : "succesful"));

	    return !foundBad;
	}
	
	/*
	 * Process a file.  We open the input and output files, failing if the
	 * output file exists and "force" wasn't specified.
	 */
	private static boolean process(String inFileName, String outFileName,
	    int alignment, boolean force, boolean zopfli, boolean pageAlignSharedLibs)
	{
	    ZipFile zin, zout;

	    //printf("PROCESS: align=%d in='%s' out='%s' force=%d\n",
	    //    alignment, inFileName, outFileName, force);

	    /* this mode isn't supported -- do a trivial check */
	    if (inFileName.equals(outFileName)) {
	        System.err.println("Input and output can't be same file");
	        return false;
	    }

	    /* don't overwrite existing unless given permission */
	    if (!force && Files.exists(FileSystems.getDefault().getPath(outFileName))) {
	    	System.err.println("Output file '" + outFileName + "' exists");
	        return false;
	    }

	    if (!zin.open(inFileName, ZipFile.kOpenReadOnly)) {
	    	System.err.println("Unable to open '" + inFileName + "' as zip archive");
	        return false;
	    }
	    
	    if (!zout.open(outFileName, ZipFile.kOpenReadWrite, ZipFile.kOpenCreate, ZipFile.kOpenTruncate)) {
	    	System.err.println("Unable to open '" + outFileName + "' as zip archive");
	        return false;
	    }

	    if (!copyAndAlign(zin, zout, alignment, zopfli, pageAlignSharedLibs)) {
	    	System.err.println("zipalign: failed rewriting '" + inFileName + "' to '" + outFileName + "'");
	    	return false;
	    }
	}
	
	private static int getAlignment(boolean pageAlignSharedLibs, int defaultAlignment,
		    ZipEntry entry) {

	    if (!pageAlignSharedLibs) {
	        return defaultAlignment;
	    }

	    final String filename = entry.getFileName();
	    int idx = filename.lastIndexOf('.');
//	    const char* ext = strrchr(entry.getFileName(), '.');
	    if (idx > -1) {
	    	if (filename.subSequence(idx, filename.length()).equals(".so")) {
	    		return kPageAlignment;
	    	}
	    }

	    return defaultAlignment;
	}
	
	/*
	 * Copy all entries from "zin" to "zout", aligning as needed.
	 */
	private static boolean copyAndAlign(ZipFile zin, ZipFile zout, int alignment, boolean zopfli,
	    boolean pageAlignSharedLibs) {
	    int numEntries = zin.getNumEntries();
	    ZipEntry entry;
	    int bias = 0;
//	    status_t status;

	    for (int i = 0; i < numEntries; i++) {
	        ZipEntry newEntry;
	        int padding = 0;

	        entry = zin.getEntryByIndex(i);
	        if (entry == null) {
	            System.err.println("ERROR: unable to retrieve entry " + i);
	            return false;
	        }

	        if (entry.isCompressed()) {
	            /* copy the entry without padding */
	            //printf("--- %s: orig at %ld len=%ld (compressed)\n",
	            //    pEntry->getFileName(), (long) pEntry->getFileOffset(),
	            //    (long) pEntry->getUncompressedLen());

	            if (zopfli) {
	            	newEntry = zout.addRecompress(zin, entry);
	                bias += newEntry.getCompressedLen() - entry.getCompressedLen();
	            } else {
	                status = zout.add(zin, entry, padding, &pNewEntry);
	            }
	        } else {
	            int alignTo = getAlignment(pageAlignSharedLibs, alignment, entry);

	            /*
	             * Copy the entry, adjusting as required.  We assume that the
	             * file position in the new file will be equal to the file
	             * position in the original.
	             */
	            long newOffset = entry.getFileOffset() + bias;
	            padding = (alignTo - (newOffset % alignTo)) % alignTo;

	            //printf("--- %s: orig at %ld(+%d) len=%ld, adding pad=%d\n",
	            //    pEntry->getFileName(), (long) pEntry->getFileOffset(),
	            //    bias, (long) pEntry->getUncompressedLen(), padding);
	            status = zout.add(zin, entry, padding, &pNewEntry);
	        }

	        if (status != NO_ERROR)
	            return 1;
	        bias += padding;
	        //printf(" added '%s' at %ld (pad=%d)\n",
	        //    pNewEntry->getFileName(), (long) pNewEntry->getFileOffset(),
	        //    padding);
	    }

	    return 0;
	}
}
