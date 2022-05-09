package org.ivdnt.blacklab.aggregator.logic;

import java.util.stream.Collectors;

import org.ivdnt.blacklab.aggregator.representation.Corpus;
import org.ivdnt.blacklab.aggregator.representation.CorpusSummary;
import org.ivdnt.blacklab.aggregator.representation.SearchSummary;
import org.ivdnt.blacklab.aggregator.representation.Server;

public class Aggregation {

    /**
     * Merge server info pages from two nodes.
     *
     * Will determine intersection of available corpora.
     */
    public static Server mergeServer(Server s1, Server s2) {
        Server cl;
        try {
            cl = s1.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }

        // Determine intersection of corpus list and merge the IndexSummaries found
        cl.indices = s1.indices.stream()
                .map(i -> i.name)
                .filter(name -> s2.indices.stream().anyMatch(i2 -> i2.name.equals(name)))
                .map(name -> {
                    CorpusSummary i1 = s1.indices.stream().filter(i -> name.equals(i.name)).findFirst().orElseThrow();
                    CorpusSummary i2 = s2.indices.stream().filter(i -> name.equals(i.name)).findFirst().orElseThrow();
                    return mergeIndexSummary(i1, i2);
                })
                .collect(Collectors.toList());
        return cl;
    }

    /**
     * Merge corpus summary from two nodes.
     *
     * Will add tokenCount for corpus and take max. of timeModified.
     */
    public static CorpusSummary mergeIndexSummary(CorpusSummary i1, CorpusSummary i2) {
        CorpusSummary cl;
        try {
            cl = i1.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
        cl.timeModified = i1.timeModified.compareTo(i2.timeModified) < 0 ? i2.timeModified : i1.timeModified;
        cl.tokenCount = i1.tokenCount + i2.tokenCount;
        return cl;
    }

    /**
     * Merge corpus information from two nodes.
     *
     * Will add tokenCount and docCount for corpus.
     */
    public static Corpus mergeCorpus(Corpus i1, Corpus i2) {
        Corpus cl;
        try {
            cl = i1.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
        cl.tokenCount = i1.tokenCount + i2.tokenCount;
        cl.documentCount = i1.documentCount + i2.documentCount;
        return cl;
    }

    public static SearchSummary mergeSearchSummary(SearchSummary a, SearchSummary b) {
        SearchSummary result;
        try {
            result = a.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }

        result.numberOfHits = a.numberOfHits + b.numberOfHits;
        result.numberOfHitsRetrieved = a.numberOfHitsRetrieved + b.numberOfHitsRetrieved;
        result.numberOfDocs = a.numberOfDocs + b.numberOfDocs;
        result.numberOfDocsRetrieved = a.numberOfDocsRetrieved + b.numberOfDocsRetrieved;
        result.stoppedCountingHits = a.stoppedCountingHits || b.stoppedCountingHits;
        result.stoppedRetrievingHits = a.stoppedRetrievingHits || b.stoppedRetrievingHits;

        if (a.largestGroupSize != null)
            result.largestGroupSize = Math.max(a.largestGroupSize, b.largestGroupSize);

        result.searchTime = Math.max(a.searchTime, b.searchTime);
        result.countTime = Math.max(a.countTime, b.countTime);
        result.stillCounting = a.stillCounting || b.stillCounting;
        return result;
    }
}
