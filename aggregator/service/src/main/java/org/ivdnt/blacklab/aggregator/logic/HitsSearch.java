package org.ivdnt.blacklab.aggregator.logic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.ivdnt.blacklab.aggregator.AggregatorConfig;
import org.ivdnt.blacklab.aggregator.logic.HitsSearch.NodeHitsSearch.HitIterator;
import org.ivdnt.blacklab.aggregator.representation.DocInfo;
import org.ivdnt.blacklab.aggregator.representation.ErrorResponse;
import org.ivdnt.blacklab.aggregator.representation.Hit;
import org.ivdnt.blacklab.aggregator.representation.HitsResults;
import org.ivdnt.blacklab.aggregator.representation.SearchSummary;

import it.unimi.dsi.fastutil.BigList;
import it.unimi.dsi.fastutil.objects.ObjectBigArrayBigList;

/** A distributed hits search.
 *
 * Keeps track of results from the nodes and maintains a merged hits list.
 */
public class HitsSearch {

    /** Use our caching mechanism or not? */
    private static final boolean USE_CACHE = false;

    /** Keep cache entries for 5 minutes */
    private static final long MAX_CACHE_AGE_MS = 5 * 60 * 1000;

    /** Search parameters */
    private static class Params {
        final String corpusName;
        final String patt;
        final String sort;
        final String group;
        final String viewGroup;

        public Params(String corpusName, String patt, String sort, String group, String viewGroup) {
            this.corpusName = corpusName;
            this.patt = patt;
            this.sort = sort;
            this.group = group;
            this.viewGroup = viewGroup;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            Params params = (Params) o;
            return Objects.equals(corpusName, params.corpusName) && Objects.equals(patt, params.patt)
                    && Objects.equals(sort, params.sort) && Objects.equals(group, params.group)
                    && Objects.equals(viewGroup, params.viewGroup);
        }

        @Override
        public int hashCode() {
            return Objects.hash(corpusName, patt, sort, group, viewGroup);
        }
    }

    /** Results of this search from a single node */
    static class NodeHitsSearch {

        private static final int PAGE_SIZE = 100;

        private static final long MAX_SUMMARY_AGE_MS = 600;

        private final WebTarget webTarget;

        private final Params params;

        private SearchSummary latestSummary;

        private long latestSummaryTime;

        private final BigList<Hit> hits = new ObjectBigArrayBigList<>();

        private final Map<String, DocInfo> docInfos = new HashMap<>();

        private boolean stillFetchingHits = true;

        /** If set, the next page has been requested and we're waiting for the response. */
        private Future<Response> nextPageRequest;

        public NodeHitsSearch(WebTarget webTarget, Params params) {
            this.params = params;
            this.webTarget = webTarget;
        }

        public SearchSummary getLatestSummary() {
            return latestSummary;
        }

        /** Start a hits page request to the server. */
        private synchronized Future<Response> getNextPageRequest() {
            if (nextPageRequest == null) {
                // Request the next page
                nextPageRequest = createRequest(hits.size64(), PAGE_SIZE);
            }
            return nextPageRequest;
        }

        private Future<Response> createRequest(long first, long number) {
            return webTarget
                    .path(params.corpusName)
                    .path("hits")
                    .queryParam("patt", params.patt)
                    .queryParam("sort", params.sort)
                    .queryParam("group", params.group)
                    .queryParam("viewgroup", params.viewGroup)
                    .queryParam("first", first)
                    .queryParam("number", number)
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
            } catch (InterruptedException|ExecutionException e) {
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
                // Make sure each hit can access its docInfo, for e.g. sort by metadata field
                hitsResults.hits.forEach(h -> h.docInfo = docInfos.get(h.docPid));

                // Was this the final page of hits?
                if (!hitsResults.summary.windowHasNext) {
                    // Yes, we're done.
                    stillFetchingHits = false;
                } else {
                    // No, start fetching the next page of hits,
                    // so it will hopefully be available when we need it.
                    getNextPageRequest();
                }
            } else {
                ErrorResponse error = response.readEntity(ErrorResponse.class);
                if (error.getError().getCode().equals("GROUP_NOT_FOUND")) {
                    // This happens when we use viewgroup but the group doesn't occur on all nodes.
                    // Interpret this as an empty result set instead.
                    stillFetchingHits = false;
                } else {
                    // A "real" unexpected error occurred.
                    ErrorResponse newError = new ErrorResponse(error.getError());
                    newError.setNodeUrl(webTarget.getUri().toString());
                    Response newResponse = Response.status(response.getStatus()).entity(newError).build();
                    throw new WebApplicationException(newResponse);
                }
            }
        }

