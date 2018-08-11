package nl.inl.blacklab.search.results;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.exceptions.ResultNotFound;
import nl.inl.blacklab.resultproperty.HitPropValue;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.util.ThreadPauser;

public abstract class HitsAbstract implements Hits {

    protected static final Logger logger = LogManager.getLogger(HitsAbstract.class);

    /** Id the next Hits instance will get */
    private static int nextHitsObjId = 0;

    private static synchronized int getNextHitsObjId() {
        return nextHitsObjId++;
    }

    /** Unique id of this Hits instance */
    protected final int hitsObjId = getNextHitsObjId();
    
    /** Information about the original query: index, field, max settings, max stats. */
    private QueryInfo queryInfo;
    
    /**
     * The hits.
     */
    protected List<Hit> hits;

    /**
     * The sort order, if we've sorted, or null if not.
     * 
     * Note that, after initial creation of the Hits object, sortOrder is immutable.
     */
    protected Integer[] sortOrder;

    /**
     * Our captured groups, or null if we have none.
     */
    protected CapturedGroupsImpl capturedGroups;
    
    /**
     * Helper object for pausing threads (making sure queries
     * don't hog the CPU for way too long).
     */
    protected ThreadPauser threadPauser;

    public HitsAbstract(QueryInfo queryInfo) {
        this.queryInfo = queryInfo;
        threadPauser = new ThreadPauser();
    }

    @Override
    public int size() {
        return hitsProcessedTotal();
    }
    
