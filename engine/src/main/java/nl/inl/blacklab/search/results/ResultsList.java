package nl.inl.blacklab.search.results;

import java.util.Iterator;
import java.util.NoSuchElementException;

import it.unimi.dsi.fastutil.BigList;
import it.unimi.dsi.fastutil.objects.ObjectBigArrayBigList;
import nl.inl.blacklab.resultproperty.ResultProperty;

public abstract class ResultsList<T, P extends ResultProperty<T>> extends Results<T, P> {

    /**
     * The results.
     */
    protected BigList<T> results;
    
    public ResultsList(QueryInfo queryInfo) {
        super(queryInfo);
        results = new ObjectBigArrayBigList<>();
    }
    
    /**
     * Return an iterator over these hits.
     *
     * @return the iterator
     */
    @Override
    public Iterator<T> iterator() {
        // Construct a custom iterator that iterates over the hits in the hits
        // list, but can also take into account the Spans object that may not have
        // been fully read. This ensures we don't instantiate Hit objects for all hits
        // if we just want to display the first few.
        return new Iterator<>() {
        
            int index = -1;
        
            @Override
            public boolean hasNext() {
                // Do we still have hits in the hits list?
                ensureResultsRead(index + 2);
                return results.size64() >= index + 2;
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
    
    @Override
    public synchronized T get(long i) {
        ensureResultsRead(i + 1);
        if (i >= results.size64())
            return null;
        return results.get((int)i);
    }
    
    @Override
    protected boolean resultsProcessedAtLeast(long lowerBound) {
        ensureResultsRead(lowerBound);
        return results.size64() >= lowerBound;
    }
    
    @Override
    protected long resultsProcessedTotal() {
        ensureAllResultsRead();
        return results.size64();
    }
    
    @Override
    protected long resultsProcessedSoFar() {
        return results.size64();
    }
    
    
    /**
     * Get part of the list of results.
     * 
     * Clients shouldn't use this. Used internally for certain performance-sensitive
     * operations like sorting.
     * 
     * The returned list is a view backed by the results list.
     * 
     * If toIndex is out of range, no exception is thrown, but a smaller list is returned.
     * 
     * @return the list of hits
     */
    protected BigList<T> resultsSubList(long fromIndex, long toIndex) {
        ensureResultsRead(toIndex);
        if (toIndex > results.size64())
            toIndex = results.size64();
        return results.subList(fromIndex, toIndex);
    }
}
