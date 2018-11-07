package nl.inl.blacklab.search.results;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.exceptions.WildcardTermTooBroad;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.resultproperty.ResultProperty;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.BLSpanQuery;

public abstract class Hits extends Results<Hit> {

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
        //return new HitsFromQuery(queryInfo, query, searchSettings);
        return new HitsFromQueryParallel(queryInfo, query, searchSettings);
    }

    /**
     * Make a wrapper Hits object for a list of Hit objects.
     *
     * Does not copy the list, but reuses it.
     * 
     * @param queryInfo information about the original query
     * @param hits the list of hits to wrap, or null for empty Hits object
     * @return hits found
     */
    public static Hits fromList(QueryInfo queryInfo, List<Hit> hits) {
        return new HitsList(queryInfo, hits);
    }

    /**
     * Make a wrapper Hits object for a list of Hit objects.
     *
     * Will create Hit objects from the arrays. Mainly useful for testing.
     * 
     * @param queryInfo information about the original query
     * @param doc doc ids
     * @param start hit starts
     * @param end hit ends
     * @return hits found
     */
    public static Hits fromArrays(QueryInfo queryInfo, int[] doc, int[] start, int[] end) {
        return new HitsList(queryInfo, doc, start, end);
    }
    
    public static Hits fromList(QueryInfo queryInfo, List<Hit> results, WindowStats windowStats, SampleParameters sampleParameters,
            int hitsCounted, int docsRetrieved, int docsCounted, CapturedGroupsImpl capturedGroups) {
        return new HitsList(queryInfo, results, windowStats, sampleParameters, hitsCounted, docsRetrieved, docsCounted, capturedGroups);
    }

    /**
     * Construct an empty Hits object.
     * 
     * @param queryInfo query info 
     * @return hits found
     */
    public static Hits emptyList(QueryInfo queryInfo) {
        return Hits.fromList(queryInfo, (List<Hit>) null);
    }

    /**
     * Take a sample of hits by wrapping an existing Hits object.
     *
     * @param sampleParameters sample parameters 
     * @return the sample
     */
    @Override
    public Hits sample(SampleParameters sampleParameters) {
        // We can later provide an optimized version that uses a HitsSampleCopy or somesuch
        // (this class could save memory by only storing the hits we're interested in)
        
        List<Hit> results = new ArrayList<>();
        int hitsCounted = 0;
        int docsRetrieved = 0;
        int docsCounted = 0;
        CapturedGroupsImpl capturedGroups = null;

        Random random = new Random(sampleParameters.seed());
        int numberOfHitsToSelect = sampleParameters.numberOfHits(size());
        if (numberOfHitsToSelect > size())
            numberOfHitsToSelect = size(); // default to all hits in this case
        // Choose the hits
        Set<Integer> chosenHitIndices = new TreeSet<>();
        for (int i = 0; i < numberOfHitsToSelect; i++) {
            // Choose a hit we haven't chosen yet
            int hitIndex;
            do {
                hitIndex = random.nextInt(size());
            } while (chosenHitIndices.contains(hitIndex));
            chosenHitIndices.add(hitIndex);
        }
        
        // Add the hits in order of their index
        int previousDoc = -1;
        if (hasCapturedGroups())
            capturedGroups = new CapturedGroupsImpl(capturedGroups().names());
        for (Integer hitIndex : chosenHitIndices) {
            Hit hit = get(hitIndex);
            if (hit.doc() != previousDoc) {
                docsRetrieved++;
                docsCounted++;
                previousDoc = hit.doc();
            }
            results.add(hit);
            if (capturedGroups != null)
                capturedGroups.put(hit, this.capturedGroups.get(hit));
            hitsCounted++;
        }
        
        return Hits.fromList(queryInfo(), results, null, sampleParameters, hitsCounted, docsRetrieved, docsCounted, null);
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
    protected CapturedGroupsImpl capturedGroups;
    
    /**
     * The number of hits we've seen and counted so far. May be more than the number
     * of hits we've retrieved if that exceeds maxHitsToRetrieve.
     */
    protected int hitsCounted = 0;

    /**
     * The number of separate documents we've seen in the hits retrieved.
     */
    protected int docsRetrieved = 0;

    /**
     * The number of separate documents we've counted so far (includes non-retrieved
     * hits).
     */
    protected int docsCounted = 0;

    private ResultsStats docsStats = new ResultsStats() {

        @Override
        public boolean processedAtLeast(int lowerBound) {
            while (!doneProcessingAndCounting() && docsProcessedSoFar() < lowerBound) {
                ensureResultsRead(results.size() + FETCH_HITS_MIN);
            }
            return docsProcessedSoFar() >= lowerBound;
        }

        @Override
        public int processedTotal() {
            return docsProcessedTotal();
        }

        @Override
        public int processedSoFar() {
            return docsProcessedSoFar();
        }

        @Override
        public int countedSoFar() {
            return docsCountedSoFar();
        }

        @Override
        public int countedTotal() {
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
        
    };
    
    public Hits(QueryInfo queryInfo) {
        super(queryInfo);
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
    public Hits window(int first, int windowSize) {
        CapturedGroupsImpl capturedGroups = null;
        int hitsCounted = 0;
        int docsRetrieved = 0;
        int docsCounted = 0;

        // Error if first out of range
        List<Hit> results = new ArrayList<>();
        WindowStats windowStats;
        boolean emptyResultSet = !hitsProcessedAtLeast(1);
        if (first < 0 || (emptyResultSet && first > 0) ||
                (!emptyResultSet && !hitsProcessedAtLeast(first + 1))) {
            //throw new IllegalArgumentException("First hit out of range");
            return Hits.emptyList(queryInfo());
        }

        // Auto-clamp number
        int number = windowSize;
        if (!hitsProcessedAtLeast(first + number))
            number = size() - first;

        // Copy the hits we're interested in.
        if (hasCapturedGroups())
            capturedGroups = new CapturedGroupsImpl(capturedGroups().names());
        int prevDoc = -1;
        hitsCounted = 0;
        for (int i = first; i < first + number; i++) {
            Hit hit = get(i);
            results.add(hit);
            if (capturedGroups != null)
                capturedGroups.put(hit, capturedGroups().get(hit));
            // OPT: copy context as well..?
            
            if (hit.doc() != prevDoc) {
                docsRetrieved++;
                docsCounted++;
                prevDoc = hit.doc();
            }
        }
        boolean hasNext = hitsProcessedAtLeast(first + windowSize + 1);
        windowStats = new WindowStats(hasNext, first, windowSize, number);
        return Hits.fromList(queryInfo(), results, windowStats, null, hitsCounted, docsRetrieved, docsCounted, capturedGroups);
    }

    /**
     * Count occurrences of context words around hit.
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
     * @param maxHits 
     *
     * @return the per-document view.
     */
    public DocResults perDocResults(int maxHits) {
        return DocResults.fromHits(queryInfo(), this, maxHits);
    }
    
    @Override
    public HitGroups group(ResultProperty<Hit> criteria, int maxResultsToStorePerGroup) {
        return HitGroups.fromHits(this, (HitProperty)criteria, maxResultsToStorePerGroup);
    }
    
    /**
     * Select only the hits where the specified property has the specified value.
     * 
     * @param property property to select on, e.g. "word left of hit"
     * @param value value to select on, e.g. 'the'
     * @return filtered hits
     */
    @Override
    public Hits filter(ResultProperty<Hit> property, PropertyValue value) {
        return new HitsFiltered(this, (HitProperty)property, value);
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
    
    @Override
    public int size() {
        return hitsProcessedTotal();
    }
    
    // Getting / iterating over the hits
    //--------------------------------------------------------------------

    public Hits getHitsInDoc(int docid) {
        ensureAllResultsRead();
        List<Hit> hitsInDoc = new ArrayList<>();
        for (Hit hit : results) {
            if (hit.doc() == docid)
                hitsInDoc.add(hit);
        }
        return Hits.fromList(queryInfo(), hitsInDoc);
    }
    
    // Stats
    // ---------------------------------------------------------------
    
    public ResultsStats hitsStats() {
        return resultsStats();
    }

    protected boolean hitsProcessedAtLeast(int lowerBound) {
        return resultsProcessedAtLeast(lowerBound);
    }

    protected int hitsProcessedTotal() {
        return resultsProcessedTotal();
    }

    protected int hitsProcessedSoFar() {
        return resultsProcessedSoFar();
    }
    
    protected int hitsCountedTotal() {
        ensureAllResultsRead();
        return hitsCounted;
    }

    public ResultsStats docsStats() {
        return docsStats;
    }

    protected int docsProcessedTotal() {
        ensureAllResultsRead();
        return docsRetrieved;
    }

    protected int docsCountedTotal() {
        ensureAllResultsRead();
        return docsCounted;
    }

    protected int hitsCountedSoFar() {
        return hitsCounted;
    }

    protected int docsCountedSoFar() {
        return docsCounted;
    }

    protected int docsProcessedSoFar() {
        return docsRetrieved;
    }

    @Override
    protected int resultsCountedTotal() {
        return hitsCountedTotal();
    }

    @Override
    protected int resultsCountedSoFar() {
        return hitsCountedSoFar();
    }

    // Deriving other Hits / Results instances
    //--------------------------------------------------------------------
    
    public Hits window(Hit hit) {
        ensureAllResultsRead();
        return window(results.indexOf(hit), 1);
    }
    
    /**
     * Return a new Hits object with these hits sorted by the given property.
     *
     * This keeps the existing sort (or lack of one) intact and allows you to cache
     * different sorts of the same resultset. The hits themselves are reused between
     * the two Hits instances, so not too much additional memory is used.
     *
     * @param sortProp the hit property to sort on
     * @return a new Hits object with the same hits, sorted in the specified way
     */
    @Override
    public <P extends ResultProperty<Hit>> Hits sort(P sortProp) {
        if (!(sortProp instanceof HitProperty))
            throw new UnsupportedOperationException("Can only sort Hits by an instance of HitProperty!");
        HitProperty hitProp = (HitProperty)sortProp;
        
        List<Hit> sorted = new ArrayList<>(resultsList());

        // We need a HitProperty with the correct Hits object
        // If we need context, make sure we have it.
        List<Annotation> requiredContext = hitProp.needsContext();
        hitProp = hitProp.copyWith(this,
                requiredContext == null ? null : new Contexts(this, requiredContext, hitProp.needsContextSize(index())));

        // Perform the actual sort.
        sorted.sort(hitProp);

        CapturedGroupsImpl capturedGroups = capturedGroups();
        int hitsCounted = hitsCountedSoFar();
        int docsRetrieved = docsProcessedSoFar();
        int docsCounted = docsCountedSoFar();
        return Hits.fromList(queryInfo(), sorted, null, null, hitsCounted, docsRetrieved, docsCounted, capturedGroups);
    }
    
    // Captured groups
    //--------------------------------------------------------------------
    
    public CapturedGroupsImpl capturedGroups() {
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
    
    @Override
    public int numberOfResultObjects() {
        return hitsProcessedSoFar();
    }

}
