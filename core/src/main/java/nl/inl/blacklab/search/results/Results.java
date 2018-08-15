package nl.inl.blacklab.search.results;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import nl.inl.blacklab.resultproperty.ResultProperty;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.util.ThreadPauser;

/**
 * A list of results of some type.
 *
 * @param <T> result type, e.g. Hit
 */
public abstract class Results<T> implements Iterable<T> {

    /** Id the next Hits instance will get */
    private static int nextHitsObjId = 0;

    private static synchronized int getNextHitsObjId() {
        return nextHitsObjId++;
    }
    
    /** Unique id of this Hits instance (for debugging) */
    protected final int hitsObjId = getNextHitsObjId();
    
    /** Information about the original query: index, field, max settings, max stats. */
    private QueryInfo queryInfo;
    
    /**
     * Helper object for pausing threads (making sure queries
     * don't hog the CPU for way too long).
     */
    protected ThreadPauser threadPauser;
    
    /**
     * The results.
     */
    protected List<T> results;

    public Results(QueryInfo queryInfo) {
        this.queryInfo = queryInfo;
        if (queryInfo.resultsObjectId() < 0)
            queryInfo.setResultsObjectId(hitsObjId); // We're the original query. set the id.
        threadPauser = new ThreadPauser();
        results = new ArrayList<>();
    }

    /**
     * Get information about the original query.
     * 
     * This includes the index, field, max. settings, and max. stats
     * (whether the max. settings were reached).
     * 
     * @return query info
     */
    public QueryInfo queryInfo() {
        return queryInfo;
    }

    /**
     * Get the field these hits are from.
     * 
     * @return field
     */
    public AnnotatedField field() {
        return queryInfo().field();
    }

    /**
     * Get the index these hits are from.
     * 
     * @return index
     */
    public BlackLabIndex index() {
        return queryInfo().index();
    }
    
    public int resultsObjId() {
        return hitsObjId;
    }

    public ThreadPauser threadPauser() {
        return threadPauser;
    }

    /**
     * Is this a hits window?
     * 
     * @return true if it's a window, false if not
     */
    public boolean isWindow() {
        return windowStats() != null;
    }

    /**
     * If this is a hits window, return the window stats.
     * 
     * @return window stats, or null if this is not a hits window
     */
    public WindowStats windowStats() {
        return null;
    }

    /**
     * Is this sampled from another instance?
     * 
     * @return true if it's a sample, false if not
     */
    public boolean isSample() {
        return sampleParameters() != null;
    }

    /**
     * If this is a sample, return the sample parameters.
     * 
     * Also includes the explicitly set or randomly chosen seed. 
     * 
     * @return sample parameters, or null if this is not a sample
     */
    public SampleParameters sampleParameters() {
        return null;
    }

    /**
     * Return an iterator over these hits.
     *
     * The order is the sorted order, not the original order. Use
     * hitsInOriginalOrder() to iterate in the original order.
     *
     * @return the iterator
     */
    @Override
    public Iterator<T> iterator() {
        // Construct a custom iterator that iterates over the hits in the hits
        // list, but can also take into account the Spans object that may not have
        // been fully read. This ensures we don't instantiate Hit objects for all hits
        // if we just want to display the first few.
        return new Iterator<T>() {
        
            int index = -1;
        
            @Override
            public boolean hasNext() {
                // Do we still have hits in the hits list?
                try {
                    ensureResultsRead(index + 2);
                } catch (InterruptedException e) {
                    // Thread was interrupted. Don't finish reading hits and accept possibly wrong
                    // answer.
                    // Client must detect the interruption and stop the thread.
                    Thread.currentThread().interrupt();
                }
                return results.size() >= index + 2;
            }
        
            @Override
            public T next() {
                // Check if there is a next, taking unread hits from Spans into account
                if (hasNext()) {
                    index++;
                    return results.get(index);
                }
                throw new NoSuchElementException();
            }
        
            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        
        };
    }
    
