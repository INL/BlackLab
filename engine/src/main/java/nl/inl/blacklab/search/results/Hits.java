package nl.inl.blacklab.search.results;

import java.util.Iterator;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import nl.inl.blacklab.exceptions.WildcardTermTooBroad;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.BLSpanQuery;

public interface Hits extends Results<Hit, HitProperty> {
    /**
     * Construct a Hits object from a SpanQuery.
     *
     * @param queryInfo      information about the original query
     * @param query          the query to execute to get the hits
     * @param searchSettings settings such as max. hits to process/count
     * @return hits found
     * @throws WildcardTermTooBroad if a wildcard term matches too many terms in the index
     */
    static Hits fromSpanQuery(QueryInfo queryInfo, BLSpanQuery query, SearchSettings searchSettings) throws WildcardTermTooBroad {
        if (queryInfo.index().blackLab().maxThreadsPerSearch() <= 1) {
            // We don't want to use multi-threaded search. Stick with the single-threaded version.
            return new HitsFromQuery(queryInfo, query, searchSettings);
        } else {
            return new HitsFromQueryParallel(queryInfo, query, searchSettings);
        }
    }

    /**
     * Make a wrapper Hits object for a list of Hit objects.
     * <p>
     * Will create Hit objects from the arrays. Mainly useful for testing.
     * Prefer using @link { {@link #fromList(QueryInfo, HitsInternal, CapturedGroups)} }
     *
     * @param queryInfo information about the original query
     * @param docs      doc ids
     * @param starts    hit starts
     * @param ends      hit ends
     * @return hits found
     */
    static Hits fromArrays(QueryInfo queryInfo, int[] docs, int[] starts, int[] ends) {

        IntList lDocs = new IntArrayList(docs);
        IntList lStarts = new IntArrayList(starts);
        IntList lEnds = new IntArrayList(ends);

        return new HitsList(queryInfo, new HitsInternalLock32(lDocs, lStarts, lEnds), null);
    }

    static Hits fromList(QueryInfo queryInfo, HitsInternal hits, CapturedGroups capturedGroups) {
        return new HitsList(queryInfo, hits, capturedGroups);
    }

    static Hits fromList(
            QueryInfo queryInfo,
            HitsInternal hits,
            WindowStats windowStats,
            SampleParameters sampleParameters,
            long hitsCounted,
            long docsRetrieved,
            long docsCounted,
            CapturedGroups capturedGroups,
            boolean ascendingLuceneDocIds) {
        return new HitsList(
                queryInfo,
                hits,
                windowStats,
                sampleParameters,
                hitsCounted,
                docsRetrieved,
                docsCounted,
                capturedGroups,
                ascendingLuceneDocIds);
    }

    /**
     * Return a Hits object with a single hit
     *
     * @param queryInfo   query info
     * @param luceneDocId Lucene document id
     * @param start       start of hit
     * @param end         end of hit
     * @return hits object
     */
    static Hits singleton(QueryInfo queryInfo, int luceneDocId, int start, int end) {
        return fromArrays(queryInfo, new int[]{luceneDocId}, new int[]{start}, new int[]{end});
    }

    /**
     * Construct an immutable empty Hits object.
     *
     * @param queryInfo query info
     * @return hits found
     */
    static Hits immutableEmptyList(QueryInfo queryInfo) {
        return new HitsList(queryInfo, HitsInternal.EMPTY_SINGLETON, null);
    }

    /**
     * Get a window into this list of hits.
     * <p>
     * Use this if you're displaying part of the resultset, like in a paging
     * interface. It makes sure BlackLab only works with the hits you want to
     * display and doesn't do any unnecessary processing on the other hits.
     * <p>
     * HitsWindow includes methods to assist with paging, like figuring out if there
     * hits before or after the window.
     *
     * @param first      first hit in the window (0-based)
     * @param windowSize size of the window
     * @return the window
     */
    @Override
    Hits window(long first, long windowSize);

    /**
     * Take a sample of hits by wrapping an existing Hits object.
     *
     * @param sampleParameters sample parameters
     * @return the sample
     */
    @Override
    Hits sample(SampleParameters sampleParameters);

