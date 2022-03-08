package nl.inl.blacklab.search.results;

import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.WildcardTermTooBroad;
import nl.inl.blacklab.forwardindex.FiidLookup;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.HitPropertyDocumentId;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.BLSpanQuery;

/**
 * A collection of matches.
 *
 * Mostly thread-safe. Deprecated method sortInPlace() is not and
 * should be avoided.
 */
public abstract class Hits extends Results<Hit, HitProperty> {


    protected final HitsInternal hitsArrays;

    protected static final Logger logger = LogManager.getLogger(Hits.class);

    /**
     * Construct a Hits object from a SpanQuery.
     *
     * @param queryInfo information about the original query
     * @param query the query to execute to get the hits
     * @param searchSettings settings such as max. hits to process/count
     * @return hits found
     * @throws WildcardTermTooBroad if a wildcard term matches too many terms in the index
     */
    public static Hits fromSpanQuery(QueryInfo queryInfo, BLSpanQuery query, SearchSettings searchSettings) throws WildcardTermTooBroad {
        if (queryInfo.index().blackLab().maxThreadsPerSearch() <= 1) {
            // We don't want to use multi-threaded search. Stick with the single-threaded version.
            return new HitsFromQuery(queryInfo, query, searchSettings);
        } else {
            return new HitsFromQueryParallel(queryInfo, query, searchSettings);
        }
    }

    /**
     * Make a wrapper Hits object for a list of Hit objects.
     *
     * Will create Hit objects from the arrays. Mainly useful for testing.
     * Prefer using @link { {@link #fromList(QueryInfo, HitsInternal, CapturedGroups)} }
     *
     * @param queryInfo information about the original query
     * @param docs doc ids
     * @param starts hit starts
     * @param ends hit ends
     * @return hits found
     */
    public static Hits fromArrays(QueryInfo queryInfo, int[] docs, int[] starts, int[] ends) {

        IntList lDocs = new IntArrayList(docs);
        IntList lStarts = new IntArrayList(starts);
        IntList lEnds = new IntArrayList(ends);

        return new HitsList(queryInfo, new HitsInternalLock32(lDocs, lStarts, lEnds), null);
    }

    public static Hits fromList(QueryInfo queryInfo, HitsInternal hits, CapturedGroups capturedGroups) {
        return new HitsList(queryInfo, hits, capturedGroups);
    }

