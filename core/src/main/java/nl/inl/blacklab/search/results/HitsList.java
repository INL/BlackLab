package nl.inl.blacklab.search.results;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.search.indexmetadata.Annotation;

/**
 * A basic Hits object implemented with a list.
 */
public class HitsList extends HitsAbstract {

    /**
     * Make a wrapper Hits object for a list of Hit objects.
     *
     * Does not copy the list, but reuses it.
     *
     * @param index the index object
     * @param field field our hits came from
     * @param hits the list of hits to wrap
     * @param settings settings, or null for default
     */
    HitsList(QueryInfo queryInfo, List<Hit> hits) {
        super(queryInfo);
        this.hits = hits == null ? new ArrayList<>() : hits;
        hitsCounted = this.hits.size();
        int prevDoc = -1;
        for (Hit h : this.hits) {
            if (h.doc() != prevDoc) {
                docsRetrieved++;
                docsCounted++;
                prevDoc = h.doc();
            }
        }
    }

    /** Construct a copy of a hits object in sorted order.
     * 
     * @param hitsToSort the hits to sort
     * @param sortProp property to sort on
     * @param reverseSort if true, reverse the sort
     */
    HitsList(HitsAbstract hitsToSort, HitProperty sortProp, boolean reverseSort) {
        super(hitsToSort.queryInfo());
        try {
            hitsToSort.ensureAllHitsRead();
        } catch (InterruptedException e) {
            // (should be detected by the client)
        }
        hits = hitsToSort.hits;
        capturedGroups = hitsToSort.capturedGroups;
        hitsCounted = hitsToSort.hitsCountedSoFar();
        docsRetrieved = hitsToSort.docsProcessedSoFar();
        docsCounted = hitsToSort.docsCountedSoFar();
        sortProp = sortProp.copyWithHits(this); // we need a HitProperty with the correct Hits object
        
        // Make sure we have a sort order array of sufficient size
        int n = hitsToSort.size();
        sortOrder = new Integer[n];
        
        // Fill the array with the original hit order (0, 1, 2, ...)
        for (int i = 0; i < n; i++)
            sortOrder[i] = i;

        // If we need context, make sure we have it.
        List<Annotation> requiredContext = sortProp.needsContext();
        if (requiredContext != null)
            sortProp.setContexts(new Contexts(hitsToSort, requiredContext, sortProp.needsContextSize()));

        // Perform the actual sort.
        Arrays.sort(sortOrder, sortProp);

        if (reverseSort) {
            // Instead of creating a new Comparator that reverses the order of the
            // sort property (which adds an extra layer of indirection to each of the
            // O(n log n) comparisons), just reverse the hits now (which runs
            // in linear time).
            for (int i = 0; i < n / 2; i++) {
                sortOrder[i] = sortOrder[n - i - 1];
            }
        }
    }
    
    @Override
    public String toString() {
        return "Hits#" + hitsObjId + " (hits.size()=" + hits.size() + ")";
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
    protected void ensureHitsRead(int number) throws InterruptedException {
        // subclasses may override
    }

    @Override
    public boolean doneProcessingAndCounting() {
        return true;
    }

}
