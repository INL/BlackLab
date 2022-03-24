package nl.inl.blacklab.tools.frequency;

/**
 * Counts of hits and docs while grouping.
 */
final class OccurranceCounts {
    public int hits;
    public int docs;

    public OccurranceCounts(int hits, int docs) {
        this.hits = hits;
        this.docs = docs;
    }
}
