package nl.inl.blacklab.search.results;

import java.util.ArrayList;
import java.util.List;

/**
 * A basic Hits object implemented with a list.
 */
public class HitsList extends Hits {

    private static List<Hit> createHitList(int[] doc, int[] start, int[] end) {
        List<Hit> hits = new ArrayList<>();
        for (int i = 0; i < doc.length; i++) {
            hits.add(Hit.create(doc[i], start[i], end[i]));
        }
        return hits;
    }

    /** Our window stats, if this is a window; null otherwise. */
    private WindowStats windowStats;
    
    private SampleParameters sampleParameters;

    /**
     * Make a wrapper Hits object for a list of Hit objects.
     *
     * Does not copy the list, but reuses it.
     *
     * @param queryInfo query info
     * @param hits the list of hits to wrap, or null for a new list
     */
    protected HitsList(QueryInfo queryInfo, List<Hit> hits) {
        super(queryInfo);
        this.results = hits == null ? new ArrayList<>() : hits;
        hitsCounted = this.results.size();
        int prevDoc = -1;
        for (Hit h : this.results) {
            if (h.doc() != prevDoc) {
                docsRetrieved++;
                docsCounted++;
                prevDoc = h.doc();
            }
        }
    }
    
    /**
     * Create a list of hits from three arrays.
     *
     * Mainly useful for testing.
     *
     * @param queryInfo query info
     * @param doc document ids
     * @param start hit starts
     * @param end hit ends
     */
    protected HitsList(QueryInfo queryInfo, int[] doc, int[] start, int[] end) {
        this(queryInfo, createHitList(doc, start, end));
    }

    /**
     * Make an empty list of hits.
     *
     * @param queryInfo query info
     */
    protected HitsList(QueryInfo queryInfo) {
        this(queryInfo, null);
    }
    
    /**
     * Construct a HitsList from all its components.
     * 
     * Should only be used internally.
     */
    protected HitsList(QueryInfo queryInfo, List<Hit> results, WindowStats windowStats, SampleParameters sampleParameters,
            int hitsCounted, int docsRetrieved, int docsCounted, CapturedGroupsImpl capturedGroups) {
        super(queryInfo);
        this.results = results;
        this.windowStats = windowStats;
        this.sampleParameters = sampleParameters;
        
        this.hitsCounted = hitsCounted;
        this.docsRetrieved = docsRetrieved;
        this.docsCounted = docsCounted;
        this.capturedGroups = capturedGroups;
    }

    @Override
    public String toString() {
        return "HitsList#" + hitsObjId + " (hits.size()=" + results.size() + "; isWindow=" + isWindow() + ")";
    }
    
    /**
     * Ensure that we have read at least as many hits as specified in the parameter.
     *
     * @param number the minimum number of hits that will have been read when this
     *            method returns (unless there are fewer hits than this); if
     *            negative, reads all hits
     * @throws InterruptedException if the thread was interrupted during this
     *             operation
     */
    @Override
    protected void ensureResultsRead(int number) {
        // subclasses may override
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

}
