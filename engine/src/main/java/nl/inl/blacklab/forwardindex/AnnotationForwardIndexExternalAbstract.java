package nl.inl.blacklab.forwardindex;

import java.io.File;
import java.text.Collator;
import java.util.List;

import org.apache.lucene.document.Document;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.util.VersionFile;

/**
 * Abstract base class for AnnotationForwardIndexReader/Writer, that read and write
 * the external (non-integrated) forward index.
 *
 * External forward indexes are a legacy format. They will be deprecated and eventually removed.
 *
 * The forward index is a component that can quickly tell you what word occurs at a specific position
 * of a specific document.
 */
public abstract class AnnotationForwardIndexExternalAbstract implements AnnotationForwardIndex {

    /*
     * Supported versions:
     * 4. Large terms file support
     * 5. New collators
     *
     * Older versions, no longer supported:
     * 1. Initial version.
     * 2. Added sort index to terms file.
     * 3. New terms and docs file format; added reverse sort index and case-insensitive index to terms file.
     */

    /**
     * Current forward index format version
     */
    static final String CURRENT_VERSION = "5";

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
    static final int preferredChunkSizeBytes = MAX_DIRECT_BUFFER_SIZE / 2;

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



    /**
     * Open an external forward index.
     *
     * Automatically figures out the forward index version and instantiates the
     * right class.
     *
     * @param dir forward index directory
     * @param indexMode true iff we're in index mode (writing to the forward index);
     *         otherwise it will be read-only.
     * @param collator collator to use for sorting
     * @param create if true, create a new forward index
     * @param annotation annotation for which this is the forward index, or null if we don't know (yet)
     * @return the forward index object
     */
    public static AnnotationForwardIndex open(File dir, boolean indexMode, Collator collator, boolean create,
            Annotation annotation) {
        if (annotation != null && !annotation.hasForwardIndex())
            throw new IllegalArgumentException("Annotation doesn't have a forward index: " + annotation);

        if (!dir.exists()) {
            if (!create)
                throw new IllegalArgumentException(
                        "Annotation should have forward index but directory is missing: " + annotation);
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
                } else {
                    throw new IllegalArgumentException("Not a forward index or unsupported version: "
                            + VersionFile.report(dir) + " (fi " + CURRENT_VERSION
                            + " expected)");
                }
            }
        } else {
            // We're creating a forward index. Write version.
            VersionFile.write(dir, "fi", CURRENT_VERSION);
        }

        AnnotationForwardIndex fi;
        CollatorVersion collVersion = CollatorVersion.V2;
        switch (version) {
        case "4":
            // Large terms file, old collators
            collVersion = CollatorVersion.V1;
            break;
        case "5":
            // Large terms file, new collators
            break;
        }
        Collators collators = new Collators(collator, collVersion);
        if (indexMode)
            fi = new AnnotationForwardIndexWriter(annotation, dir, collators, create);
        else {
            if (create)
                throw new UnsupportedOperationException("create == true, but not in index mode!");
            fi = new AnnotationForwardIndexReader(annotation, dir, collators);
        }
        return fi;
    }

    /** The table of contents (TOC) file, docs.dat */
    final File tocFile;

    /** The tokens file (stores indexes into terms.dat) */
    final File tokensFile;

    /** The terms file (stores unique terms) */
    final File termsFile;

    /** The unique terms in our index */
    Terms terms = null;

    /**
     * The position (in ints) in the tokens file after the last token written. Note
     * that the actual file may be larger because we reserve space at the end.
     */
    long tokenFileEndPosition = 0;

    /**
     * If true, our Terms can be used for NFA matching (Collator is consistent with
     * other comparisons)
     */
    final boolean canDoNfaMatching;

    /** The annotation for which we're the forward index */
    final Annotation annotation;

    /** Has the tokens file been mapped? */
    protected boolean initialized = false;

    public AnnotationForwardIndexExternalAbstract(Annotation annotation, File dir, Collators collators) {
        this.annotation = annotation;
        canDoNfaMatching = collators != null && collators.version() != CollatorVersion.V1;

        termsFile = new File(dir, "terms.dat");
        tocFile = new File(dir, "docs.dat");
        tokensFile = new File(dir, "tokens.dat");
    }

    @Override
    public void initialize() {
        // NOP, subclasses may override
        initialized = true;
    }

    /**
     * Store the given content and assign an id to it
     *
     * @param content the content to store
     * @return the id assigned to the content
     */
    @Override
    public int addDocument(List<String> content) {
        return addDocument(content, null);
    }

    @Override
    public void deleteDocumentByLuceneDoc(Document d) {
        deleteDocument(Integer.parseInt(d.get(annotation().forwardIndexIdField())));
    }

    /**
     * Retrieve token ids for the entire document.
     * @param fiid forward index id
     * @return token ids for the entire document.
     */
    @Override
    public int[] getDocument(int fiid) {
        int[] fullDoc = new int[] { -1 };
        return retrievePartsInt(fiid, fullDoc, fullDoc).get(0);
    }

    /**
     * Get the Terms object in order to translate ids to token strings
     * 
     * @return the Terms object
     */
    @Override
    public Terms terms() {
        if (!initialized)
            initialize();
        return terms;
    }


    /**
     * @return total size in bytes of the tokens file.
     */
    @Override
    public long totalSize() {
        if (!initialized)
            initialize();
        return tokenFileEndPosition;
    }

    @Override
    public int getToken(int fiid, int pos) {
        // Slow/naive implementation, subclasses should override
        return retrievePartsInt(fiid, new int[] { pos }, new int[] { pos + 1 }).get(0)[0];
    }

    /**
     * The annotation for which this is the forward index
     * 
     * @return annotation
     */
    @Override
    public Annotation annotation() {
        return annotation;
    }

    @Override
    public boolean canDoNfaMatching() {
        return canDoNfaMatching;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "(" + tocFile.getParentFile() + ")";
    }

}