    /**
     * Return a new Hits object with these hits sorted by the given property.
     * <p>
     * This keeps the existing sort (or lack of one) intact and allows you to cache
     * different sorts of the same resultset.
     *
     * @param sortProp the hit property to sort on
     * @return a new Hits object with the same hits, sorted in the specified way
     */
    @Override
    Hits sort(HitProperty sortProp);

    boolean hasAscendingLuceneDocIds();

    /**
     * Return a Hits object with these hits in ascending Lucene doc id order.
     * <p>
     * Necessary for operations that make use of DocValues, which use sequential access.
     * <p>
     * If already in ascending order, returns itself.
     *
     * @return hits in ascending Lucene doc id order
     */
    Hits withAscendingLuceneDocIds();

    @Override
    HitGroups group(HitProperty criteria, long maxResultsToStorePerGroup);

    /**
     * Select only the hits where the specified property has the specified value.
     *
     * @param property property to select on, e.g. "word left of hit"
     * @param value    value to select on, e.g. 'the'
     * @return filtered hits
     */
    @Override
    Hits filter(HitProperty property, PropertyValue value);

    @Override
    long numberOfResultObjects();

    @Override
    Iterator<Hit> iterator();

    Iterator<EphemeralHit> ephemeralIterator();

    @Override
    Hit get(long i);

    /**
     * Did we exceed the maximum number of hits to process/count?
     * <p>
     * NOTE: this is only valid for the original Hits instance (that
     * executes the query), and not for any derived Hits instance (window, sorted, filtered, ...).
     * <p>
     * The reason that this is not part of QueryInfo is that this creates a brittle
     * link between derived Hits instances and the original Hits instances, which by now
     * may have been aborted, leaving the max stats in a frozen, incorrect state.
     *
     * @return our max stats, or {@link MaxStats#NOT_EXCEEDED} if not available for this instance
     */
    MaxStats maxStats();

    /**
     * Count occurrences of context words around hit.
     *
     * @param annotation  what annotation to get collocations for
     * @param contextSize how many words around the hits to use
     * @param sensitivity what sensitivity to use
     * @param sort        sort the resulting collocations by descending frequency?
     * @return the frequency of each occurring token
     */
    TermFrequencyList collocations(Annotation annotation, ContextSize contextSize, MatchSensitivity sensitivity, boolean sort);

    /**
     * Count occurrences of context words around hit.
     * <p>
     * Sorts the results from most to least frequent.
     *
     * @param annotation  what annotation to get collocations for
     * @param contextSize how many words around the hits to use
     * @param sensitivity what sensitivity to use
     * @return the frequency of each occurring token
     */
    TermFrequencyList collocations(Annotation annotation, ContextSize contextSize, MatchSensitivity sensitivity);

    /**
     * Count occurrences of context words around hit.
     * <p>
     * Matches case- and diacritics-sensitively, and sorts the results from most to least frequent.
     *
     * @param annotation  what annotation to get collocations for
     * @param contextSize how many words around the hits to use
     * @return the frequency of each occurring token
     */
    TermFrequencyList collocations(Annotation annotation, ContextSize contextSize);

    /**
     * Return a per-document view of these hits.
     *
     * @param maxHits maximum number of hits to store per document
     * @return the per-document view.
     */
    DocResults perDocResults(long maxHits);

    /**
     * Create concordances from the forward index.
     *
     * @param contextSize desired context size
     * @return concordances
     */
    Concordances concordances(ContextSize contextSize);

    Hits getHitsInDoc(int docid);

    ResultsStats hitsStats();

    ResultsStats docsStats();

    /**
     * Assumes this hit is within our lists.
     */
    Hits window(Hit hit);

    CapturedGroups capturedGroups();

    boolean hasCapturedGroups();

    Concordances concordances(ContextSize contextSize, ConcordanceType type);

    Kwics kwics(ContextSize contextSize);

    /**
     * Get Lucene document id for the specified hit
     *
     * @param index hit index
     * @return document id
     */
    int doc(long index);

    /**
     * Get start position for the specified hit
     *
     * @param index hit index
     * @return document id
     */
    int start(long index);

    /**
     * Get end position for the specified hit
     *
     * @param index hit index
     * @return document id
     */
    int end(long index);

    HitsInternal getInternalHitsUnsafe();


}
