package nl.inl.blacklab.search.results;

import it.unimi.dsi.fastutil.ints.IntIterator;

/**
 * An immutable Hits object.
 */
public class HitsImmutable extends HitsAbstract {

    /** Our window stats, if this is a window; null otherwise. */
    private WindowStats windowStats;

    /** Our sample parameters, if any. null if not a sample of a larger result set */
    private SampleParameters sampleParameters;

    private boolean ascendingLuceneDocIds;

    /**
     * Make a wrapper Hits object for a list of Hit objects.
     *
     * Does not copy the list, but reuses it.
     *
     * @param queryInfo query info
     * @param hits the list of hits to wrap, or null for a new list
     * @param capturedGroups the list of hits to wrap, or null for no captured groups
     */
    protected HitsImmutable(QueryInfo queryInfo, HitsInternalRead hits, CapturedGroups capturedGroups) {
        super(queryInfo, hits);
        this.capturedGroups = capturedGroups;

        hitsCounted = this.hitsArrays.size();

        // Count docs and check if doc ids are ascending
        int prevDoc = -1;
        IntIterator it = this.hitsArrays.docsIterator();
        ascendingLuceneDocIds = true;
        while (it.hasNext()) {
            int docId = it.nextInt();
            if (docId != prevDoc) {
                if (docId < prevDoc)
                    ascendingLuceneDocIds = false;
                docsRetrieved++;
                docsCounted++;
                prevDoc = docId;
            }
        }
    }

    /**
     * Construct a HitsImmutable from all its components.
     *
     * Should only be used internally.
     */
    protected HitsImmutable(
                       QueryInfo queryInfo,
                       HitsInternalRead hits,
                       WindowStats windowStats,
                       SampleParameters sampleParameters,
                       long hitsCounted,
                       long docsRetrieved,
                       long docsCounted,
                       CapturedGroups capturedGroups,
                       boolean ascendingLuceneDocIds
                       ) {
        super(queryInfo, hits);
        this.windowStats = windowStats;
        this.sampleParameters = sampleParameters;
        this.hitsCounted = hitsCounted;
        this.docsRetrieved = docsRetrieved;
        this.docsCounted = docsCounted;
        this.capturedGroups = capturedGroups;
        this.ascendingLuceneDocIds = ascendingLuceneDocIds;
    }

    @Override
    public String toString() {
        return "HitsImmutable#" + hitsObjId + " (hits.size()=" + size() + ")";
    }

    /**
     * Ensure that we have read at least as many hits as specified in the parameter.
     *
     * @param number the minimum number of hits that will have been read when this
     *            method returns (unless there are fewer hits than this); if
     *            negative, reads all hits
     */
    @Override
    protected final void ensureResultsRead(long number) {
        // immutable, results have always been read
    }

    @Override
    public boolean doneProcessingAndCounting() {
        return true;
    }

    @Override
    public SampleParameters sampleParameters() {
        return sampleParameters;
    }

    @Override
    public WindowStats windowStats() {
        return windowStats;
    }

    @Override
    public MaxStats maxStats() {
        return MaxStats.NOT_EXCEEDED;
    }

    @Override
    public boolean hasAscendingLuceneDocIds() {
        return ascendingLuceneDocIds;
    }
}
