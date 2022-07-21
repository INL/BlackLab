package nl.inl.blacklab.search.fimatch;

import net.jcip.annotations.NotThreadSafe;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

/**
 * Allows the forward index matching subsystem to access the forward indices
 * in a single index segment (LeafReader).
 *
 * Not intended to be threadsafe, but to be used from e.g. Spans in a single thread.
 */
@NotThreadSafe
public interface ForwardIndexAccessorLeafReader {
    /**
     * Get a token source, which we can use to get tokens from a document for
     * different annotations.
     *
     * @param docId document id within this segment
     * @return the token source
     */
    ForwardIndexDocument advanceForwardIndexDoc(int docId);

    /**
     * Return the document length in tokens.
     *
     * NOTE: this does NOT include the extra closing token at the end.
     *
     * @param docId document id within this segment
     * @return document length in tokens
     */
    int getDocLength(int docId);

    /**
     * Get a chunk of tokens from a forward index
     *
     * @param annotIndex annotation to get tokens for
     * @param docId document id within this segment
     * @param start first token to get
     * @param end one more than the last token to get
     * @return chunk of tokens
     */
    int[] getChunk(int annotIndex, int docId, int start, int end);

    int getNumberOfAnnotations();

    String getTermString(int annotIndex, int termId);

    boolean termsEqual(int annotIndex, int[] termId, MatchSensitivity sensitivity);
}
