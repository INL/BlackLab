package nl.inl.blacklab.searches;

import nl.inl.blacklab.search.results.SearchResult;

/**
 * Abstract base class for all Search implementations,
 * to enforce that equals() and hashCode are implemented
 * (to ensure proper caching)
 * 
 * @param <T> type of SearchResult this search will yield, e.g. Hits
 */
public abstract class AbstractSearch<T extends SearchResult> implements Search<T> {
	
    @Override
    public abstract T execute();
	
    @Override
    public abstract Search<T> custom(SearchOperation<T> receiver);

    @Override
    public abstract boolean equals(Object obj);

    @Override
    public abstract int hashCode();
	
}