        /** Ensures specified hit is available if it exists.
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
                return CompletableFuture.supplyAsync( () -> {
                    try {
                        Response response = createRequest(0, 0).get();
                        HitsResults results = response.readEntity(HitsResults.class);
                        return results.summary;
                    } catch (InterruptedException|ExecutionException e) {
                        throw new RuntimeException(e);
                    }
                });
            } else {
                // Summary is new enough, just return it
                return CompletableFuture.completedFuture(latestSummary);
            }

        }

        /** Iterate through all the hits. */
        class HitIterator {
            private long index = -1;

            /**
             * Is there another hit?
             * @return true if there is, false if not
             */
            public boolean hasNext() {
                return ensureHitAvailable(index + 1);
            }

            /** Has next() ever been called? */
            public boolean wasNexted() {
                return index >= 0;
            }

            /** Return the current hit.
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
             * Return next hit
             *
             * @return next hit, or null if no more hits.
             */
            public Hit next() {
                index++;
                return hit(index);
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

    /** Search cache */
    private final static Map<Params, HitsSearch> cache = new HashMap<>();

    /** Get search object for specified parameters.
     *
     * Also makes sure old searches are removed from cache.
     */
    public static HitsSearch get(Client client, String corpusName, String patt, String sort,
            String group, String viewGroup) {
        Comparator<Hit> comparator = HitComparators.deserialize(sort);
        Params params = new Params(corpusName, patt, sort, group, viewGroup);
        synchronized (cache) {
            HitsSearch search = cache.computeIfAbsent(params, __ -> new HitsSearch(client, params, comparator));
            search.updateLastAccessTime();
            if (USE_CACHE)
                removeOldSearches(MAX_CACHE_AGE_MS);
            else
                cache.clear();
            return search;
        }
    }

    public static void removeOldSearches(long maxAge) {
        long when = System.currentTimeMillis() - maxAge;
        List<Params> toRemove = cache.entrySet().stream()
                .filter(e -> e.getValue().getLastAccessTime() < when)
                .map(Entry::getKey)
                .collect(Collectors.toList());
        toRemove.forEach(cache::remove);
    }

    /** When was the search last accessed by a client? Used to remove it from cache. */
    private long lastAccessTime;

    /** This search on each of our nodes. */
    private final List<NodeHitsSearch> nodeSearches;

    /** Iterators on all the node's results.
     *
     * These always point to the current not-yet-consumed hit,
     * unless {@link HitIterator#wasNexted()} returns false, in which
     * case next() should be called to position it on the first hit.
     * Otherwise, if {@link HitIterator#current()} returns null, this node has no more hits.
     */
    private final List<HitIterator> nodeSearchIterators;

    /** Merged hits results */
    private final BigList<Hit> hits = new ObjectBigArrayBigList<>();

    /** Our sort */
    private final Comparator<Hit> comparator;

    /** Relevant docInfos */
    private final Map<String, DocInfo> docInfos = new HashMap<>();

    public HitsSearch(Client client, Params params, Comparator<Hit> comparator) {
        this.comparator = comparator;
        nodeSearches = new ArrayList<>();
        nodeSearchIterators = new ArrayList<>();
        for (String nodeUrl: AggregatorConfig.get().getNodes()) {
            NodeHitsSearch search = new NodeHitsSearch(client.target(nodeUrl), params);
            nodeSearches.add(search);
            nodeSearchIterators.add(search.iterator());
        }
        updateLastAccessTime();
    }

    private void updateLastAccessTime() {
        lastAccessTime = System.currentTimeMillis();
    }

    public long getLastAccessTime() {
        return lastAccessTime;
    }

    /**
     * Ensure the requested number of hits is available.
     *
     * @param l number of hits we need
     * @return true if available, false if no more hits
     */
    private synchronized boolean ensureResultsRead(long l) {
        // TODO: maybe don't synchronize the whole method, or a request for 10 hits might be
        //    waiting for another request that wants a million hits...
        String previousHitDoc = hits.isEmpty() ? "--NONE--" : hits.get(hits.size64() - 1).docPid;
        while (hits.size64() <= l) {
            // - check each node's current hit
            // - select the 'smallest' one (according to our sort)
            //   (or if we don't have sort: select hit from same doc as previous,
            //    sp we keep hits from one document together. If there's no such hit,
            //    we choose the "most lagging node", see below)
            // - add it to our hits
            // - advance that smallest node to the next hit (if available)

            // Find the "smallest" hit from all the nodes' unconsumed hits
            // (smallest = first occurring with current sort)
            HitIterator smallestHitSource = null;
            for (HitIterator it: nodeSearchIterators) {
                if (!it.wasNexted()) {
                    // Position iterator on the first hit
                    if (!it.hasNext())
                        continue;
                    it.next();
                }
                Hit hit = it.current();
                if (hit == null)
                    continue; // no more hits from this node

                if (comparator == null) {
                    // No sort specified. Check if this hit is in the same doc as the previous
                    if (previousHitDoc.equals(hit.docPid)) {
                        smallestHitSource = it;
                    }
                } else {
                    // Sort specified, check if this hit is smaller than the one found so far
                    if (smallestHitSource == null || comparator.compare(hit, smallestHitSource.current()) < 0) {
                        smallestHitSource = it;
                    }
                }
            }

            // If there's no sort, and no hit from the same doc as the previous hit:
            // Find which node is at the lowest hit index, and return next hit from that,
            // so we hit each node roughly equally.
            if (comparator == null && smallestHitSource == null) {
                long lowestIndex = Long.MAX_VALUE;
                for (HitIterator it: nodeSearchIterators) {
                    long index = it.hitIndex();
                    if (index < lowestIndex) {
                        lowestIndex = index;
                        smallestHitSource = it;
                    }
                }
            }

            // Are we done?
            if (smallestHitSource == null) {
                // Yes, no hits left on any node.
                // (nodes may still be counting if the hit retrieval limit was reached)
                break;
            }

            // Add smallest hit to our list
            Hit nextHit = smallestHitSource.current();
            hits.add(nextHit);
            previousHitDoc = nextHit.docPid;

            // Make sure we have the docInfo for this doc
            var it = smallestHitSource;
            docInfos.computeIfAbsent(nextHit.docPid, pid -> it.currentDocInfo());

            // Advance iterator to the next unused hit (or null if no more hits)
            smallestHitSource.next();
        }
        return hits.size64() > l;
    }

    public HitsResults window(long first, long number) {
        // Make sure we have all the hits we need available
        ensureResultsRead(first + number + 1); // + 1 to determine windowHasNext (see below)

        // Did we run out of hits, or is this a full window?
        long actualWindowSize = hits.size64() >= first + number ? number : hits.size64() - first;

        if (number == 0) {
            // If we requested 0 hits, we care about the running total; ensure it's up to date
            CompletableFuture<?>[] futures = nodeSearches.stream()
                    .map(NodeHitsSearch::ensureRecentSummary)
                    .toArray(CompletableFuture[]::new);
            try {
                CompletableFuture.allOf(futures).get();
            } catch (InterruptedException|ExecutionException e) {
                throw new RuntimeException(e);
            }
        }

        // Determine summary
        SearchSummary summary = nodeSearches.stream()
                .map(NodeHitsSearch::getLatestSummary)
                .filter(Objects::nonNull)
                .reduce(Aggregation::mergeSearchSummary)
                .orElseThrow();
        summary.requestedWindowSize = number;
        summary.actualWindowSize = actualWindowSize;
        summary.windowFirstResult = first;
        summary.windowHasNext = hits.size64() > first + number;
        summary.windowHasPrevious = first > 0;

        // Build the hits window and docInfos and return
        BigList<Hit> hitWindow = hits.size64() >= first + number ? hits.subList((int)first, (int)(first + number)) : hits;
        List<DocInfo> relevantDocs = hitWindow.stream()
                .map(h -> h.docPid)
                .collect(Collectors.toSet()).stream()
                .map(docInfos::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        return new HitsResults(summary, hitWindow, relevantDocs);
    }

}
