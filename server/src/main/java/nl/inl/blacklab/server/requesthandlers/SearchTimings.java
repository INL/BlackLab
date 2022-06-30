package nl.inl.blacklab.server.requesthandlers;

/**
 * Keeps track of various timings we want to report to the client.
 */
public class SearchTimings {

    /**
     * How much time it took to respond to this request.
     */
    private long responseTime;

    /**
     * How much time each required search task originally took to produce this response.
     */
    private long searchTaskTime;

    /**
     * How long it took to count all the results.
     */
    private long countTime;

    public SearchTimings(long responseTime, long searchTaskTime, long countTime) {
        this.responseTime = responseTime;
        this.searchTaskTime = searchTaskTime;
        this.countTime = countTime;
    }

    public static SearchTimings searchAndCount(long searchTime, long countTime) {
        return new SearchTimings(searchTime, searchTime, countTime);
    }

    public long getResponseTime() {
        return responseTime;
    }

    public long getSearchTaskTime() {
        return searchTaskTime;
    }

    public long getCountTime() {
        return countTime;
    }
}
