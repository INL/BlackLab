package nl.inl.blacklab.forwardindex;

import java.io.File;
import java.text.Collator;
import java.util.List;
import java.util.Set;

import org.apache.lucene.document.Document;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.util.VersionFile;

/**
 * A component that can quickly tell you what word occurs at a specific position
 * of a specific document.
 */
public abstract class AnnotationForwardIndex {

    /*
     * File format version history:
     * 1. Initial version.
     * 2. Added sort index to terms file.
     * 3. New terms and docs file format; added reverse sort index and case-insensitive index to terms file.
     * 4. Large terms file support
     * 5. New collators
     */

    /**
     * Current forward index format version
     */
    private static final String CURRENT_VERSION = "5";

    /** The number of cached fiids we check to see if this field is set anywhere. */
    static final int NUMBER_OF_CACHE_ENTRIES_TO_CHECK = 1000;

    /**
     * Java has as limit of 2GB for MappedByteBuffer. But this could be worked
     * around using arrays of MappedByteBuffers, see:
     * http://stackoverflow.com/questions/5675748/java-memorymapping-big-files
     */
    private static final int MAX_DIRECT_BUFFER_SIZE = Integer.MAX_VALUE;

    /**
     * Desired chunk size. Usually just MAX_DIRECT_BUFFER_SIZE, but can be set to be
     * smaller (for easier testing).
     *
     * NOTE: using MAX_DIRECT_BUFFER_SIZE (2GB) failed on Linux 64 bit, so we're
     * using 1GB for now.
     */
    static int preferredChunkSizeBytes = MAX_DIRECT_BUFFER_SIZE / 2;

    /** Size of a long in bytes. */
    static final int SIZEOF_LONG = Long.SIZE / Byte.SIZE;

    /**
     * Size of an int in bytes. This will always be 4, according to the standard.
     */
    static final int SIZEOF_INT = Integer.SIZE / Byte.SIZE;

    /**
     * The number of integer positions to reserve when mapping the file for writing.
     */
    static final int WRITE_MAP_RESERVE = 250_000; // 250K integers = 1M bytes

    /** Different versions of insensitive collator */
    public enum CollatorVersion {
        V1, // ignored dash and space
        V2 // doesn't ignore dash and space
    }

    /**
     * Open a forward index.
     *
     * Automatically figures out the forward index version and instantiates the
     * right class.
     *
     * @param dir forward index directory
     * @param indexMode true iff we're in index mode (writing to the forward index);
     *            otherwise it will be read-only.
     * @param collator collator to use for sorting
     * @param create if true, create a new forward index
     * @param annotation annotation for which this is the forward index, or null if we don't know (yet)
     * @param buildTermIndexesOnInit whether to build term indexes right away or lazily
     * @return the forward index object
     */
    public static AnnotationForwardIndex open(File dir, boolean indexMode, Collator collator, boolean create, Annotation annotation, boolean buildTermIndexesOnInit) {
    
        if (annotation != null && !annotation.hasForwardIndex())
            throw new IllegalArgumentException("Annotation doesn't have a forward index: " + annotation);
        
        if (!dir.exists()) {
            if (!create)
                throw new IllegalArgumentException("Annotation should have forward index but directory is missing: " + annotation);
            if (!dir.mkdir())
                throw new BlackLabRuntimeException("Could not create dir: " + dir);
        }
    
        // Version check
        String version = CURRENT_VERSION;
        if (!indexMode || !create) {
            // We're opening an existing forward index. Check version.
            if (!VersionFile.isTypeVersion(dir, "fi", CURRENT_VERSION)) {
                if (VersionFile.isTypeVersion(dir, "fi", "4")) {
                    version = "4";
                } else if (VersionFile.isTypeVersion(dir, "fi", "3")) {
                    version = "3";
                } else if (VersionFile.isTypeVersion(dir, "fi", "2")) {
                    version = "2";
                } else {
                    throw new IllegalArgumentException("Not a forward index or wrong version: "
                            + VersionFile.report(dir) + " (fi " + CURRENT_VERSION + " expected)");
                }
            }
        } else {
            // We're creating a forward index. Write version.
            VersionFile.write(dir, "fi", CURRENT_VERSION);
        }
    
        AnnotationForwardIndex fi;
        boolean largeTermsFileSupport = true;
        CollatorVersion collVersion = CollatorVersion.V2;
        switch (version) {
        case "2":
            throw new UnsupportedOperationException(
                    "Forward index version (2) too old for this BlackLab version. Please re-index.");
        case "3":
            largeTermsFileSupport = false;
            collVersion = CollatorVersion.V1;
            break;
        case "4":
            collVersion = CollatorVersion.V1;
            break;
        case "5":
            break;
        }
        Collators collators = new Collators(collator, collVersion);
        if (indexMode)
            fi = new AnnotationForwardIndexWriter(annotation, dir, collators, create, largeTermsFileSupport);
        else {
            if (create)
                throw new UnsupportedOperationException("create == true, but not in index mode!");
            fi = new AnnotationForwardIndexReader(annotation, dir, collators, largeTermsFileSupport, buildTermIndexesOnInit);
        }
        return fi;
    }

    /** A task to perform on a document in the forward index. */
    public interface ForwardIndexDocTask {
        void perform(int fiid, int[] tokenIds);
    }

    /** The table of contents (TOC) file, docs.dat */
    File tocFile;