    /**
     * Return the specified hit.
     *
     * @param i index of the desired hit
     * @return the hit, or null if it's beyond the last hit
     */
    public synchronized T get(int i) {
        try {
            ensureResultsRead(i + 1);
        } catch (InterruptedException e) {
            // Thread was interrupted. Required hit hasn't been gathered;
            // we will just return null.
            Thread.currentThread().interrupt();
        }
        if (i >= results.size())
            return null;
        return results.get(i);
    }
    
    
//    /**
//     * Group these hits by a criterium (or several criteria).
//     *
//     * @param criteria the hit property to group on
//     * @return a HitGroups object representing the grouped hits
//     */
//    public abstract ResultGroups<T> groupedBy(ResultProperty<T> criteria);
//
//    /**
//     * Select only the hits where the specified property has the specified value.
//     * 
//     * @param property property to select on, e.g. "word left of hit"
//     * @param value value to select on, e.g. 'the'
//     * @return filtered hits
//     */
//    public abstract Results<T> filteredBy(ResultProperty<T> property, PropertyValue value);

    /**
     * Return a new Hits object with these hits sorted by the given property.
     *
     * This keeps the existing sort (or lack of one) intact and allows you to cache
     * different sorts of the same resultset. The hits themselves are reused between
     * the two Hits instances, so not too much additional memory is used.
     *
     * @param sortProp the hit property to sort on
     * @return a new Hits object with the same hits, sorted in the specified way
     */
    public <P extends ResultProperty<T>> Results<T> sortedBy(P sortProp) {
        try {
            ensureAllHitsRead();
        } catch (InterruptedException e) {
            // Thread was interrupted; abort operation
            // and let client decide what to do
            Thread.currentThread().interrupt();
        }
        return sortProp.sortResults(this);
    }

    /**
     * Get a window into this list of hits.
     *
     * Use this if you're displaying part of the resultset, like in a paging
     * interface. It makes sure BlackLab only works with the hits you want to
     * display and doesn't do any unnecessary processing on the other hits.
     *
     * HitsWindow includes methods to assist with paging, like figuring out if there
     * hits before or after the window.
     *
     * @param first first hit in the window (0-based)
     * @param windowSize size of the window
     * @return the window
     */
    public abstract Results<T> window(int first, int windowSize);
    
    @Override
    public String toString() {
        return getClass().getSimpleName() + "(#" + hitsObjId + ")";
    }

    /**
     * Ensure that we have read at least as many results as specified in the parameter.
     *
     * @param number the minimum number of results that will have been read when this
     *            method returns (unless there are fewer hits than this); if
     *            negative, reads all hits
     * @throws InterruptedException if the thread was interrupted during this
     *             operation
     */
    protected abstract void ensureResultsRead(int number) throws InterruptedException;

    /**
     * Ensure that we have read all results.
     *
     * @throws InterruptedException if the thread was interrupted during this
     *             operation
     */
    protected void ensureAllHitsRead() throws InterruptedException {
        ensureResultsRead(-1);
    }
    
    public boolean resultsProcessedAtLeast(int lowerBound) {
        try {
            // Try to fetch at least this many hits
            ensureResultsRead(lowerBound);
        } catch (InterruptedException e) {
            // Thread was interrupted; abort operation
            // and let client decide what to do
            Thread.currentThread().interrupt();
        }

        return results.size() >= lowerBound;
    }

    /**
     * This is an alias of resultsProcessedTotal().
     * 
     * @return number of hits processed total
     */
    public int size() {
        return resultsProcessedTotal();
    }

    public int resultsProcessedTotal() {
        try {
            // Probably not all hits have been seen yet. Collect them all.
            ensureAllHitsRead();
        } catch (InterruptedException e) {
            // Abort operation. Result may be wrong, but
            // interrupted results shouldn't be shown to user anyway.
            Thread.currentThread().interrupt();
        }
        return results.size();
    }

    public int resultsProcessedSoFar() {
        return results.size();
    }

    /**
     * Get the raw list of results.
     * 
     * Clients shouldn't use this. Used internally for certain performance-sensitive
     * operations like sorting.
     * 
     * The list will only contain whatever hits have been processed; if you want all the hits,
     * call ensureAllHitsRead(), size() or hitsProcessedTotal() first. 
     * 
     * @return the list of hits
     */
    public List<T> resultsList() {
        return results;
    }

    
}
