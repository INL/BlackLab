package nl.inl.blacklab.forwardindex;

import java.io.File;
import java.text.Collator;
import java.util.List;
import java.util.Set;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.search.BlackLabIndex;
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
     */

    /**
     * Current forward index format version
     */
    private static final String CURRENT_VERSION = "5";

    /**
     * Indicate how to translate Lucene document ids to forward index ids (by
     * looking them up in the index).
     *
     * Caches the forward index id field.
     *
     * @param reader the index
     * @param annotation annotaion for which this is the forward index
     */

    /**
     * Convert a Lucene document id to the corresponding forward index id.
     * 
     * @param docId the Lucene doc id
     * @return the forward index id
     */
    public abstract int luceneDocIdToFiid(int docId);

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
    public abstract Terms terms();

    /**
     * @return the number of documents in the forward index
     */
    public abstract int getNumDocs();

    /**
     * @return the amount of space in free blocks in the forward index.
     */
    public abstract long getFreeSpace();

    /**
     * @return the number of free blocks in the forward index.
     */
    public abstract int getFreeBlocks();

    /**
     * @return total size in bytes of the tokens file.
     */
    public abstract long getTotalSize();

    /**
     * Gets the length (in tokens) of a document
     * 
     * @param fiid forward index id of a document
     * @return length of the document
     */
    public abstract int getDocLength(int fiid);

    /** Different versions of insensitive collator */
    public enum CollatorVersion {
        V1, // ignored dash and space
        V2 // doesn't ignore dash and space
    }
    
    public static AnnotationForwardIndex open(File dir, BlackLabIndex index, Annotation annotation) {
        return open(dir, index.indexMode(), index.collator(), index.indexMode() && index.isEmpty(), annotation, new FiidLookup(index.reader(), annotation));
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
     * @param fiidLookup how to look up fiid given docId 
     * @return the forward index object
     */
    public static AnnotationForwardIndex open(File dir, boolean indexMode, Collator collator, boolean create, Annotation annotation, FiidLookup fiidLookup) {

        if (!dir.exists()) {
            if (!create)
                throw new IllegalArgumentException("ForwardIndex doesn't exist: " + dir);
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
        fi = new AnnotationForwardIndexImpl(dir, indexMode, collators, create, largeTermsFileSupport);
        if (annotation != null && fiidLookup != null) {
            fi.setIdTranslateInfo(fiidLookup, annotation);
        }
        return fi;
    }

    protected abstract void setLargeTermsFileSupport(boolean b);

    /** @return the set of all forward index ids */
    public abstract Set<Integer> idSet();

    /** A task to perform on a document in the forward index. */
    public interface ForwardIndexDocTask {
        void perform(int fiid, int[] tokenIds);
    }

    /**
     * Perform a task on each document in the forward index.
     * 
     * @param task the task to perform
     */
    public void forEachDocument(ForwardIndexDocTask task) {
        for (Integer fiid: idSet()) {
            int[] tokenIds = retrievePartsInt(fiid, new int[] { -1 }, new int[] { -1 }).get(0);
            task.perform(fiid, tokenIds);
        }
    }

    public int getToken(int fiid, int pos) {
        // Slow/naive implementation, subclasses should override
        return retrievePartsInt(fiid, new int[] { pos }, new int[] { pos + 1 }).get(0)[0];
    }

    public abstract boolean canDoNfaMatching();

    /**
     * The annotation for which this is the forward index
     * 
     * @return annotation
     */
    public abstract Annotation annotation();

    public abstract void setIdTranslateInfo(FiidLookup fiidLookup, Annotation annotation);
}
