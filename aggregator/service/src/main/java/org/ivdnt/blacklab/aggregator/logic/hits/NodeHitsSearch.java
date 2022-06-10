package org.ivdnt.blacklab.aggregator.logic.hits;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.ivdnt.blacklab.aggregator.logic.Requests;
import org.ivdnt.blacklab.aggregator.representation.DocInfo;
import org.ivdnt.blacklab.aggregator.representation.ErrorResponse;
import org.ivdnt.blacklab.aggregator.representation.Hit;
import org.ivdnt.blacklab.aggregator.representation.HitsResults;
import org.ivdnt.blacklab.aggregator.representation.SearchSummary;

import it.unimi.dsi.fastutil.BigList;
import it.unimi.dsi.fastutil.objects.ObjectBigArrayBigList;

/** Results of this search from a single node */
class NodeHitsSearch {

    /** Minimum page size to request. Very small pages cause too much request overhead. */
    private static final int PAGE_SIZE_MIN = 20;

    /**
     * Maximum page size to request. Very large pages might slow us down because we're getting
     * more than we need, or we're waiting too long for the next page to be available.
     */
    private static final int PAGE_SIZE_MAX = 300;

    /** How much bigger should each subsequent page be than the last? */
    private static final double PAGE_SIZE_GROWTH = 1.2;

    private static final long MAX_SUMMARY_AGE_MS = 600;

    /** API endpoint we're sending request to */
    private final WebTarget webTarget;

    /** Request (search) parameters */
    private final Params params;

    private final boolean useCache;

    /** Latest copy of the search summary in each response, giving us e.g. the running count. */
    private SearchSummary latestSummary;

    /** Time of the latest search summary, so we can refresh it if needed. */
    private long latestSummaryTime;

    /** The hits we've received so far. */
    private final BigList<Hit> hits = new ObjectBigArrayBigList<>();

    /** The docInfos we've received so far. */
    private final Map<String, DocInfo> docInfos = new HashMap<>();

    /** Are we still fetching, or do we have all the hits? */
    private boolean stillFetchingHits = true;

    /**
     * What size page are we requesting? We start small (for a quick initial response)
     * and grows larger (for efficiency if we're requesting many hits).
     */
    private int pageSize = PAGE_SIZE_MIN;

    /** If set, the next page has been requested and we're waiting for the response. */
    private Future<Response> nextPageRequest;

    /** Last request we sent to the server (corresponds to nextPageRequest) */
    private WebTarget nextPageTarget;

    public NodeHitsSearch(WebTarget webTarget, Params params, boolean useCache) {
        this.params = params;
        this.webTarget = webTarget;
        this.useCache = useCache;
    }

    public SearchSummary getLatestSummary() {
        return latestSummary;
    }

    /** Start a hits page request to the server. */
    private synchronized Future<Response> getNextPageRequest() {
        if (nextPageRequest == null) {
            // Request the next page.
            nextPageRequest = createRequest(hits.size64(), pageSize);
        }
        return nextPageRequest;
    }

    private Future<Response> createRequest(long first, long number) {
        nextPageTarget = Requests.optParams(webTarget.path(params.corpusName).path("hits"),
                "patt", params.patt,
                "sort", params.sort,
                "group", params.group,
                "viewgroup", params.viewGroup,
                "first", first,
                "number", number,
                "usecache", useCache);
        return nextPageTarget
                .request(MediaType.APPLICATION_JSON)
                .async()
                .get();
    }

