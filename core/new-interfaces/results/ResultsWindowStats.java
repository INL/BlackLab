package nl.inl.blacklab.interfaces.results;

/**
 * Information about a results window.
 * 
 * This tells us what range of e.g. hits we're viewing, and if there's a next and previous page.
 */
interface ResultsWindowStats {
	/**
	 * Are there more hits in the original Hits object beyond our window?
	 *
	 * @return true if there are, false if not.
	 */
	boolean hasNext();

	/**
	 * Are there more hits in the original Hits object "to the left" of our window?
	 *
	 * @return true if there are, false if not.
	 */
	boolean hasPrevious();

	/**
	 * Where would the next window start?
	 *
	 * @return index of the first hit beyond our window
	 */
	int nextFrom();

	/**
	 * Where would the previous window start?
	 *
	 * @return index of the start hit for the previous page
	 */
	int prevFrom();

	/**
	 * What's the first in the window?
	 *
	 * @return index of the first hit
	 */
	int first();

	/**
	 * What's the last in the window?
	 *
	 * @return index of the last hit
	 */
	int last();

	/**
	 * How many hits are in this window?
	 *
	 * Note that this may be different from the specified "window size",
	 * as the window may not be full.
	 *
	 * @return number of hits
	 */
	int size();

    /**
     * How many results per page did we request?
     *
     * @return number of results per page requested
     */
    int requestedSize();

    /**
     * How many hits are available in the original source Hits object?
     *
     * @return number of hits in the source
     */
	ResultsStats source();
}
