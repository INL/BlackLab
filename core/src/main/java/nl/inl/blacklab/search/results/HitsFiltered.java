package nl.inl.blacklab.search.results;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import nl.inl.blacklab.resultproperty.HitPropValue;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.Annotation;

/**
 * A Hits object that filters another.
 */
public class HitsFiltered extends HitsAbstract {

    private Lock ensureHitsReadLock = new ReentrantLock();
    
    /**
     * Document the previous hit was in, so we can count separate documents.
     */
    private int previousHitDoc = -1;

    private Hits source;

    private HitProperty filterProperty;

    private HitPropValue filterValue;
    
    private boolean doneFiltering = false;
    
    private int indexInSource = -1;

    HitsFiltered(Hits hits, HitProperty property, HitPropValue value) {
        super(hits.queryInfo());
        this.source = hits;
        this.filterProperty = property.copyWithHits(hits);
        List<Annotation> contextsNeeded = filterProperty.needsContext();
        Contexts contexts = new Contexts(hits, contextsNeeded, BlackLabIndex.DEFAULT_CONTEXT_SIZE);
        filterProperty.setContexts(contexts);
        List<Integer> contextIndices = IntStream.range(0, contextsNeeded.size()).boxed().collect(Collectors.toList());
        filterProperty.setContextIndices(contextIndices);
        this.filterValue = value;
        this.hits = new ArrayList<>();
    }
    
    @Override
    public String toString() {
        return "HitsFilter#" + hitsObjId;
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
        // Prevent locking when not required
        if (doneFiltering || number >= 0 && hits.size() >= number)
            return;
        
        // At least one hit needs to be fetched.
        // Make sure we fetch at least FETCH_HITS_MIN while we're at it, to avoid too much locking.
        if (number >= 0 && number - hits.size() < FETCH_HITS_MIN)
            number = hits.size() + FETCH_HITS_MIN;

        while (!ensureHitsReadLock.tryLock()) {
            /*
             * Another thread is already counting, we don't want to straight up block until it's done
             * as it might be counting/retrieving all results, while we might only want trying to retrieve a small fraction
             * So instead poll our own state, then if we're still missing results after that just count them ourselves
             */
            Thread.sleep(50);
            if (doneFiltering || number >= 0 && hits.size() >= number)
                return;
        }
        try {
            boolean readAllHits = number < 0;
            while (!doneFiltering && (readAllHits || hits.size() < number)) {
                // Pause if asked
                threadPauser.waitIfPaused();

                // Advance to next hit
                indexInSource++;
                if (source.hitsProcessedAtLeast(indexInSource + 1)) {
                    if (filterProperty.get(indexInSource).equals(filterValue)) {
                        // Yes, keep this hit
                        Hit hit = source.getByOriginalOrder(indexInSource);
                        hits.add(hit);
                        hitsCounted++;
                        if (hit.doc() != previousHitDoc) {
                            docsCounted++;
                            docsRetrieved++;
                            previousHitDoc = hit.doc();
                        }
                    }
                } else {
                    doneFiltering = true;
                }
            }
        } finally {
            ensureHitsReadLock.unlock();
        }
    }

    @Override
    public boolean doneProcessingAndCounting() {
        return doneFiltering;
    }
    
    @Override
    public MaxStats maxStats() {
        return MaxStats.NOT_EXCEEDED;
    }

}