    /** Get the next page of hits from the server. */
    private synchronized void processNextPage() {
        if (!stillFetchingHits)
            throw new IllegalArgumentException("getNextPage called but already done fetching hits");
        Response response;
        try {
            response = getNextPageRequest().get();
            nextPageRequest = null; // completed
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
        if (response.getStatus() == Status.OK.getStatusCode()) {
            // Add hits to the list
            HitsResults hitsResults = response.readEntity(HitsResults.class);
            latestSummary = hitsResults.summary;
            latestSummaryTime = System.currentTimeMillis();
            hits.addAll(hitsResults.hits);
            for (DocInfo docInfo: hitsResults.docInfos) {
                docInfos.put(docInfo.pid, docInfo);
            }
            // Make sure each hit can access its docInfo, for e.g.
            // merging results sorted by metadata field
            hitsResults.hits.forEach(h -> h.docInfo = docInfos.get(h.docPid));

            // Was this the final page of hits?
            if (!hitsResults.summary.windowHasNext) {
                // Yes, we're done.
                stillFetchingHits = false;
            } else {
                // No, start fetching the next page of hits,
                // so it will (hopefully) be available if/when we need it.
                // Make each subsequent page a little larger so we can efficiently process
                // large hit sets.
                pageSize = (int) Math.min(PAGE_SIZE_MAX, Math.round(pageSize * PAGE_SIZE_GROWTH));
                getNextPageRequest();
            }
        } else {
            // An error occurred.
            ErrorResponse error = response.readEntity(ErrorResponse.class);
            if (error.getError().getCode().equals("GROUP_NOT_FOUND")) {
                // This happens when we use viewgroup but the group doesn't occur on all nodes.
                // Interpret this as an empty result set instead.
                // (strictly speaking, we should keep track of this and if all nodes returned
                //  GROUP_NOT_FOUND, we should too, as the group actually doesn't exist)
                stillFetchingHits = false;
            } else {
                // A "real" unexpected error occurred.
                // Throw an exception that will result in an error response to the client.
                ErrorResponse newError = new ErrorResponse(error.getError());
                newError.setNodeUrl(webTarget.getUri().toString()); // keep track of what node caused the error
                Response newResponse = Response.status(response.getStatus()).entity(newError).build();
                throw new WebApplicationException(newResponse);
            }
        }
    }

    /**
     * Ensures specified hit is available if it exists.
     *
     * @param i hit index
     * @return true if the hit exists and is available, false if it doesn't exist
     */
    private synchronized boolean ensureHitAvailable(long i) {
        while (hits.size64() <= i && stillFetchingHits) {
            // Wait for another page of hits
            processNextPage();
        }
        return hits.size64() > i;
    }

    /** Get specified hit, or null if it doesn't exist. */
    private Hit hit(long i) {
        if (ensureHitAvailable(i))
            return hits.get(i);
        return null;
    }

    public CompletableFuture<SearchSummary> ensureRecentSummary() {
        long now = System.currentTimeMillis();
        if (now - latestSummaryTime > MAX_SUMMARY_AGE_MS) {
            // Summary is too old. Get a new one.
            return CompletableFuture.supplyAsync(() -> {
                try {
                    Response response = createRequest(0, 0).get();
                    HitsResults results = response.readEntity(HitsResults.class);
                    return results.summary;
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            });
        } else {
            // Summary is new enough, just return it
            return CompletableFuture.completedFuture(latestSummary);
        }

    }

    /** Called to give us a hint to determine our initial page size. */
    public void setInitialHitsRequest(long totalHitsNeeded, int numberOfNodes) {

        // Guess how many hits we might need from each node to satisfy this request.
        // In our case, twice as much as the ideal situation where each node contributes
        // exactly as many hits.
        long suggestedPageSize = totalHitsNeeded * 2 / numberOfNodes;

        // Clamp to the min and max values.
        pageSize = (int) Math.min(PAGE_SIZE_MAX, Math.max(PAGE_SIZE_MIN, suggestedPageSize));
    }

    /** Iterate through all the hits. */
    class HitIterator {
        private long index = -1;

        /**
         * Is there another hit?
         *
         * @return true if there is, false if not
         */
        public boolean hasNext() {
            try {
                return ensureHitAvailable(index + 1);
            } catch (Exception e) {
                WebTarget t = nextPageTarget == null ? webTarget : nextPageTarget;
                throw Requests.translateNodeException(t.getUri().toString(), e);
            }
        }

        /** Has next() ever been called? */
        public boolean wasNexted() {
            return index >= 0;
        }

        /**
         * Return the current hit.
         *
         * Returns null if not yet nexted (check using {@link #wasNexted()})
         * or there are no more hits.
         *
         * @return current hit
         */
        public Hit current() {
            return index < 0 ? null : hit(index);
        }

        /**
         * Go to next hit
         */
        public void next() {
            index++;
            try {
                ensureHitAvailable(index);
            } catch (Exception e) {
                WebTarget t = nextPageTarget == null ? webTarget : nextPageTarget;
                throw Requests.translateNodeException(t.getUri().toString(), e);
            }
        }

        public DocInfo currentDocInfo() {
            String pid = current().docPid;
            return docInfos.get(pid);
        }

        public long hitIndex() {
            return index;
        }
    }

    /** An iterator for our hits, without having to deal with paging. */
    public HitIterator iterator() {
        // Make sure we'll have some hits available when we need them
        if (hits.isEmpty())
            getNextPageRequest();

        return new HitIterator();
    }
}
