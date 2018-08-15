package nl.inl.blacklab.search.results;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

/**
 * A basic Hits object implemented with a list.
 */
public class HitsList extends Hits {

    /** Our window stats, if this is a window; null otherwise. */
    WindowStats windowStats;
    
    private SampleParameters parameters;

    @Override
    public SampleParameters sampleParameters() {
        return parameters;
    }
    
    /**
     * Make a wrapper Hits object for a list of Hit objects.
     *
     * Does not copy the list, but reuses it.
     *
     * @param queryInfo query info
     * @param hits the list of hits to wrap, or null for a new list
     */
    public HitsList(QueryInfo queryInfo, List<Hit> hits) {
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
     * Make an empty list of hits.
     *
     * @param queryInfo query info
     */
    protected HitsList(QueryInfo queryInfo) {
        this(queryInfo, null);
    }
    
    /**
     * Construct a hits window from a Hits instance.
     *
     * @param source the larger Hits object we would like a window into
     * @param first the first hit in our window
     * @param windowSize the size of our window
     */
    HitsList(Hits source, int first, int windowSize) {
        super(source.queryInfo());

        // Error if first out of range
        boolean emptyResultSet = !source.hitsProcessedAtLeast(1);
        if (first < 0 || (emptyResultSet && first > 0) ||
                (!emptyResultSet && !source.hitsProcessedAtLeast(first + 1))) {
            throw new IllegalArgumentException("First hit out of range");
        }

        // Auto-clamp number
        int number = windowSize;
        if (!source.hitsProcessedAtLeast(first + number))
            number = source.size() - first;

        // Copy the hits we're interested in.
        if (source.hasCapturedGroups())
            capturedGroups = new CapturedGroupsImpl(source.capturedGroups().names());
        int prevDoc = -1;
        hitsCounted = 0;
        for (int i = first; i < first + number; i++) {
            Hit hit = source.get(i);
            results.add(hit);
            if (capturedGroups != null)
                capturedGroups.put(hit, source.capturedGroups().get(hit));
            // OPT: copy context as well..?
            
            if (hit.doc() != prevDoc) {
                docsRetrieved++;
                docsCounted++;
                prevDoc = hit.doc();
            }
        }
        boolean hasNext = source.hitsProcessedAtLeast(first + windowSize + 1);
        windowStats = new WindowStats(hasNext, first, windowSize, number);
    }
    
    /**
     * Samples hits.
     * 
     * @param hits hits to sample from
     * @param parameters how much to sample, and optionally, a sample seed
     */
    HitsList(Hits hits, SampleParameters parameters) {
        super(hits.queryInfo());
        this.parameters = parameters;
        Random random = new Random(parameters.seed());
        int numberOfHitsToSelect = parameters.numberOfHits(hits.size());
        if (numberOfHitsToSelect > hits.size())
            numberOfHitsToSelect = hits.size(); // default to all hits in this case
        // Choose the hits
        Set<Integer> chosenHitIndices = new TreeSet<>();
        for (int i = 0; i < numberOfHitsToSelect; i++) {
            // Choose a hit we haven't chosen yet
            int hitIndex;
            do {
                hitIndex = random.nextInt(hits.size());
            } while (chosenHitIndices.contains(hitIndex));
            chosenHitIndices.add(hitIndex);
        }
        
        // Add the hits in order of their index
        int previousDoc = -1;
        for (Integer hitIndex : chosenHitIndices) {
            Hit hit = hits.get(hitIndex);
            if (hit.doc() != previousDoc) {
                docsRetrieved++;
                docsCounted++;
                previousDoc = hit.doc();
            }
            this.results.add(hit);
            hitsCounted++;
        }
    }
    
    @Override
    public WindowStats windowStats() {
        return windowStats;
    }

    /**
     * Construct a HitsList from all its components.
     * 
     * Should only be used internally.
     */
    @SuppressWarnings("javadoc")
    public HitsList(QueryInfo queryInfo, List<Hit> hitsList, CapturedGroupsImpl capturedGroups, int hitsCounted,
            int docsRetrieved, int docsCounted) {
        super(queryInfo);
        this.results = hitsList;
        this.capturedGroups = capturedGroups;
        this.hitsCounted = hitsCounted;
        this.docsRetrieved = docsRetrieved;
        this.docsCounted = docsCounted;
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
    protected void ensureResultsRead(int number) throws InterruptedException {
        // subclasses may override
    }

    @Override
    public boolean doneProcessingAndCounting() {
        return true;
    }

    @Override
    public MaxStats maxStats() {
        return MaxStats.NOT_EXCEEDED;
    }

}
