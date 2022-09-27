package nl.inl.blacklab.search.fimatch;

import net.jcip.annotations.NotThreadSafe;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

/** Source of tokens for the forward index matching process.
 *
 * Not threadsafe. Used from Spans. An instance is created
 * per document, and a document only occurs in one index segment
 * (so only one Spans), so this doesn't need threadsafety.
 */
@NotThreadSafe
public interface ForwardIndexDocument {

    /**
     * Return token at specified position.
     *
     * NOTE: returns segment-local term id!
     *
     * @param annotIndex annotation to read
     * @param pos position to read
     * @return token at this position
     */
    int getTokenSegmentTermId(int annotIndex, int pos);

    /**
     * Return token at specified position.
     *
     * NOTE: returns global term id!
     *
     * @param annotIndex annotation to read
     * @param pos position to read
     * @return token at this position
     */
    int getTokenGlobalTermId(int annotIndex, int pos);

    /**
     * Return string for term id
     * 
     * @param annotIndex annotation for which we want a term string
     * @param segmentTermId term id
     * @return corresponding term string
     */
    String getTermString(int annotIndex, int segmentTermId);

    /**
     * Are all the specified term ids equal given these sensitivity settings?
     *
     * @param annotIndex annotation we're looking at
     * @param segmentTermId term ids we're comparing
     * @param sensitivity whether we're comparing case-/diacritics-sensitively
     * @return true if all are equal, false if not
     */
    boolean segmentTermsEqual(int annotIndex, int[] segmentTermId, MatchSensitivity sensitivity);

    /**
     * Is this position valid in this document?
     * 
     * @param pos position
     * @return true if valid, false if not
     */
    boolean validPos(int pos);
}
