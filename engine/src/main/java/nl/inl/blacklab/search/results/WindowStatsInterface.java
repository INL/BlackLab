package nl.inl.blacklab.search.results;

/**
 * Information about a results window.
 * 
 * For example: start and (requested/actual) size of the window 
 * and whether there are next/previous windows.
 */
public interface WindowStatsInterface {
    /**
     * Are there more results in the original Hits object beyond our window?
     *
     * @return true if there are, false if not.
     */
    boolean hasNext();

    /**
     * Are there more results in the original Hits object "to the left" of our
     * window?
     *
     * @return true if there are, false if not.
     */
    boolean hasPrevious();

    /**
     * Where would the next window start?
     *
     * @return index of the first result beyond our window
     */
    int nextFrom();

    /**
     * Where would the previous window start?
     *
     * @return index of the start result for the previous page
     */
    int prevFrom();

    /**
     * What's the first in the window?
     *
     * @return index of the first result
     */
    int first();

    /**
     * What's the last in the window?
     *
     * @return index of the last result
     */
    int last();

    /**
     * How many results are in this window?
     *
     * Note that this may be different from the specified "window size", as the
     * window may not be full.
     *
     * @return number of results
     */
    int windowSize();

    /**
     * How many results per page did we request?
     *
     * @return number of results per page requested
     */
    int requestedWindowSize();

}
