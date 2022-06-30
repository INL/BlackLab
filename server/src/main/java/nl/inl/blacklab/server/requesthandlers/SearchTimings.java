package nl.inl.blacklab.server.requesthandlers;

/**
 * Keeps track of various timings we want to report to the client.
 */
public class SearchTimings {

    /**
     * How much time each required search task originally took to produce this response.
     */
    private long searchTaskTime;

    /**
     * How long it took to count all the results.
     */
    private long countTime;

    public SearchTimings(long searchTaskTime, long countTime) {
        this.searchTaskTime = searchTaskTime;
        this.countTime = countTime;
    }

    public long getSearchTaskTime() {
        return searchTaskTime;
    }

    public long getCountTime() {
        return countTime;
    }
}
