package nl.inl.blacklab.interfaces.search;

import nl.inl.blacklab.interfaces.results.SearchResult;

/**
 * Abstract base class for all Search implementations,
 * to enforce that equals() and hashCode are implemented
 * (to ensure proper caching)
 */
public abstract class AbstractSearch implements Search {
	
    @Override
    public abstract SearchResult execute();
	
    @Override
    public abstract Search custom(SearchOperation receiver);

    @Override
    public abstract boolean equals(Object obj);

    @Override
    public abstract int hashCode();
	
}
