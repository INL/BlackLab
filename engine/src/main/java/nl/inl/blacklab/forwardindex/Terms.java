package nl.inl.blacklab.forwardindex;

import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import org.eclipse.collections.api.set.primitive.MutableIntSet;

import java.io.File;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.IntBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.charset.Charset;
import java.text.Collator;

/**
 * Keeps a list of unique terms and their sort positions.
 */
public abstract class Terms {

    public static final int NO_TERM = -1;

    protected static final Charset DEFAULT_CHARSET = Charset.forName("utf-8");

    /**
     * We set this to a lower value on Windows because we can't properly truncate
     * the file due to the file still being mapped (there is no clean way to unmap a
     * mapped file in Java, and Windows doesn't allow truncating a mapped file). The
     * lower value on Windows prevents too much wasted space.
     */
    protected static final int DEFAULT_MAX_MAP_SIZE = File.separatorChar == '\\' ? 100_000_000 : Integer.MAX_VALUE - 100;

    /** Number of bytes per int */
    protected static final int BYTES_PER_INT = Integer.SIZE / Byte.SIZE;

    /** How many terms total are there? (always valid) */
    int numberOfTerms = 0;

    /** Search mode only: the terms, by index number. */
    String[] terms;

    /**
     * Collator to use for string comparisons
     */
    Collator collator;

    /**
     * Collator to use for insensitive string comparisons
     */
    Collator collatorInsensitive;

    /**
     * The maximum block size to use while writing the terms file. Usually around
     * the limit of 2GB, but for testing, we can set this to a lower value. Note
     * that this should be significantly larger than maxBlockSize, because we also
     * need to store offsets.
     */
    int maxMapSize = DEFAULT_MAX_MAP_SIZE;

    public void initialize() {
        // NOP
    }

    /**
     * Get the existing index number of a term, or add it to the term list and
     * assign it a new index number.
     *
     * In index mode, this is fast. In search mode, this is slower, because we have
     * to do a binary search through the memory-mapped terms file. However, this is
     * only done rarely.
     *
     * If you care about this being fast, call buildTermIndex() at the start of your
     * application.
     *
     * @param term the term to get the index number for
     * @return the term's index number
     */
    public abstract int indexOf(String term);

    /**
     * Get the index number(s) of terms matching a string.
     *
     * This is used in search mode when translating queries into NFAs. Depending on
     * case-sensitivity settings, a single term string may match multiple terms.
     *
     * @param results (out) index numbers for the matching term(s)
     * @param term the term to get the index number for
     * @param sensitivity compare sensitively? (case-sensitivity currently switches both case-
     *            and diacritics-sensitivity)
     */
    public abstract void indexOf(MutableIntSet results, String term, MatchSensitivity sensitivity);

    /**
     * Clear the Terms object.
     */
    public abstract void clear();

    protected IntBuffer readFromFileChannel(FileChannel fc, long fileLength) throws IOException {
        long fileMapStart = 0;
        long fileMapLength = Math.min(maxMapSize, fileLength);
        MappedByteBuffer buf = fc.map(MapMode.READ_ONLY, fileMapStart, fileMapLength);

        int n = buf.getInt();
        IntBuffer ib = buf.asIntBuffer();
        numberOfTerms = n;
        int[] termStringOffsets = new int[n + 1];
        terms = new String[n];

        // "New" format, multiple blocks of term strings if necessary, so term strings may total over 2 GB.
        // This is the only supported format now.

        // Read the term string offsets and string data block
        int currentTerm = 0;
        while (currentTerm < n) {

            int numTermsThisBlock = ib.get();
            ib.get(termStringOffsets, currentTerm, numTermsThisBlock); // term string offsets

            // Read term strings data
            int dataBlockSize = termStringOffsets[currentTerm + numTermsThisBlock] = ib.get();
            ((Buffer)buf).position(buf.position() + BYTES_PER_INT * (numTermsThisBlock + 2));
            byte[] termStringsThisBlock = new byte[dataBlockSize];
            buf.get(termStringsThisBlock);

            // Now instantiate String objects from the offsets and byte data
            int firstTermInBlock = currentTerm;
            for (; currentTerm < firstTermInBlock + numTermsThisBlock; currentTerm++) {
                int offset = termStringOffsets[currentTerm];
                int length = termStringOffsets[currentTerm + 1] - offset;
                String str = new String(termStringsThisBlock, offset, length, DEFAULT_CHARSET);

                // We need to find term for id while searching
                terms[currentTerm] = str;
            }

            // Re-map a new part of the file before we read the next block.
            // (and before we read the sort buffers)
            long bytesRead = buf.position();
            fileMapStart += bytesRead;
            fileMapLength = Math.min(maxMapSize, fileLength - fileMapStart);
            if (fileMapLength > 0) {
                buf = fc.map(MapMode.READ_ONLY, fileMapStart, fileMapLength);
                ib = buf.asIntBuffer();
            }
        }
        return ib;
    }

    /**
     * Write the terms file
     *
     * @param termsFile where to write the terms file
     */
    public abstract void write(File termsFile);

    /**
     * Get a term by id. Only works in search mode.
     *
     * @param id the term id
     * @return the corresponding term
     */
    public abstract String get(int id);

    /**
     * @return the number of terms in this object
     */
    public abstract int numberOfTerms();

    /**
     * Get the sort position for a term based on its term id
     *
     * @param id the term id
     * @param sensitivity whether we want the sensitive or insensitive sort position
     * @return the sort position
     */
    public abstract int idToSortPosition(int id, MatchSensitivity sensitivity);

    /**
     * Convert an array of term ids to sort positions
     *
     * @param termId the term ids
     * @param sortOrder the sort positions
     * @param sensitivity whether we want the sensitive or insensitive sort positions
     */
    public void toSortOrder(int[] termId, int[] sortOrder, MatchSensitivity sensitivity) {
        for (int i = 0; i < termId.length; i++) {
            sortOrder[i] = idToSortPosition(termId[i], sensitivity);
        }
    }

    /**
     * Compare two terms (from their term ids) based on their sort positions
     *
     * @param termId1 id of the first term
     * @param termId2 id of the second term
     * @param sensitivity whether we want to compare sensitively or insensitively
     * @return the comparison result (negative if term1 < term2, zero if equal,
     *         positive if term1 > term2)
     */
    public int compareSortPosition(int termId1, int termId2, MatchSensitivity sensitivity) {
        return idToSortPosition(termId1, sensitivity) - idToSortPosition(termId2, sensitivity);
    }

    public static Terms openForReading(Collators collators, File termsFile, boolean buildTermIndexesOnInit) {
        return new TermsReader(collators, termsFile, buildTermIndexesOnInit);
    }

    public static Terms openForWriting(Collators collators, File termsFile) {
        return new TermsWriter(collators, termsFile);
    }

    public abstract boolean termsEqual(int[] termId, MatchSensitivity sensitivity);

    public int deserializeToken(String term) {
        int termId;
        if (term.equals("~"))
            termId = Terms.NO_TERM; // no token, effectively a "null" value
        else {
            if (term.startsWith("~~")) {
                // tilde in first position has to be escaped
                // because of how null value is encoded
                term = term.substring(1);
            }
            termId = indexOf(term);
        }
        return termId;
    }

    public String serializeTerm(int valueTokenId) {
        String token;
        if (valueTokenId < 0)
            token = "~"; // no token, effectively a "null" value
        else {
            token = get(valueTokenId);
            if (token.length() > 0 && token.charAt(0) == '~') {
                // tilde in first position has to be escaped
                // because of how null value is encoded
                token = "~" + token;
            }
        }
        return token;
    }

}
