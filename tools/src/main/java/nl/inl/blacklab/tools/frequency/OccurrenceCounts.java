package nl.inl.blacklab.tools.frequency;

import java.io.Serializable;

/**
 * Counts of hits and docs while grouping.
 */
final class OccurrenceCounts implements Serializable {
    public int hits;
    public int docs;

    public OccurrenceCounts(int hits, int docs) {
        this.hits = hits;
        this.docs = docs;
    }
}
