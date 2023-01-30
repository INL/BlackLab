package org.ivdnt.blacklab.proxy.logic.hits;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.ivdnt.blacklab.proxy.logic.Requests;
import org.ivdnt.blacklab.proxy.representation.DocInfo;
import org.ivdnt.blacklab.proxy.representation.ErrorResponse;
import org.ivdnt.blacklab.proxy.representation.HitMin;
import org.ivdnt.blacklab.proxy.representation.HitsResults;
import org.ivdnt.blacklab.proxy.representation.HitsResultsMinimal;
import org.ivdnt.blacklab.proxy.representation.SearchSummary;

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
    private static final int PAGE_SIZE_MAX = 200000;

    /** How much bigger should each subsequent page be than the last? */
    private static final double PAGE_SIZE_GROWTH = 1.2;

    private static final long MAX_SUMMARY_AGE_MS = 600;

    /** API endpoint we're sending request to */
    private final WebTarget webTarget;

    /** Our node id */
    private final int nodeId;

    /** Request (search) parameters */
    private final Params params;

    private final boolean useCache;

    /** Latest copy of the search summary in each response, giving us e.g. the running count. */
    private SearchSummary latestSummary;

    /** Time of the latest search summary, so we can refresh it if needed. */
    private long latestSummaryTime;

    /** The hits we've received so far */
    private final BigList<HitMin> hits = new ObjectBigArrayBigList<>();

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

    public NodeHitsSearch(int nodeId, WebTarget webTarget, Params params, boolean useCache) {
        this.nodeId = nodeId;
        this.params = params;
        this.webTarget = webTarget;
        this.useCache = useCache;
    }

    public int getNodeId() {
        return nodeId;
    }

    public SearchSummary getLatestSummary() {
        return latestSummary;
    }

    /** Start a hits page request to the server. */
    private synchronized Future<Response> getNextPageRequest() {
        if (nextPageRequest == null) {
            // Request the next page.
            nextPageRequest = createRequest(hits.size64(), pageSize, true);
        }
        return nextPageRequest;
    }

    private Future<Response> createRequest(long first, long number, boolean minimal) {
        nextPageTarget = Requests.optParams(webTarget.path(params.corpusName).path("hits"),
                "patt", params.patt,
                "sort", params.sort,
                "filter", params.filter,
                "group", params.group,
                "viewgroup", params.viewGroup,
                "first", first,
                "number", number,
                "usecache", useCache,
                "aggregator", minimal);
        return nextPageTarget
                .request(MediaType.APPLICATION_JSON)
                .async()
                .get();
    }

    void processResponse(Response response) {
        // Add hits to the list
        HitsResultsMinimal hitsResults = response.readEntity(HitsResultsMinimal.class);
        latestSummary = hitsResults.summary;
        latestSummaryTime = System.currentTimeMillis();
        long indexOnNode = hitsResults.summary.windowFirstResult;
        for (List<Object> hit: hitsResults.hits) {
            int docIdOnNode = (int)hit.get(0);
            String[] sortValue = null;
            if (hit.size() > 1) {
                List<Object> sv = (List<Object>) hit.get(1);
                sortValue = sv.stream().map(o -> (String) o).toArray(String[]::new);
            }
            hits.add(new HitMin(nodeId, indexOnNode, docIdOnNode, sortValue));
            indexOnNode++;
        }
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
            processResponse(response);
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
    private HitMin hit(long i) {
        if (ensureHitAvailable(i))
            return hits.get(i);
        return null;
    }

    public CompletableFuture<HitsResults> getFullHits(long first, long  number) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Response response = createRequest(first, number, false).get();
                if (response.getStatus() == Status.OK.getStatusCode()) {
                    HitsResults results = response.readEntity(HitsResults.class);
                    Map<String, DocInfo> docInfos = new HashMap<>();
                    for (DocInfo docInfo: results.docInfos) {
                        docInfos.put(docInfo.pid, docInfo);
                    }
                    results.hits.forEach(h -> h.docInfo = docInfos.get(h.docPid));
                    return results;
                } else {
                    ErrorResponse error = response.readEntity(ErrorResponse.class);
                    ErrorResponse newError = new ErrorResponse(error.getError());
                    newError.setNodeUrl(webTarget.getUri().toString()); // keep track of what node caused the error
                    Response newResponse = Response.status(response.getStatus()).entity(newError).build();
                    throw new WebApplicationException(newResponse);
                }
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<SearchSummary> ensureRecentSummary() {
        long now = System.currentTimeMillis();
        if (now - latestSummaryTime > MAX_SUMMARY_AGE_MS) {
            // Summary is too old. Get a new one.
            return CompletableFuture.supplyAsync(() -> {
                try {
                    Response response = createRequest(0, 0, true).get();
                    HitsResults results = response.readEntity(HitsResults.class);
                    latestSummary = results.summary;
                    latestSummaryTime = System.currentTimeMillis();
                    return latestSummary;
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
        public HitMin current() {
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
