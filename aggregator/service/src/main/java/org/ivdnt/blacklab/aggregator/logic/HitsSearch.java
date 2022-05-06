package org.ivdnt.blacklab.aggregator.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.ws.rs.client.Client;

import org.ivdnt.blacklab.aggregator.AggregatorConfig;
import org.ivdnt.blacklab.aggregator.representation.DocInfo;
import org.ivdnt.blacklab.aggregator.representation.Hit;
import org.ivdnt.blacklab.aggregator.representation.HitsResults;
import org.ivdnt.blacklab.aggregator.representation.SearchSummary;

import it.unimi.dsi.fastutil.BigList;
import it.unimi.dsi.fastutil.objects.ObjectBigArrayBigList;

/** A distributed hits search. */
public class HitsSearch {

    /** Keep cache entries for 5 minutes */
    private static final long MAX_CACHE_AGE_MS = 5 * 60 * 1000;

    /** Search parameters */
    private static class Params {
        Client client;
        String corpusName;
        String cqlPattern;
        String sort;

        public Params(Client client, String corpusName, String cqlPattern, String sort) {
            this.client = client;
            this.corpusName = corpusName;
            this.cqlPattern = cqlPattern;
            this.sort = sort;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            Params params = (Params) o;
            return Objects.equals(client, params.client) && Objects.equals(corpusName, params.corpusName)
                    && Objects.equals(cqlPattern, params.cqlPattern) && Objects.equals(sort, params.sort);
        }

        @Override
        public int hashCode() {
            return Objects.hash(client, corpusName, cqlPattern, sort);
        }
    }

    /** Results of this search from a single node */
    private class NodeHitsSearch implements Iterable<Hit> {

        private final String nodeUrl;

        private SearchSummary latestSummary;

        private BigList<Hit> hits = new ObjectBigArrayBigList<>();

        private Map<String, DocInfo> docInfos = new HashMap<>();

        public NodeHitsSearch(String nodeUrl, Params params) {
            this.nodeUrl = nodeUrl;
        }

        public SearchSummary getLatestSummary() {
            return this.latestSummary;
        }

        @Override
        public Iterator<Hit> iterator() {
            // TODO implement
        }
    }

    /** Search cache */
    private final static Map<Params, HitsSearch> cache = new HashMap<>();

    /** Get search object for specified parameters.
     *
     * Also makes sure old searches are removed from cache.
     */
    public static HitsSearch get(Client client, String corpusName, String cqlPattern, String sort) {
        Params params = new Params(client, corpusName, cqlPattern, sort);
        synchronized (cache) {
            HitsSearch search = cache.computeIfAbsent(params, __ -> new HitsSearch(params));
            search.updateLastAccessTime();
            removeOldSearches(MAX_CACHE_AGE_MS);
            return search;
        }
    }

    public static void removeOldSearches(long maxAge) {
        long when = System.currentTimeMillis() - maxAge;
        List<Params> toRemove = cache.entrySet().stream()
                .filter(e -> e.getValue().getLastAccessTime() < when)
                .map(e -> e.getKey())
                .collect(Collectors.toList());
        toRemove.forEach(pid -> cache.remove(pid));
    }

    private final Params params;

    private long lastAccessTime;

    private final List<NodeHitsSearch> nodeSearches;

    /** Merged hits results */
    private final BigList<Hit> hits = new ObjectBigArrayBigList<>();

    /** Relevant docInfos */
    private final Map<String, DocInfo> docInfos = new HashMap<>();

    public HitsSearch(Params params) {
        this.params = params;
        nodeSearches = new ArrayList<>();
        for (String nodeUrl: AggregatorConfig.get().getNodes()) {
            nodeSearches.add(new NodeHitsSearch(nodeUrl, params));
        }
        updateLastAccessTime();
    }

    private void updateLastAccessTime() {
        lastAccessTime = System.currentTimeMillis();
    }

    public long getLastAccessTime() {
        return lastAccessTime;
    }

    private boolean ensureResultsRead(long l) {
        // TODO implement:
        // - check each node's current hit
        // - select the 'smallest' one (according to our sort)
        // - add it to our hits
        // - advance that node to the next hit (if available)

    }

    public HitsResults window(long first, long number) {
        SearchSummary summary = nodeSearches.stream()
                .map(node -> node.getLatestSummary())
                .reduce(Aggregation::mergeSearchSummary)
                .get();

        ensureResultsRead(first + number);
        BigList<Hit> hitWindow = hits.subList((int)first, (int)(first + number));
        List<DocInfo> relevantDocs = hitWindow.stream()
                .map(h -> h.docPid)
                .collect(Collectors.toSet()).stream()
                .map(pid -> docInfos.get(pid))
                .collect(Collectors.toList());

        return new HitsResults(summary, hitWindow, relevantDocs);
    }

}