    /** The tokens file (stores indexes into terms.dat) */
    File tokensFile;

    /** The terms file (stores unique terms) */
    File termsFile;

    /** The unique terms in our index */
    Terms terms = null;

    /**
     * The position (in ints) in the tokens file after the last token written. Note
     * that the actual file may be larger because we reserve space at the end.
     */
    long tokenFileEndPosition = 0;

    /**
     * If true, we use the new, block-based terms file, that can grow larger than 2
     * GB.
     */
    boolean useBlockBasedTermsFile = true;

    /**
     * If true, our Terms can be used for NFA matching (Collator is consistent with
     * other comparisons)
     */
    boolean canDoNfaMatching;

    /** The annotation for which we're the forward index */
    Annotation annotation;

    /** Has the tokens file been mapped? */
    protected boolean initialized = false;

    public AnnotationForwardIndex(Annotation annotation, File dir, Collators collators, boolean largeTermsFileSupport) {
        this.annotation = annotation;
        canDoNfaMatching = collators == null ? false : collators.version() != CollatorVersion.V1;

        termsFile = new File(dir, "terms.dat");
        tocFile = new File(dir, "docs.dat");
        tokensFile = new File(dir, "tokens.dat");
        
        setLargeTermsFileSupport(largeTermsFileSupport);
    }
    
    public void initialize() {
        // NOP, subclasses may override
        initialized = true;
    }
    
    /**
     * Close the forward index. Writes the table of contents to disk if modified.
     */
    public abstract void close();

    /**
     * Store the given content and assign an id to it.
     *
     * Note that if more than one token occurs at any position, we only store the
     * first in the forward index.
     *
     * @param content the content to store
     * @param posIncr the associated position increments, or null if position
     *            increment is always 1.
     * @return the id assigned to the content
     */
    public abstract int addDocument(List<String> content, List<Integer> posIncr);

    /**
     * Store the given content and assign an id to it
     *
     * @param content the content to store
     * @return the id assigned to the content
     */
    public int addDocument(List<String> content) {
        return addDocument(content, null);
    }

    /**
     * Delete a document from the forward index
     *
     * @param fiid id of the document to delete
     */
    public abstract void deleteDocument(int fiid);

    public void deleteDocumentByLuceneDoc(Document d) {
        deleteDocument(Integer.parseInt(d.get(annotation().forwardIndexIdField())));
    }
    
    /**
     * Retrieve one or more parts from the specified content, in the form of token
     * ids.
     *
     * This is more efficient than retrieving the whole content, or retrieving parts
     * in separate calls, because the file is only opened once and random access is
     * used to read only the required parts.
     *
     * NOTE: if offset and length are both -1, retrieves the whole content. This is
     * used by the retrieve(id) method.
     *
     * NOTE2: Mapped file IO on Windows has some issues that sometimes cause an
     * OutOfMemoryError on the FileChannel.map() call (which makes no sense, because
     * memory mapping only uses address space, it doesn't try to read the whole
     * file). Possibly this could be solved by using 64-bit Java, but we haven't
     * tried. For now we just disable memory mapping on Windows.
     *
     * @param fiid forward index document id
     * @param start the starting points of the parts to retrieve (in words) (-1 for
     *            start of document)
     * @param end the end points (i.e. first token beyond) of the parts to retrieve
     *            (in words) (-1 for end of document)
     * @return the parts
     */
    public abstract List<int[]> retrievePartsInt(int fiid, int[] start, int[] end);

    /**
     * Get the Terms object in order to translate ids to token strings
     * 
     * @return the Terms object
     */
    public Terms terms() {
        if (!initialized)
            initialize();
        return terms;
    }

    /**
     * @return the number of documents in the forward index
     */
    public abstract int numDocs();

    /**
     * @return the amount of space in free blocks in the forward index.
     */
    public abstract long freeSpace();

    /**
     * @return the number of free blocks in the forward index.
     */
    public abstract int freeBlocks();


    /**
     * @return total size in bytes of the tokens file.
     */
    public long totalSize() {
        if (!initialized)
            initialize();
        return tokenFileEndPosition;
    }

    /**
     * Gets the length (in tokens) of a document
     * 
     * @param fiid forward index id of a document
     * @return length of the document
     */
    public abstract int docLength(int fiid);

    protected void setLargeTermsFileSupport(boolean b) {
        this.useBlockBasedTermsFile = b;
    }

    /**
     * Perform a task on each document in the forward index.
     * 
     * @param task the task to perform
     */
    public void forEachDocument(ForwardIndexDocTask task) {
        if (!initialized)
            initialize();
        for (Integer fiid: idSet()) {
            int[] tokenIds = retrievePartsInt(fiid, new int[] { -1 }, new int[] { -1 }).get(0);
            task.perform(fiid, tokenIds);
        }
    }

    public int getToken(int fiid, int pos) {
        // Slow/naive implementation, subclasses should override
        return retrievePartsInt(fiid, new int[] { pos }, new int[] { pos + 1 }).get(0)[0];
    }

    /**
     * The annotation for which this is the forward index
     * 
     * @return annotation
     */
    public Annotation annotation() {
        return annotation;
    }
    
    public abstract Set<Integer> idSet();

    public boolean canDoNfaMatching() {
        return canDoNfaMatching;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "(" + tocFile.getParentFile() + ")";
    }

}