    /**
     * Ensure that we have read all hits.
     *
     * @throws InterruptedException if the thread was interrupted during this
     *             operation
     */
    protected void ensureAllHitsRead() throws InterruptedException {
        ensureHitsRead(-1);
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
    protected abstract void ensureHitsRead(int number) throws InterruptedException;
    
    // Getting / iterating over the hits
    //--------------------------------------------------------------------

    @Override
    public Iterator<Hit> iterator() {
        // Construct a custom iterator that iterates over the hits in the hits
        // list, but can also take into account the Spans object that may not have
        // been fully read. This ensures we don't instantiate Hit objects for all hits
        // if we just want to display the first few.
        return new Iterator<Hit>() {
        
            int index = -1;
        
            @Override
            public boolean hasNext() {
                // Do we still have hits in the hits list?
                try {
                    ensureHitsRead(index + 2);
                } catch (InterruptedException e) {
                    // Thread was interrupted. Don't finish reading hits and accept possibly wrong
                    // answer.
                    // Client must detect the interruption and stop the thread.
                    Thread.currentThread().interrupt();
                }
                return hits.size() >= index + 2;
            }
        
            @Override
            public Hit next() {
                // Check if there is a next, taking unread hits from Spans into account
                if (hasNext()) {
                    index++;
                    return hits.get(sortOrder == null ? index : sortOrder[index]);
                }
                throw new NoSuchElementException();
            }
        
            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        
        };
    }
    
    @Override
    public Iterable<Hit> originalOrder() {
        // Construct a custom iterator that iterates over the hits in the hits
        // list, but can also take into account the Spans object that may not have
        // been fully read. This ensures we don't instantiate Hit objects for all hits
        // if we just want to display the first few.
        return new Iterable<Hit>() {
            @Override
            public Iterator<Hit> iterator() {
                // TODO Auto-generated method stub
                return new Iterator<Hit>() {
                    int index = -1;
                
                    @Override
                    public boolean hasNext() {
                        // Do we still have hits in the hits list?
                        try {
                            ensureHitsRead(index + 2);
                        } catch (InterruptedException e) {
                            // Thread was interrupted. Don't finish reading hits and accept possibly wrong
                            // answer.
                            // Client must detect the interruption and stop the thread.
                            Thread.currentThread().interrupt();
                        }
                        return hits.size() >= index + 2;
                    }
                
                    @Override
                    public Hit next() {
                        // Check if there is a next, taking unread hits from Spans into account
                        if (hasNext()) {
                            index++;
                            return hits.get(index);
                        }
                        throw new NoSuchElementException();
                    }
                
                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                
                };
            }
        };
    }
    
    @Override
    public Hit getByOriginalOrder(int i) {
        try {
            ensureHitsRead(i + 1);
        } catch (InterruptedException e) {
            // Thread was interrupted. Required hit hasn't been gathered;
            // we will just return null.
            Thread.currentThread().interrupt();
        }
        if (i >= hits.size())
            return null;
        return hits.get(i);
    }

    @Override
    public synchronized Hit get(int i) {
        try {
            ensureHitsRead(i + 1);
        } catch (InterruptedException e) {
            // Thread was interrupted. Required hit hasn't been gathered;
            // we will just return null.
            Thread.currentThread().interrupt();
        }
        if (i >= hits.size())
            return null;
        return hits.get(sortOrder == null ? i : sortOrder[i]);
    }
    
    @Override
    public Hits getHitsInDoc(int docid) {
        try {
            ensureAllHitsRead();
        } catch (InterruptedException e) {
            // Interrupted. Just return no hits;
            // client should detect thread was interrupted if it
            // wants to use background threads.
            Thread.currentThread().interrupt();
            return Hits.emptyList(queryInfo());
        }
        List<Hit> hitsInDoc = new ArrayList<>();
        for (Hit hit : hits) {
            if (hit.doc() == docid)
                hitsInDoc.add(hit);
        }
        return Hits.fromList(queryInfo(), hitsInDoc);
    }

    @Override
    public boolean hitsProcessedAtLeast(int lowerBound) {
        try {
            // Try to fetch at least this many hits
            ensureHitsRead(lowerBound);
        } catch (InterruptedException e) {
            // Thread was interrupted; abort operation
            // and let client decide what to do
            Thread.currentThread().interrupt();
        }

        return hits.size() >= lowerBound;
    }

    @Override
    public int hitsProcessedTotal() {
        try {
            // Probably not all hits have been seen yet. Collect them all.
            ensureAllHitsRead();
        } catch (InterruptedException e) {
            // Abort operation. Result may be wrong, but
            // interrupted results shouldn't be shown to user anyway.
            queryInfo().maxStats().setHitsCountedExceededMaximum(); // indicate that we've stopped counting
            Thread.currentThread().interrupt();
        }
        return hits.size();
    }

    @Override
    public int hitsProcessedSoFar() {
        return hits.size();
    }

    protected int indexOf(Hit hit) {
        try {
            ensureAllHitsRead();
        } catch (InterruptedException e) {
            // (should be detected by the client)
        }
        int originalIndex = hits.indexOf(hit);
        for (int i = 0; i < sortOrder.length; i++) {
            if (sortOrder[i] == originalIndex)
                return i;
        }
        return -1;
    }

    // Deriving other Hits / Results instances
    //--------------------------------------------------------------------
    
    @Override
    public Hits sortedBy(final HitProperty sortProp) {
        return sortedBy(sortProp, false);
    }

    @Override
    public Hits sortedBy(HitProperty sortProp, boolean reverseSort) {
        return sorted(this, sortProp, reverseSort);
    }
    
    private static Hits sorted(HitsAbstract hits, HitProperty sortProp, boolean reverseSort) {
        return new HitsImpl(hits, sortProp, reverseSort);
    }

    @Override
    public Hits filteredBy(HitProperty property, HitPropValue value) {
        property = property.copyWithHits(this); // we need a HitProperty with the correct Hits object
        List<Annotation> requiredContext = property.needsContext();
        property.setContexts(new Contexts(this, requiredContext, property.needsContextSize()));

        List<Hit> filtered = new ArrayList<>();
        for (int i = 0; i < size(); i++) {
            if (property.get(i).equals(value))
                filtered.add(get(i));
        }
        return new HitsImpl(queryInfo, filtered);
    }

    @Override
    public HitGroups groupedBy(final HitProperty criteria) {
        return ResultsGrouper.fromHits(this, criteria);
    }

    @Override
    public DocResults perDocResults() {
        return DocResults.fromHits(queryInfo(), this);
    }

    @Override
    public TermFrequencyList collocations(int contextSize) {
        return TermFrequencyList.collocations(contextSize, this, null, null, true);
    }

    @Override
    public synchronized TermFrequencyList collocations(int contextSize, Annotation annotation, QueryExecutionContext ctx, boolean sort) {
        return TermFrequencyList.collocations(contextSize, this, annotation, ctx, sort);
    }
    
    @Override
    public HitsWindow window(int first, int windowSize) {
        return new HitsWindow(this, first, windowSize);
    }

    @Override
    public HitsWindow window(Hit hit) throws ResultNotFound {
        int i = indexOf(hit);
        if (i < 0)
            throw new ResultNotFound("Hit not found in hits list!");
        return window(i, 1);
    }

    @Override
    public int resultsObjId() {
        return hitsObjId;
    }

    @Override
    public void pause(boolean pause) {
        threadPauser.pause(pause);
    }

    @Override
    public boolean isPaused() {
        return threadPauser.isPaused();
    }
    
    @Override
    public ThreadPauser threadPauser() {
        return threadPauser;
    }

    @Override
    public QueryInfo queryInfo() {
        return queryInfo;
    }
    
    // Captured groups
    //--------------------------------------------------------------------
    
    public CapturedGroups capturedGroups() {
        return capturedGroups;
    }

    @Override
    public boolean hasCapturedGroups() {
        return capturedGroups != null;
    }

    // Hits display
    //--------------------------------------------------------------------
    
    @Override
    public Concordances concordances(int contextSize, ConcordanceType type) {
        return new Concordances(this, type, contextSize);
    }

    @Override
    public Kwics kwics(int contextSize) {
        return new Kwics(this, contextSize);
    }

}