    public static Hits fromList(
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
     * @param queryInfo query info
     * @param luceneDocId Lucene document id
     * @param start start of hit
     * @param end end of hit
     * @return hits object
     */
    public static Hits singleton(QueryInfo queryInfo, int luceneDocId, int start, int end) {
        return fromArrays(queryInfo, new int [] {luceneDocId}, new int[] {start}, new int[] {end});
    }

    /**
     * Construct an immutable empty Hits object.
     *
     * @param queryInfo query info
     * @return hits found
     */
    public static Hits immutableEmptyList(QueryInfo queryInfo) {
        return new HitsList(queryInfo, HitsInternal.EMPTY_SINGLETON, null);
    }

    /**
     * Minimum number of hits to fetch in an ensureHitsRead() block.
     *
     * This prevents locking again and again for a single hit when iterating.
     *
     * See {@link HitsFromQuery} and {@link HitsFiltered}.
     */
    protected static final int FETCH_HITS_MIN = 20;

    /**
     * Our captured groups, or null if we have none.
     */
    protected CapturedGroups capturedGroups;

    /**
     * The number of hits we've seen and counted so far. May be more than the number
     * of hits we've retrieved if that exceeds maxHitsToRetrieve.
     */
    protected long hitsCounted = 0;

    /**
     * The number of separate documents we've seen in the hits retrieved.
     */
    protected long docsRetrieved = 0;

    /**
     * The number of separate documents we've counted so far (includes non-retrieved
     * hits).
     */
    protected long docsCounted = 0;

    private final ResultsStats docsStats = new ResultsStats() {

        @Override
        public boolean processedAtLeast(long lowerBound) {
            while (!doneProcessingAndCounting() && docsProcessedSoFar() < lowerBound) {
                ensureResultsRead(hitsArrays.size() + FETCH_HITS_MIN);
            }
            return docsProcessedSoFar() >= lowerBound;
        }

        @Override
        public long processedTotal() {
            return docsProcessedTotal();
        }

        @Override
        public long processedSoFar() {
            return docsProcessedSoFar();
        }

        @Override
        public long countedSoFar() {
            return docsCountedSoFar();
        }

        @Override
        public long countedTotal() {
            return docsCountedTotal();
        }

        @Override
        public boolean done() {
            return doneProcessingAndCounting();
        }

        @Override
        public MaxStats maxStats() {
            return Hits.this.maxStats();
        }

        @Override
        public String toString() {
            return "ResultsStats(" + Hits.this + ")";
        }

    };

    /** Construct an empty, mutable Hits object.
     *
     * @param queryInfo query info for corresponding query
     * @deprecated if you need an empty Hits object, use {@link #immutableEmptyList(QueryInfo)};
     *     otherwise, use {@link #Hits(QueryInfo, HitsInternal)}
     */
    @Deprecated
    public Hits(QueryInfo queryInfo) {
        this(queryInfo, false);
    }

    /** Construct an empty Hits object.
     *
     * @param queryInfo query info for corresponding query
     * @param readOnly if true, returns an immutable Hits object; otherwise, a mutable one
     */
    public Hits(QueryInfo queryInfo, boolean readOnly) {
        this(queryInfo, readOnly ? HitsInternal.EMPTY_SINGLETON : HitsInternal.create(-1, true, true));
    }

    /**
     * Construct a Hits object from a hits array.
     *
     * NOTE: if you pass null, a new, mutable HitsArray is used. For an immutable empty Hits object, use {@link #Hits(QueryInfo, boolean)}.
     *
     * @param queryInfo query info for corresponding query
     * @param hits hits array to use for this object. The array is used as-is, not copied.
     */
    public Hits(QueryInfo queryInfo, HitsInternal hits) {
        super(queryInfo);
        this.hitsArrays = hits == null ? HitsInternal.create(-1, true, true) : hits;
    }

    // Inherited from Results
    //--------------------------------------------------------------------

    /**
     * Get a window into this list of hits.
     *
     * Use this if you're displaying part of the resultset, like in a paging
     * interface. It makes sure BlackLab only works with the hits you want to
     * display and doesn't do any unnecessary processing on the other hits.
     *
     * HitsWindow includes methods to assist with paging, like figuring out if there
     * hits before or after the window.
     *
     * @param first first hit in the window (0-based)
     * @param windowSize size of the window
     * @return the window
     */
    @Override
    public Hits window(long first, long windowSize) {
        // Error if first out of range
        WindowStats windowStats;
        boolean emptyResultSet = !hitsProcessedAtLeast(1);
        if (first < 0 || (emptyResultSet && first > 0) ||
            (!emptyResultSet && !hitsProcessedAtLeast(first + 1))) {
            //throw new IllegalArgumentException("First hit out of range");
            return Hits.immutableEmptyList(queryInfo());
        }

        // Auto-clamp number
        // take care not to always call size(), as that blocks until we're done!
        // Instead, first call ensureResultsRead so we block until we have either have enough or finish
        this.ensureResultsRead(first + windowSize);
        // and only THEN do this, since now we know if we don't have this many hits, we're done, and it's safe to call size
        long number = hitsProcessedAtLeast(first + windowSize) ? windowSize : size() - first;

        // Copy the hits we're interested in.
        CapturedGroups capturedGroups = hasCapturedGroups() ? new CapturedGroupsImpl(capturedGroups().names()) : null;
        MutableInt docsRetrieved = new MutableInt(0); // Bypass warning (enclosing scope must be effectively final)
        HitsInternal window = HitsInternal.create(windowSize, windowSize > Integer.MAX_VALUE, false);

        this.hitsArrays.withReadLock(h -> {
            int prevDoc = -1;
            EphemeralHit hit = new EphemeralHit();
            for (long i = first; i < first + number; i++) {
                h.getEphemeral(i, hit);
                if (capturedGroups != null) {
                    Hit hh = hit.toHit();
                    capturedGroups.put(hh, capturedGroups().get(hh));
                }
                // OPT: copy context as well..?

                int doc = hit.doc;
                if (doc != prevDoc) {
                    docsRetrieved.add(1);
                    prevDoc = doc;
                }
                window.add(hit);
            }
        });
        boolean hasNext = hitsProcessedAtLeast(first + windowSize + 1);
        windowStats = new WindowStats(hasNext, first, windowSize, number);
        return Hits.fromList(queryInfo(), window, windowStats, null,
                hitsCounted, docsRetrieved.getValue(), docsRetrieved.getValue(),
                capturedGroups, hasAscendingLuceneDocIds());
    }

    /**
     * Take a sample of hits by wrapping an existing Hits object.
     *
     * @param sampleParameters sample parameters
     * @return the sample
     */
    @Override
    public Hits sample(SampleParameters sampleParameters) {

        // Determine total number of hits (fetching all of them)
        long totalNumberOfHits = size();
        if (totalNumberOfHits > Integer.MAX_VALUE) {
            // TODO: we might want to enable this, because the whole point of sampling is to make sense
            //       of huge result sets without having to look at every hit.
            //       Ideally, old seeds would keep working as well (although that may not be practical,
            //       and not likely to be a huge issue)
            throw new BlackLabRuntimeException("Cannot sample from more than " + Integer.MAX_VALUE + " hits");
        }

        // We can later provide an optimized version that uses a HitsSampleCopy or somesuch
        // (this class could save memory by only storing the hits we're interested in)
        Random random = new Random(sampleParameters.seed());
        Set<Long> chosenHitIndices = new TreeSet<>();
        long numberOfHitsToSelect = sampleParameters.numberOfHits(totalNumberOfHits);
        if (numberOfHitsToSelect >= size()) {
            numberOfHitsToSelect = size(); // default to all hits in this case
            for (long i = 0; i < numberOfHitsToSelect; ++i) {
                chosenHitIndices.add(i);
            }
        } else {
            // Choose the hits
            for (int i = 0; i < numberOfHitsToSelect; i++) {
                // Choose a hit we haven't chosen yet
                long hitIndex;
                do {
                    hitIndex = random.nextInt((int)Math.min(Integer.MAX_VALUE, size()));
                } while (chosenHitIndices.contains(hitIndex));
                chosenHitIndices.add(hitIndex);
            }
        }

        MutableInt docsInSample = new MutableInt(0);
        CapturedGroups capturedGroups = hasCapturedGroups() ? new CapturedGroupsImpl(capturedGroups().names()) : null;
        HitsInternal sample = HitsInternal.create(numberOfHitsToSelect, numberOfHitsToSelect > Integer.MAX_VALUE, false);

        this.hitsArrays.withReadLock(__ -> {
            int previousDoc = -1;
            EphemeralHit hit = new EphemeralHit();
            for (Long hitIndex : chosenHitIndices) {
                this.hitsArrays.getEphemeral(hitIndex, hit);
                if (hit.doc != previousDoc) {
                    docsInSample.add(1);
                    previousDoc = hit.doc;
                }

                sample.add(hit);
                if (capturedGroups != null) {
                    Hit h = hit.toHit();
                    capturedGroups.put(h, this.capturedGroups.get(h));
                }
            }
        });

        return Hits.fromList(queryInfo(), sample, null, sampleParameters, sample.size(),
                docsInSample.getValue(), docsInSample.getValue(), capturedGroups,
                hasAscendingLuceneDocIds());
    }

    /**
     * Return a new Hits object with these hits sorted by the given property.
     *
     * This keeps the existing sort (or lack of one) intact and allows you to cache
     * different sorts of the same resultset.
     *
     * @param sortProp the hit property to sort on
     * @return a new Hits object with the same hits, sorted in the specified way
     */
    @Override
    public Hits sort(HitProperty sortProp) {
        // We need a HitProperty with the correct Hits object
        // If we need context, make sure we have it.
        List<Annotation> requiredContext = sortProp.needsContext();
        List<FiidLookup> fiidLookups = FiidLookup.getList(requiredContext, queryInfo().index().reader());
        sortProp = sortProp.copyWith(this,
            requiredContext == null ? null : new Contexts(this, requiredContext, sortProp.needsContextSize(index()), fiidLookups));

        // Perform the actual sort.
        this.ensureAllResultsRead();
        HitsInternal sorted = this.hitsArrays.sort(sortProp); // TODO use wrapper objects

        CapturedGroups capturedGroups = capturedGroups();
        long hitsCounted = hitsCountedSoFar();
        long docsRetrieved = docsProcessedSoFar();
        long docsCounted = docsCountedSoFar();
        boolean ascendingLuceneDocIds = sortProp instanceof HitPropertyDocumentId;
        return Hits.fromList(queryInfo(), sorted, null, null,
                hitsCounted, docsRetrieved, docsCounted, capturedGroups, ascendingLuceneDocIds);
    }

    public abstract boolean hasAscendingLuceneDocIds();

    /**
     * Return a Hits object with these hits in ascending Lucene doc id order.
     *
     * Necessary for operations that make use of DocValues, which use sequential access.
     *
     * If already in ascending order, returns itself.
     *
     * @return hits in ascending Lucene doc id order
     */
    public Hits withAscendingLuceneDocIds() {
        return hasAscendingLuceneDocIds() ? this : sort(new HitPropertyDocumentId());
    }

    @Override
    public HitGroups group(HitProperty criteria, long maxResultsToStorePerGroup) {
        ensureAllResultsRead();
        return HitGroups.fromHits(this, criteria, maxResultsToStorePerGroup);
    }

    /**
     * Select only the hits where the specified property has the specified value.
     *
     * @param property property to select on, e.g. "word left of hit"
     * @param value value to select on, e.g. 'the'
     * @return filtered hits
     */
    @Override
    public Hits filter(HitProperty property, PropertyValue value) {
        return new HitsFiltered(this, property, value);
    }

    @Override
    protected long resultsCountedTotal() {
        return hitsCountedTotal();
    }

    @Override
    protected long resultsCountedSoFar() {
        return hitsCountedSoFar();
    }

    @Override
    protected boolean resultsProcessedAtLeast(long lowerBound) {
        return this.hitsArrays.size() >= lowerBound;
    }

    @Override
    protected long resultsProcessedTotal() {
        ensureAllResultsRead();
        return this.hitsArrays.size();
    }

    @Override
    protected long resultsProcessedSoFar() {
        return hitsProcessedSoFar();
    }

    @Override
    public long numberOfResultObjects() {
        return this.hitsArrays.size();
    }

    @Override
    public Iterator<Hit> iterator() {
        // We need to wrap the internal iterator, as we probably shouldn't
        return new Iterator<>() {
            final Iterator<EphemeralHit> i = ephemeralIterator();

            @Override
            public boolean hasNext() {
                return i.hasNext();
            }

            @Override
            public Hit next() {
                return i.next().toHit();
            }
        };
    }

    public Iterator<EphemeralHit> ephemeralIterator() {
        ensureAllResultsRead();
        return hitsArrays.iterator();
    }

    @Override
    public Hit get(long i) {
        ensureResultsRead(i + 1);
        return this.hitsArrays.get(i);
    }

    /**
     * Did we exceed the maximum number of hits to process/count?
     *
     * NOTE: this is only valid for the original Hits instance (that
     * executes the query), and not for any derived Hits instance (window, sorted, filtered, ...).
     *
     * The reason that this is not part of QueryInfo is that this creates a brittle
     * link between derived Hits instances and the original Hits instances, which by now
     * may have been aborted, leaving the max stats in a frozen, incorrect state.
     *
     * @return our max stats, or {@link MaxStats#NOT_EXCEEDED} if not available for this instance
     */
    public abstract MaxStats maxStats();

    /**
     * Count occurrences of context words around hit.
     *
     * @param annotation what annotation to get collocations for
     * @param contextSize how many words around the hits to use
     * @param sensitivity what sensitivity to use
     * @param sort sort the resulting collocations by descending frequency?
     *
     * @return the frequency of each occurring token
     */
    public TermFrequencyList collocations(Annotation annotation, ContextSize contextSize, MatchSensitivity sensitivity, boolean sort) {
        return TermFrequencyList.collocations(this, annotation, contextSize, sensitivity, sort);
    }

    /**
     * Count occurrences of context words around hit.
     *
     * Sorts the results from most to least frequent.
     *
     * @param annotation what annotation to get collocations for
     * @param contextSize how many words around the hits to use
     * @param sensitivity what sensitivity to use
     * @return the frequency of each occurring token
     */
    public TermFrequencyList collocations(Annotation annotation, ContextSize contextSize, MatchSensitivity sensitivity) {
        return collocations(annotation, contextSize, sensitivity, true);
    }

    /**
     * Count occurrences of context words around hit.
     *
     * Matches case- and diacritics-sensitively, and sorts the results from most to least frequent.
     *
     * @param annotation what annotation to get collocations for
     * @param contextSize how many words around the hits to use
     *
     * @return the frequency of each occurring token
     */
    public TermFrequencyList collocations(Annotation annotation, ContextSize contextSize) {
        return collocations(annotation, contextSize, MatchSensitivity.SENSITIVE, true);
    }

    /**
     * Return a per-document view of these hits.
     *
     * @param maxHits maximum number of hits to store per document
     *
     * @return the per-document view.
     */
    public DocResults perDocResults(long maxHits) {
        return DocResults.fromHits(queryInfo(), this, maxHits);
    }

    /**
     * Create concordances from the forward index.
     *
     * @param contextSize desired context size
     * @return concordances
     */
    public Concordances concordances(ContextSize contextSize) {
        return concordances(contextSize, ConcordanceType.FORWARD_INDEX);
    }

    // Getting / iterating over the hits
    //--------------------------------------------------------------------

    public Hits getHitsInDoc(int docid) {
        ensureAllResultsRead();
        HitsInternal r = HitsInternal.create(-1, size() > Integer.MAX_VALUE, false);
        // all hits read, no lock needed.
        for (EphemeralHit h : this.hitsArrays) {
            if (h.doc == docid)
                r.add(h);
        }
        return new HitsList(queryInfo(), r, null);
    }

    // Stats
    // ---------------------------------------------------------------

    public ResultsStats hitsStats() {
        return resultsStats();
    }

    protected boolean hitsProcessedAtLeast(long lowerBound) {
        return this.hitsArrays.size() >= lowerBound;
    }

    protected long hitsProcessedTotal() {
        ensureAllResultsRead();
        return this.hitsArrays.size();
    }

    protected long hitsProcessedSoFar() {
        return this.hitsArrays.size();
    }

    protected long hitsCountedTotal() {
        ensureAllResultsRead();
        return hitsCounted;
    }

    public ResultsStats docsStats() {
        return docsStats;
    }

    protected long docsProcessedTotal() {
        ensureAllResultsRead();
        return docsRetrieved;
    }

    protected long docsCountedTotal() {
        ensureAllResultsRead();
        return docsCounted;
    }

    protected long hitsCountedSoFar() {
        return hitsCounted;
    }

    protected long docsCountedSoFar() {
        return docsCounted;
    }

    protected long docsProcessedSoFar() {
        return docsRetrieved;
    }

    // Deriving other Hits / Results instances
    //--------------------------------------------------------------------

    /** Assumes this hit is within our lists. */
    public Hits window(Hit hit) {
        CapturedGroups capturedGroups = null;
        if (this.capturedGroups != null) {
            this.size(); // ensure all results read (so we know our hit's capture groups are available)
            capturedGroups = new CapturedGroupsImpl(this.capturedGroups.names());
            capturedGroups.put(hit, this.capturedGroups.get(hit));
        }

        HitsInternal r = HitsInternal.create(1, false, false);
        r.add(hit);

        return Hits.fromList(
            this.queryInfo(),
            r,
            new WindowStats(false, 1, 1, 1),
            null, // window is not sampled
            1,
            1,
            1,
            capturedGroups,
            true);
    }

    // Captured groups
    //--------------------------------------------------------------------

    public CapturedGroups capturedGroups() {
        return capturedGroups;
    }

    public boolean hasCapturedGroups() {
        return capturedGroups != null;
    }

    // Hits display
    //--------------------------------------------------------------------

    public Concordances concordances(ContextSize contextSize, ConcordanceType type) {
        if (contextSize == null)
            contextSize = index().defaultContextSize();
        if (type == null)
            type = ConcordanceType.FORWARD_INDEX;
        return new Concordances(this, type, contextSize);
    }

    public Kwics kwics(ContextSize contextSize) {
        if (contextSize == null)
            contextSize = index().defaultContextSize();
        return new Kwics(this, contextSize);
    }

    /**
     * Get Lucene document id for the specified hit
     * @param index hit index
     * @return document id
     */
    public int doc(long index) {
        ensureResultsRead(index + 1);
        return hitsArrays.doc(index);
    }

    /**
     * Get start position for the specified hit
     * @param index hit index
     * @return document id
     */
    public int start(long index) {
        ensureResultsRead(index + 1);
        return hitsArrays.start(index);
    }

    /**
     * Get end position for the specified hit
     * @param index hit index
     * @return document id
     */
    public int end(long index) {
        ensureResultsRead(index + 1);
        return hitsArrays.end(index);
    }
}
