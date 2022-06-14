package org.ivdnt.blacklab.aggregator.logic.hits;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import javax.ws.rs.client.Client;

import org.ivdnt.blacklab.aggregator.AggregatorConfig;
import org.ivdnt.blacklab.aggregator.logic.Aggregation;
import org.ivdnt.blacklab.aggregator.logic.Requests.UseCache;
import org.ivdnt.blacklab.aggregator.logic.hits.NodeHitsSearch.HitIterator;
import org.ivdnt.blacklab.aggregator.representation.DocInfo;
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

    /** Keep cache entries for 5 minutes */
    private static final long MAX_CACHE_AGE_MS = 5 * 60 * 1000;

    /** Information about how a page of hits was assembled from the node responses,
     * so we don't have to keep everything in memory but can reconstruct part of the
     * hits when needed.
     */
    static class MergeTablePage {
        /** index of the first hit in this page */
        private final long startIndex;

        /** size of the page */
        private long size;

        /** at what index is each node's iterator at the start of this page? */
        private final long[] startIndexOnNode;

        ///** When was this page last used? () */
        //long timeLastUsed;

        /** hits in this page (or null if they need to be reconstructed) */
        private List<Hit> hits;

        /** page following this one or null if none */
        private MergeTablePage nextPage;

        public MergeTablePage(long startIndex, long size, long[] startIndexOnNode) {
            this.startIndex = startIndex;
            this.size = size;
            this.startIndexOnNode = startIndexOnNode;
            hits = new ArrayList<>();
        }

        /**
         * Add a hit to this page.
         *
         * Page size is incremented and hit is stored (if the hits on this page
         * are in memory)
         *
         * @param hit hit to add
         */
        public void add(Hit hit) {
            this.size++;
            if (this.hits != null)
                this.hits.add(hit);
        }

        /**
         * Get hit by its index in the total results.
         *
         * @param i hit index
         * @return hit
         */
        public Hit hitByGlobalIndex(long i) {
            if (i < startIndex || i >= startIndex + size)
                throw new IllegalArgumentException("Hit not on this page (not " + startIndex + " <= " + i + " < " + startIndex + size + ")");
            if (hits == null)
                throw new IllegalArgumentException("Hits for this page are not available (page " + startIndex + "-" + startIndex + size + ", hit " + i + ")");
            return hits.get((int)(i - startIndex));
        }
    }

    /** Search cache */
    private final static Map<Params, HitsSearch> cache = new HashMap<>();

    /** Get search object for specified parameters.
     *
     * Also makes sure old searches are removed from cache.
     */
    public static HitsSearch get(Client client, String corpusName, String patt, String filter, String sort,
            String group, String viewGroup, UseCache useCache, long initialNumberOfHits) {
        Comparator<Hit> comparator = HitComparators.deserialize(sort);
        Params params = new Params(corpusName, patt, filter, sort, group, viewGroup);
        synchronized (cache) {
            if (!useCache.onAggregator())
                cache.clear();
            HitsSearch search = cache.computeIfAbsent(params, __ -> new HitsSearch(client, params, comparator, initialNumberOfHits, useCache.onNodes()));
            search.updateLastAccessTime();
            if (useCache.onAggregator())
                removeOldSearches();
            return search;
        }
    }

    private static void removeOldSearches() {
        long when = System.currentTimeMillis() - MAX_CACHE_AGE_MS;
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

    /**
     * Our hits, separated into pages (at a document boundary).
     * We keep track of the start and size of each page, and the start index
     * on each node, so we can unload the actual hits and reconstruct them later.
     */
    List<MergeTablePage> mergeTable = new ArrayList<>();

    /** Where are we currently adding hits (always points to the last page) */
    MergeTablePage mergeTableCurrentPage;

    /** Relevant docInfos */
    private final Map<String, DocInfo> docInfos = new HashMap<>();

    /** Our sort */
    private final Comparator<Hit> comparator;

    private HitsSearch(Client client, Params params, Comparator<Hit> comparator, long initialNumberOfHits,
            boolean useCache) {
        this.comparator = comparator;

        // Create our node searches and keep track of iterators
        nodeSearches = new ArrayList<>();
        nodeSearchIterators = new ArrayList<>();
        int numberOfNodes = AggregatorConfig.get().getNodes().size();
        for (String nodeUrl: AggregatorConfig.get().getNodes()) {
            NodeHitsSearch search = new NodeHitsSearch(client.target(nodeUrl), params, useCache);
            search.setInitialHitsRequest(initialNumberOfHits, numberOfNodes); // hint for page size
            nodeSearches.add(search);
            nodeSearchIterators.add(search.iterator());
        }
        updateLastAccessTime();

        // Initialize merge table
        long[] startIndexOnNode = new long[nodeSearches.size()];
        Arrays.fill(startIndexOnNode, 0);
        mergeTableCurrentPage = new MergeTablePage(0, 0, startIndexOnNode);
        mergeTable.add(mergeTableCurrentPage);
    }

    private void updateLastAccessTime() {
        lastAccessTime = System.currentTimeMillis();
    }

    private long getLastAccessTime() {
        return lastAccessTime;
    }

    private long size() {
        return mergeTableCurrentPage.startIndex + mergeTableCurrentPage.size;
    }

    private MergeTablePage pageContaining(long hitIndex) {
        if (hitIndex < 0 || hitIndex >= size())
            throw new IllegalArgumentException("Hit index out of range: " + hitIndex);
        // Use binary search to find page
        int min = 0, max = mergeTable.size() - 1, index;
        while (min <= max) {
            index = (min + max) / 2;
            MergeTablePage page = mergeTable.get(index);
            if (page.startIndex > hitIndex) {
                max = index - 1;
            } else if (page.startIndex + page.size <= hitIndex){
                min = index + 1;
            } else {
                return page;
            }
        }
        throw new IllegalArgumentException("Error, min > max: " + min + " > " + max);
    }

    private Hit get(long i) {
        MergeTablePage page = pageContaining(i);
        return page.hitByGlobalIndex(i);
    }

    /**
     * Ensure the requested number of hits is available.
     *
     * May not actually read this number if no more hits are available; always check.
     *
     * @param l number of hits we need
     */
    private synchronized void ensureResultsRead(long l) {
        // TODO: maybe don't synchronize the whole method, or a request for 10 hits might be
        //    waiting for another request that wants a million hits...

        String previousHitDoc = size() == 0 ? "--NONE--" : get(size() - 1).docPid;
        while (size() <= l) {
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
            // so the merge is reproducible and we hit each node roughly equally.
            if (comparator == null && smallestHitSource == null) {
                long lowestIndex = Long.MAX_VALUE;
                for (HitIterator it: nodeSearchIterators) {
                    long index = it.hitIndex();
                    if (index >= 0 && index < lowestIndex && it.current() != null) {
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

            // Get the smallest hit
            Hit nextHit = smallestHitSource.current();

            // Update merge table
            if (mergeTableCurrentPage.size >= 1000 && !previousHitDoc.equals(nextHit.docPid)) {
                // Page has gotten large and we're at a document boundary.
                // Add a new page.
                MergeTablePage newPage = new MergeTablePage(size(), 0, currentNodeIndexes());
                mergeTableCurrentPage.nextPage = newPage;
                mergeTable.add(newPage);
                mergeTableCurrentPage = newPage;
            }

            // Add hit to current page
            mergeTableCurrentPage.add(nextHit);

            // Keep track of the last hit's document
            previousHitDoc = nextHit.docPid;

            // Make sure we have the docInfo for this doc
            var it = smallestHitSource;
            docInfos.computeIfAbsent(nextHit.docPid, pid -> it.currentDocInfo());

            // Advance iterator to the next unused hit (or null if no more hits)
            smallestHitSource.next();
        }
    }

    private long[] currentNodeIndexes() {
        long[] result = new long[nodeSearchIterators.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = nodeSearchIterators.get(i).hitIndex();
        }
        return result;
    }

    public HitsResults window(long first, long number) {
        if (first < 0 || number < 0)
            throw new RuntimeException("Illegal values for window");

        // Make sure we have all the hits we need available
        ensureResultsRead(first + number + 1); // + 1 to determine windowHasNext (see below)

        // Did we run out of hits, or is this a full window?
        long actualWindowSize = size() >= first + number ? number : size() - first;
        if (actualWindowSize < 0)
            throw new RuntimeException("Cannot get negative window size");

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
        summary.windowHasNext = size() > first + number;
        summary.windowHasPrevious = first > 0;

        // Build the hits window and docInfos and return
        BigList<Hit> hitWindow = subList(first, actualWindowSize);
        List<DocInfo> relevantDocs = hitWindow.stream()
                .map(h -> h.docPid)
                .collect(Collectors.toSet()).stream()
                .map(docInfos::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        return new HitsResults(summary, hitWindow, relevantDocs);
    }

    private BigList<Hit> subList(long first, long number) {
        // WAS: return hits.subList(first, first + number);
        if (first < 0 || first + number > size())
            throw new IndexOutOfBoundsException("Asked for more hits than were available (" + first + ", " + number + " / " + size() + ")");
        BigList<Hit> result = new ObjectBigArrayBigList<>();

        // Find the first page to take hits from and determine start index
        MergeTablePage page = pageContaining(first);
        long indexInPage = first - page.startIndex;
        long needHits = number;
        while (result.size64() < number && page != null) {
            // How many to take from this page?
            long available = page.startIndex + page.size - indexInPage;
            long numberFromThisPage = Math.min(needHits, available);

            // Take the hits and update admin
            result.addAll(page.hits.subList((int)indexInPage, (int)(indexInPage + numberFromThisPage)));
            indexInPage = 0;
            needHits -= numberFromThisPage;

            // Go to next page
            page = page.nextPage;
        }
        if (result.size64() < number)
            throw new IndexOutOfBoundsException("Internal error! (" + result.size64() + " < " + number + ")");
        return result;
    }
}
