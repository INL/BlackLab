package nl.inl.blacklab.server.search;

import java.util.function.Supplier;

import nl.inl.blacklab.search.results.SearchResult;
import nl.inl.blacklab.searches.FutureSearchResult;
import nl.inl.blacklab.searches.Search;

class NewBlsCacheEntry<T extends SearchResult> extends FutureSearchResult<T> {
    
    /** id for the next job started */
    private static Long nextEntryId = 0L;
    
    public static long getNextEntryId() {
        Long n;
        synchronized(nextEntryId) {
            n = nextEntryId;
            nextEntryId++;
        }
        return n;
    }

    /** Unique entry id */
    long id = getNextEntryId();
    
    /** Our search */
    private Search search;

    public NewBlsCacheEntry(Search search, Supplier<T> supplier) {
        super(supplier);
        this.search = search;
    }
    
    public long id() {
        return id;
    }

    public Search search() {
        return search;
    }

}