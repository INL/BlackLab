package nl.inl.blacklab.search.results;

public interface DocOrHitGroups {
    /**
     * Get the total number of results that were grouped
     *
     * @return the number of results that were grouped
     */
    int getTotalResults();

    /**
     * Return the size of the largest group
     *
     * @return size of the largest group
     */
    int getLargestGroupSize();

    /**
     * Return the number of groups
     *
     * @return number of groups
     */
    int numberOfGroups();

}
