package nl.inl.blacklab.searches;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.SearchResult;

/**
 * Abstract base class for all Search implementations,
 * to enforce that equals() and hashCode are implemented
 * (to ensure proper caching)
 */
public abstract class AbstractSearch implements Search {
	
    private QueryInfo queryInfo;
    
    List<SearchResultObserver> observers;
    
    public AbstractSearch(QueryInfo queryInfo, List<SearchResultObserver> customOperations) {
        this.queryInfo = queryInfo;  
        this.observers = customOperations == null ? Collections.emptyList() : customOperations;
    }
    
    public AbstractSearch(QueryInfo queryInfo) {
        this(queryInfo, null);
    }
    
    @Override
    public QueryInfo queryInfo() {
        return queryInfo;
    }

    protected <T extends SearchResult> T notifyObservers(T result) {
        for (SearchResultObserver op: observers) {
            op.perform(this, result);
        }
        return result;
    }
    
    protected List<SearchResultObserver> extraObserver(SearchResultObserver observer) {
        List<SearchResultObserver> ops = new ArrayList<>(this.observers);
        ops.add(observer);
        return ops;
    }

    /**
     * Be notified of the result of this search.
     * 
     * This is useful to allow the client to put intermediate results
     * into the cache, among other things.
     * 
     * @param observer search observer
     * @return this search
     */
    public abstract Search observe(SearchResultObserver observer);

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((queryInfo == null) ? 0 : queryInfo.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        AbstractSearch other = (AbstractSearch) obj;
        if (queryInfo == null) {
            if (other.queryInfo != null)
                return false;
        } else if (!queryInfo.equals(other.queryInfo))
            return false;
        return true;
    }
	
}
