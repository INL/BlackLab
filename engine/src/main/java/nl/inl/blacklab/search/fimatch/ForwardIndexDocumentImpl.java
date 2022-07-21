package nl.inl.blacklab.search.fimatch;

import java.util.ArrayList;
import java.util.List;

import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

/** Source of tokens for the forward index matching process.
 *
 * Not threadsafe. Used from Spans. An instance is created
 * per document, and a document only occurs in one index segment
 * (so only one Spans), so this doesn't need threadsafety.
 */
class ForwardIndexDocumentImpl implements ForwardIndexDocument {

    /** Default size for our chunks */
    private static final int CHUNK_SIZE = 10;

    /** How to access our forward indexes (for the current segment) */
    private final ForwardIndexAccessorLeafReader fiAccessor;

    /** Document id (within the segment) of the document we're looking at */
    private final int docId;

    /** Number of tokens in document.
     *  NOTE: This does NOT include the extra closing token at the end.
     */
    private final int docLengthTokens;

    /**
     * Chunks of the document from the forward index, for each of the annotations.
     */
    private final List<List<int[]>> allAnnotChunks = new ArrayList<>();

    /**
     * Construct a token reader for one or more annotations from one forward index document.
     *
     * @param fiAccessor forward index accessor for this segment
     * @param docId document if within this segment
     */
    public ForwardIndexDocumentImpl(ForwardIndexAccessorLeafReader fiAccessor, int docId) {
        this.fiAccessor = fiAccessor;
        this.docId = docId;
        this.docLengthTokens = fiAccessor.getDocLength(docId);

        // Create empty lists of chunks for each annotation
        for (int i = 0; i < fiAccessor.getNumberOfAnnotations(); i++) {
            allAnnotChunks.add(new ArrayList<>());
        }
    }

    @Override
    public int getToken(int annotIndex, int pos) {
        if (pos < 0 || pos >= docLengthTokens)
            return Terms.NO_TERM;

        // Get the list of chunks for the annotation we're interested in,
        // and the forward index object to get more.
        List<int[]> chunks = allAnnotChunks.get(annotIndex);

        // Where can our token be found?
        int whichChunk = pos / CHUNK_SIZE;
        int posWithinChunk = pos % CHUNK_SIZE;

        // Make sure we have the chunk we need:
        // First, make sure the list is long enough.
        // (we fill with nulls to avoid fetching chunks we don't need)
        while (chunks.size() <= whichChunk)
            chunks.add(null);
        // Now, see if we have the chunk we want, and fetch it if not
        int[] chunk = chunks.get(whichChunk);
        if (chunk == null) {
            chunk = fetchChunk(annotIndex, whichChunk);
            chunks.set(whichChunk, chunk);
        }

        return chunk[posWithinChunk];

    }

    /**
     * Fetch a chunk from the forward index for the specified annotation.
     * 
     * @param annotIndex which annotation we want a forward index chunk for
     * @param number the chunk number to fetch
     * @return the chunk
     */
    private int[] fetchChunk(int annotIndex, int number) {
        int start = number * CHUNK_SIZE;
        int end = start + CHUNK_SIZE;
        if (end > docLengthTokens) {
            end = docLengthTokens;
        }
        return fiAccessor.getChunk(annotIndex, docId, start, end);
    }

    @Override
    public String getTermString(int annotIndex, int termId) {
        return fiAccessor.getTermString(annotIndex, termId);
    }

    @Override
    public boolean termsEqual(int annotIndex, int[] termId, MatchSensitivity sensitivity) {
        return fiAccessor.termsEqual(annotIndex, termId, sensitivity);
    }

    @Override
    public boolean validPos(int pos) {
        return pos >= 0 && pos < docLengthTokens;
    }

}
