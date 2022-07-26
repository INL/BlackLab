package nl.inl.blacklab.forwardindex;

import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

/**
 * Read terms from an index segment.
 */
public interface TermsSegmentReader {

    /**
     * Get the term string for a term id.
     *
     * @param id term id
     * @return term string
     */
    String get(int id);

    /**
     * Check if two terms are considered equal for the given sensitivity.
     * @param termId term id
     * @param sensitivity how to compare the terms
     * @return true if the terms are equal
     */
    boolean termsEqual(int[] termId, MatchSensitivity sensitivity);
}
