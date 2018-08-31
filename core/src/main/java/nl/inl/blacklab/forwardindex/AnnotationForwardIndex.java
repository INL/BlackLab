package nl.inl.blacklab.forwardindex;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.text.Collator;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
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
    static final int WRITE_MAP_RESERVE = 250000; // 250K integers = 1M bytes

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
     * @param fiidLookup how to look up fiid given docId 
     * @return the forward index object
     */
    public static AnnotationForwardIndex open(File dir, boolean indexMode, Collator collator, boolean create, Annotation annotation, FiidLookup fiidLookup) {
    
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
            fi = new AnnotationForwardIndexWriter(dir, collators, create, largeTermsFileSupport);
        else {
            if (create)
                throw new UnsupportedOperationException("create == true, but not in index mode!");
            fi = new AnnotationForwardIndexReader(dir, collators, largeTermsFileSupport);
        }
        if (annotation != null && fiidLookup != null) {
            fi.setIdTranslateInfo(fiidLookup, annotation);
        }
        return fi;
    }

    /** A task to perform on a document in the forward index. */
    public interface ForwardIndexDocTask {
        void perform(int fiid, int[] tokenIds);
    }

    /**
     * The table of contents (where documents start in the tokens file and how long
     * they are)
     */
    ArrayList<TocEntry> toc = new ArrayList<>();

    /** Deleted TOC entries. Always sorted by size. */
    ArrayList<TocEntry> deletedTocEntries = new ArrayList<>();

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

    /** How we look up forward index id in the index. */
    FiidLookup fiidLookup;

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

    public AnnotationForwardIndex(File dir, Collators collators, boolean largeTermsFileSupport) {
        canDoNfaMatching = collators == null ? false : collators.version() != CollatorVersion.V1;

        termsFile = new File(dir, "terms.dat");
        tocFile = new File(dir, "docs.dat");
        tokensFile = new File(dir, "tokens.dat");
        
        setLargeTermsFileSupport(largeTermsFileSupport);
    }
    
    public void initialize() {
        // NOP
    }

    /**
     * Convert a Lucene document id to the corresponding forward index id.
     * 
     * @param docId the Lucene doc id
     * @return the forward index id
     */
    protected int luceneDocIdToFiid(int docId) {
        return (int) fiidLookup.get(docId);
    }

    /**
     * Read the table of contents from the file
     */
    protected void readToc() {
        toc.clear();
        deletedTocEntries.clear();
        try (RandomAccessFile raf = new RandomAccessFile(tocFile, "r");
                FileChannel fc = raf.getChannel()) {
            long fileSize = tocFile.length();
            MappedByteBuffer buf = fc.map(MapMode.READ_ONLY, 0, fileSize);
            int n = buf.getInt();
            long[] offset = new long[n];
            int[] length = new int[n];
            byte[] deleted = new byte[n];
            LongBuffer lb = buf.asLongBuffer();
            lb.get(offset);
            buf.position(buf.position() + SIZEOF_LONG * n);
            IntBuffer ib = buf.asIntBuffer();
            ib.get(length);
            buf.position(buf.position() + SIZEOF_INT * n);
            buf.get(deleted);
            toc.ensureCapacity(n);
            for (int i = 0; i < n; i++) {
                TocEntry e = new TocEntry(offset[i], length[i], deleted[i] != 0);
                toc.add(e);
                if (e.deleted) {
                    deletedTocEntries.add(e);
                }
                long end = e.offset + e.length;
                if (end > tokenFileEndPosition)
                    tokenFileEndPosition = end;
            }
            sortDeletedTocEntries();
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
        toc.trimToSize();
        deletedTocEntries.trimToSize();
    }

    protected void sortDeletedTocEntries() {
        deletedTocEntries.sort(new Comparator<TocEntry>() {
            @Override
            public int compare(TocEntry o1, TocEntry o2) {
                return o1.length - o2.length;
            }
        });
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
    public abstract void deleteDocumentByFiid(int fiid);

    public void deleteDocument(int docId) {
        deleteDocumentByFiid(luceneDocIdToFiid(docId));
    }

    public void deleteDocumentByLuceneDoc(Document d) {
        deleteDocumentByFiid(Integer.parseInt(d.get(annotation().forwardIndexIdField())));
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
    public abstract List<int[]> retrievePartsIntByFiid(int fiid, int[] start, int[] end);

    public List<int[]> retrievePartsInt(int docId, int[] start, int[] end) {
        return retrievePartsIntByFiid(luceneDocIdToFiid(docId), start, end);
    }

    /**
     * Get the Terms object in order to translate ids to token strings
     * 
     * @return the Terms object
     */
    public Terms terms() {
        return terms;
    }

    /**
     * @return the number of documents in the forward index
     */
    public int numDocs() {
        return toc.size();
    }

    /**
     * @return the amount of space in free blocks in the forward index.
     */
    public long freeSpace() {
        long freeSpace = 0;
        for (TocEntry e : deletedTocEntries) {
            freeSpace += e.length;
        }
        return freeSpace;
    }

    /**
     * @return the number of free blocks in the forward index.
     */
    public int freeBlocks() {
        return deletedTocEntries.size();
    }


    /**
     * @return total size in bytes of the tokens file.
     */
    public long totalSize() {
        return tokenFileEndPosition;
    }

    /**
     * Gets the length (in tokens) of a document
     * 
     * @param fiid forward index id of a document
     * @return length of the document
     */
    public int docLengthByFiid(int fiid) {
        return toc.get(fiid).length;
    }

    public int docLength(int docId) {
        return docLengthByFiid(luceneDocIdToFiid(docId));
    }

    protected void setLargeTermsFileSupport(boolean b) {
        this.useBlockBasedTermsFile = b;
    }

    /**
     * Perform a task on each document in the forward index.
     * 
     * @param task the task to perform
     */
    public void forEachDocument(ForwardIndexDocTask task) {
        for (Integer fiid: idSet()) {
            int[] tokenIds = retrievePartsIntByFiid(fiid, new int[] { -1 }, new int[] { -1 }).get(0);
            task.perform(fiid, tokenIds);
        }
    }

    public int getToken(int fiid, int pos) {
        // Slow/naive implementation, subclasses should override
        return retrievePartsIntByFiid(fiid, new int[] { pos }, new int[] { pos + 1 }).get(0)[0];
    }

    /**
     * The annotation for which this is the forward index
     * 
     * @return annotation
     */
    public Annotation annotation() {
        return annotation;
    }

    /**
     * Indicate how to translate Lucene document ids to forward index ids (by
     * looking them up in the index).
     *
     * Caches the forward index id field.
     * 
     * @param fiidLookup how to look up fiids
     * @param annotation annotation for which this is the forward index
     */
    public void setIdTranslateInfo(FiidLookup fiidLookup, Annotation annotation) {
        this.annotation = annotation;
        this.fiidLookup = fiidLookup;
    }

    /** @return the set of all forward index ids */
    public Set<Integer> idSet() {
        return new AbstractSet<Integer>() {
            @Override
            public boolean contains(Object o) {
                return !toc.get((Integer) o).deleted;
            }

            @Override
            public boolean isEmpty() {
                return toc.size() == deletedTocEntries.size();
            }

            @Override
            public Iterator<Integer> iterator() {
                return new Iterator<Integer>() {
                    int current = -1;
                    int next = -1;

                    @Override
                    public boolean hasNext() {
                        if (next < 0)
                            findNext();
                        return next < toc.size();
                    }

                    private void findNext() {
                        next = current + 1;
                        while (next < toc.size() && toc.get(next).deleted) {
                            next++;
                        }
                    }

                    @Override
                    public Integer next() {
                        if (next < 0)
                            findNext();
                        if (next >= toc.size())
                            throw new NoSuchElementException();
                        current = next;
                        next = -1;
                        return current;
                    }

                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }

            @Override
            public int size() {
                return toc.size() - deletedTocEntries.size();
            }
        };
    }

    public boolean canDoNfaMatching() {
        return canDoNfaMatching;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "(" + tocFile.getParentFile() + ")";
    }

}
