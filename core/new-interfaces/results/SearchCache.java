package nl.inl.blacklab.interfaces.results;

import nl.inl.blacklab.interfaces.search.Search;

/**
 * An interface for a search cache that the client will manage,
 * and BlackLab can use to only perform new searches when the old
 * search is not cached. 
 */
public interface SearchCache {
    SearchResult get(Search operation);
}