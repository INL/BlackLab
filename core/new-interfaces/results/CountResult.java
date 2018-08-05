package nl.inl.blacklab.interfaces.results;

/** Result of a counting-only operation. */
public interface CountResult extends SearchResult {
    
    /**
     * Returns the counting result.
     * 
     * NOTE: the resulting ResultsNumber has already completed. 
     * 
     * @return result
     */
    ResultsNumber number();
    
    @Override
    default SearchResult save() {
        // This is always effectively immutable
        // (that is, count().total() will always return the same value;
        //  while still counting, count().soFar() will of course change,
        //  as will the return value of other methods relating to the 
        //  fetching process)
        return this;
    }
    
}
