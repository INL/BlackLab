package nl.inl.blacklab.interfaces.results;

/**
 * Base interface for all possible BlackLab results.
 *
 * Returned by SearchOperation.
 */
public interface SearchResult {
	
    /**
     * Get (effectively) immutable version of this result that can be saved for later.
     * 
     * Effectively immutable here means that e.g. a hits result
     * might not have fetched all the hits yet, so the size().soFar()
     * and other ResultNumber methods related to the fetching process,
     * but the hits themselves, or size().total() don't change. 
     * 
     * Already-immutable results will simply return themselves.
     * 
     * @return an (effectively) immutable version of the result
     */
    SearchResult save();
    
}
