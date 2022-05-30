package org.ivdnt.blacklab.aggregator.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import org.ivdnt.blacklab.aggregator.representation.AnnotatedField;
import org.ivdnt.blacklab.aggregator.representation.Annotation;
import org.ivdnt.blacklab.aggregator.representation.Corpus;
import org.ivdnt.blacklab.aggregator.representation.CorpusSummary;
import org.ivdnt.blacklab.aggregator.representation.HitGroup;
import org.ivdnt.blacklab.aggregator.representation.HitsResults;
import org.ivdnt.blacklab.aggregator.representation.MetadataField;
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
        cl.annotatedFields = mergeAnnotatedFields(i1.annotatedFields, i2.annotatedFields);
        cl.metadataFields = mergeMetadataFields(i1.metadataFields, i2.metadataFields);
        return cl;
    }

    public static List<MetadataField> mergeMetadataFields(List<MetadataField> a, List<MetadataField> b) {
        return a.stream().map(f -> {
            // Find corresponding field and merge annotation
            MetadataField af = f.clone();
            MetadataField bf = b.stream().filter(bff -> bff.name.equals(f.name)).findFirst().orElse(null);
            if (bf != null) {
                af.fieldValues = mergeCountMapInt(af.fieldValues, bf.fieldValues);
                af.valueListComplete = af.valueListComplete && bf.valueListComplete;
            }
            return af;
        }).collect(Collectors.toList());
    }

    private static Map<String, Integer> mergeCountMapInt(Map<String, Integer> a, Map<String, Integer> b) {
        if (a == null)
            return b;
        if (b == null)
            return a;
        Map<String, Integer> c = new HashMap<>(b);
        a.forEach( (k, v) -> c.merge(k, v, Integer::sum));
        return c;
    }

    private static Map<String, Long> mergeCountMapLong(Map<String, Long> a, Map<String, Long> b) {
        if (a == null)
            return b;
        if (b == null)
            return a;
        Map<String, Long> c = new HashMap<>(b);
        a.forEach( (k, v) -> c.merge(k, v, Long::sum));
        return c;
    }

    public static List<AnnotatedField> mergeAnnotatedFields(List<AnnotatedField> a, List<AnnotatedField> b) {
        return a.stream().map(f -> {
            // Find corresponding field and merge annotation
            AnnotatedField af = f.clone();
            b.stream().filter(bff -> bff.name.equals(f.name)).findFirst()
                    .ifPresent(bf -> af.annotations = mergeAnnotations(af.annotations, bf.annotations));
            return af;
        }).collect(Collectors.toList());
    }

    public static List<Annotation> mergeAnnotations(List<Annotation> a, List<Annotation> b) {
        return a.stream().map(f -> {
            // Find corresponding field and merge annotation
            Annotation af = f.clone();
            Annotation bf = b.stream().filter(bff -> bff.name.equals(f.name)).findFirst().orElse(null);
            if (bf != null) {
                af.values = mergeAnnotationValues(af.values, bf.values);
                if (af.valueListComplete != null && bf.valueListComplete != null)
                    af.valueListComplete = af.valueListComplete && bf.valueListComplete;
                else if (af.valueListComplete == null)
                    af.valueListComplete = bf.valueListComplete;
            }
            return af;
        }).collect(Collectors.toList());
    }

    public static List<String> mergeAnnotationValues(List<String> a, List<String> b) {
        if (a == null)
            return b;
        if (b == null)
            return a;
        Set<String> r = new HashSet<>();
        r.addAll(a);
        r.addAll(b);
        return new ArrayList<>(r);
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

        // Can't do this here because groups with same key will be merged
        //result.numberOfGroups = ...
        result.largestGroupSize = combine(a.largestGroupSize, b.largestGroupSize, Math::max);

        result.searchTime = Math.max(a.searchTime, b.searchTime);

        result.countTime = combine(a.countTime, b.countTime, Math::max);
        result.stillCounting = a.stillCounting || b.stillCounting;

        if (a.subcorpusSize != null || b.subcorpusSize != null) {
            // Add the subcorpus sizes
            result.subcorpusSize = new HashMap<>();
            if (a.subcorpusSize != null)
                result.subcorpusSize.putAll(a.subcorpusSize);
            if (b.subcorpusSize != null)
                b.subcorpusSize.forEach((key, value) -> result.subcorpusSize.merge(key, value, Long::sum));
        }

        return result;
    }

    private static Long combine(Long x, Long y, BiFunction<Long, Long, Long> operation) {
        if (x == null)
            return y;
        if (y == null)
            return x;
        return operation.apply(x, y);
    }

    public static HitGroup mergeHitGroups(HitGroup a, HitGroup b) {
        return new HitGroup(
                a.identity,
                a.identityDisplay,
                a.size + b.size,
                a.properties,
                a.numberOfDocs + b.numberOfDocs,
                mergeCountMapLong(a.subcorpusSize, b.subcorpusSize)
        );
    }

    public static HitsResults mergeHitsGrouped(HitsResults a, HitsResults b) {
        if (a.hits != null || b.hits != null)
            throw new IllegalArgumentException("Merging grouped results but there are hits");

        HitsResults result;
        try {
            result = a.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
        result.summary = mergeSearchSummary(a.summary, b.summary);

        // Convert to a sorted map and merge
        Map<String, HitGroup> ga = result.hitGroups.stream()
                .collect(Collectors.toMap(HitGroup::getIdentity, g -> g, (x, y) -> x, TreeMap::new));
        b.hitGroups.forEach( g -> ga.compute(g.identity, (k, v) -> v == null ? g : mergeHitGroups(v, g) ) );
        result.summary.numberOfGroups = (long) ga.size();

        // TODO: merge without disturbing the existing sort

        // Back to list
        result.hitGroups = new ArrayList<>(ga.values());

        return result;
    }
}
