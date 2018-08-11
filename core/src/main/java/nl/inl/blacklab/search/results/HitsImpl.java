package nl.inl.blacklab.search.results;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.util.ThreadPauser;

/**
 * A basic Hits object implemented with a list.
 */
public class HitsImpl extends HitsAbstract {
    
    // Instance variables
    //--------------------------------------------------------------------

    // Fetching information

    /**
     * The number of hits we've seen and counted so far. May be more than the number
     * of hits we've retrieved if that exceeds maxHitsToRetrieve.
     */
    protected int hitsCounted = 0;

    /**
     * The number of separate documents we've seen in the hits retrieved.
     */
    protected int docsRetrieved = 0;

    /**
     * The number of separate documents we've counted so far (includes non-retrieved
     * hits).
     */
    protected int docsCounted = 0;

    
    // Hit information

    /**
     * The hits.
     */
    protected List<Hit> hits;

    /**
     * The sort order, if we've sorted, or null if not.
     * 
     * Note that, after initial creation of the Hits object, sortOrder is immutable.
     */
    private Integer[] sortOrder;

    /**
     * Our captured groups, or null if we have none.
     */
    CapturedGroupsImpl capturedGroups;
    

    // Constructors
    //--------------------------------------------------------------------

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
    HitsImpl(QueryInfo queryInfo, List<Hit> hits) {
        super(queryInfo);
        this.hits = hits == null ? new ArrayList<>() : hits;
        hitsCounted = this.hits.size();
        int prevDoc = -1;
        docsRetrieved = docsCounted = 0;
        for (Hit h : this.hits) {
            if (h.doc() != prevDoc) {
                docsRetrieved++;
                docsCounted++;
                prevDoc = h.doc();
            }
        }
        threadPauser = new ThreadPauser();
    }

    /**
     * Construct a Hits object from an existing Hits object.
     *
     * The same hits list is reused. Context and sort order are not copied. All
     * other fields are.
     *
     * @param copyFrom the Hits object to copy
     * @param settings settings to override, or null to copy
     */
    private HitsImpl(HitsImpl copyFrom) {
        super(copyFrom.queryInfo);
        try {
            copyFrom.ensureAllHitsRead();
        } catch (InterruptedException e) {
            // (should be detected by the client)
        }
        hits = copyFrom.hits;
        capturedGroups = copyFrom.capturedGroups;
        hitsCounted = copyFrom.hitsCountedSoFar();
        docsRetrieved = copyFrom.docsProcessedSoFar();
        docsCounted = copyFrom.docsCountedSoFar();
    }

    
    // Copying hits objects (and their relevant settings)
    //--------------------------------------------------------------------
    
    @Override
    public HitsImpl copy() {
        return new HitsImpl(this);
    }
    

    // Deriving other Hits / Results instances
    //--------------------------------------------------------------------
    
    @Override
    public Hits sortedBy(final HitProperty sortProp) {
        return sortedBy(sortProp, false);
    }

    @Override
    public Hits sortedBy(HitProperty sortProp, boolean reverseSort) {
        // Sort hits
        try {
            ensureAllHitsRead();
        } catch (InterruptedException e) {
            // Thread was interrupted; don't complete the operation but return
            // and let the caller detect and deal with the interruption.
            Thread.currentThread().interrupt();
            return this;
        }

        HitsImpl hits = copy();
        sortProp = sortProp.copyWithHits(hits);
        
        // Make sure we have a sort order array of sufficient size
        if (hits.sortOrder == null || hits.sortOrder.length < hits.size()) {
            hits.sortOrder = new Integer[hits.size()];
        }
        // Fill the array with the original hit order (0, 1, 2, ...)
        int n = hits.size();
        for (int i = 0; i < n; i++)
            hits.sortOrder[i] = i;

        // If we need context, make sure we have it.
        List<Annotation> requiredContext = sortProp.needsContext();
        if (requiredContext != null)
            sortProp.setContexts(new Contexts(hits, requiredContext, sortProp.needsContextSize()));

        // Perform the actual sort.
        Arrays.sort(hits.sortOrder, sortProp);

        if (reverseSort) {
            // Instead of creating a new Comparator that reverses the order of the
            // sort property (which adds an extra layer of indirection to each of the
            // O(n log n) comparisons), just reverse the hits now (which runs
            // in linear time).
            for (int i = 0; i < n / 2; i++) {
                hits.sortOrder[i] = hits.sortOrder[n - i - 1];
            }
        }
        return hits;
    }

    
    // General stuff
    //--------------------------------------------------------------------

    @Override
    public String toString() {
        return "Hits#" + hitsObjId + " (hits.size()=" + hits.size() + ")";
    }
    

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
            return Hits.emptyList(queryInfo);
        }
        List<Hit> hitsInDoc = new ArrayList<>();
        for (Hit hit : hits) {
            if (hit.doc() == docid)
                hitsInDoc.add(hit);
        }
        return Hits.fromList(queryInfo, hitsInDoc);
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

    // Hits fetching
    //--------------------------------------------------------------------

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
    protected void ensureHitsRead(int number) throws InterruptedException {
        // subclasses may override
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
            maxStats().setHitsCountedExceededMaximum(); // indicate that we've stopped counting
            Thread.currentThread().interrupt();
        }
        return hits.size();
    }

    @Override
    public int hitsCountedTotal() {
        try {
            ensureAllHitsRead();
        } catch (InterruptedException e) {
            // Abort operation. Result may be wrong, but
            // interrupted results shouldn't be shown to user anyway.
            Thread.currentThread().interrupt();
        }
        return hitsCounted;
    }

    @Override
    public int docsProcessedTotal() {
        try {
            ensureAllHitsRead();
        } catch (InterruptedException e) {
            // Abort operation. Result may be wrong, but
            // interrupted results shouldn't be shown to user anyway.
            Thread.currentThread().interrupt();
        }
        return docsRetrieved;
    }

    @Override
    public int docsCountedTotal() {
        try {
            ensureAllHitsRead();
        } catch (InterruptedException e) {
            // Abort operation. Result may be wrong, but
            // interrupted results shouldn't be shown to user anyway.
            Thread.currentThread().interrupt();
        }
        return docsCounted;
    }

    @Override
    public int hitsCountedSoFar() {
        return hitsCounted;
    }

    @Override
    public int hitsProcessedSoFar() {
        return hits.size();
    }

    @Override
    public int docsCountedSoFar() {
        return docsCounted;
    }

    @Override
    public int docsProcessedSoFar() {
        return docsRetrieved;
    }

    @Override
    public boolean doneProcessingAndCounting() {
        return true;
    }

    @Override
    protected int indexOf(Hit hit) {
        int originalIndex = hits.indexOf(hit);
        for (int i = 0; i < sortOrder.length; i++) {
            if (sortOrder[i] == originalIndex)
                return i;
        }
        return -1;
    }

}
