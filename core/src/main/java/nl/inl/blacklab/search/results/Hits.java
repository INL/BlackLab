package nl.inl.blacklab.search.results;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.exceptions.WildcardTermTooBroad;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
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
     * @param maxSettings max. hits to process/count
     * @return hits found
     * @throws WildcardTermTooBroad if a wildcard term matches too many terms in the index
     */
    public static Hits fromSpanQuery(QueryInfo queryInfo, BLSpanQuery query, MaxSettings maxSettings) throws WildcardTermTooBroad {
        return new HitsFromQuery(queryInfo, query, maxSettings);
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
     * @param hits hits object to wrap
     * @param parameters sample parameters 
     * @return the sample
     */
    public static Hits sample(Hits hits, SampleParameters parameters) {
        // We can later provide an optimized version that uses a HitsSampleCopy or somesuch
        // (this class could save memory by only storing the hits we're interested in)
        return new HitsList(hits, parameters);
    }

    /**
     * Minimum number of hits to fetch in an ensureHitsRead() block.
     * 
     * This prevents locking again and again for a single hit when iterating.
     */
    protected static final int FETCH_HITS_MIN = 20;

    /** Id the next Hits instance will get */
    private static int nextHitsObjId = 0;

    private static synchronized int getNextHitsObjId() {
        return nextHitsObjId++;
    }

    /** Unique id of this Hits instance (for debugging) */
    protected final int hitsObjId = getNextHitsObjId();
    
    /**
     * The hits.
     */
    protected List<Hit> hits;

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
        return new HitsList(this, first, windowSize);
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
     * 
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
     *
     * @return the per-document view.
     */
    public DocResults perDocResults() {
        return DocResults.fromHits(queryInfo(), this);
    }

    /**
     * Group these hits by a criterium (or several criteria).
     *
     * @param criteria the hit property to group on
     * @return a HitGroups object representing the grouped hits
     */
    public HitGroups groupedBy(final HitProperty criteria) {
        return HitGroupsImpl.fromHits(this, criteria);
    }
    
    /**
     * Select only the hits where the specified property has the specified value.
     * 
     * @param property property to select on, e.g. "word left of hit"
     * @param value value to select on, e.g. 'the'
     * @return filtered hits
     */
    public Hits filteredBy(HitProperty property, PropertyValue value) {
        return new HitsFiltered(this, property, value);
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
    
    /**
     * Ensure that we have read all hits.
     *
     * @throws InterruptedException if the thread was interrupted during this
     *             operation
     */
    protected void ensureAllHitsRead() throws InterruptedException {
        ensureHitsRead(-1);
    }

    /**
     * Ensure that we have read at least as many hits as specified in the parameter.
     *
     * @param number the minimum number of hits that will have been read when this
     *            method returns (unless there are fewer hits than this); if
     *            negative, reads all hits
     * @throws InterruptedException if the thread was interrupted during this
     *             operation
     */
    protected abstract void ensureHitsRead(int number) throws InterruptedException;
    
    // Getting / iterating over the hits
    //--------------------------------------------------------------------

    @Override
    public Iterator<Hit> iterator() {
        // Construct a custom iterator that iterates over the hits in the hits
        // list, but can also take into account the Spans object that may not have
        // been fully read. This ensures we don't instantiate Hit objects for all hits
        // if we just want to display the first few.
        return new Iterator<Hit>() {
        
            int index = -1;
        
            @Override
            public boolean hasNext() {
                // Do we still have hits in the hits list?
                try {
                    ensureHitsRead(index + 2);
                } catch (InterruptedException e) {
                    // Thread was interrupted. Don't finish reading hits and accept possibly wrong
                    // answer.
                    // Client must detect the interruption and stop the thread.
                    Thread.currentThread().interrupt();
                }
                return hits.size() >= index + 2;
            }
        
            @Override
            public Hit next() {
                // Check if there is a next, taking unread hits from Spans into account
                if (hasNext()) {
                    index++;
                    return hits.get(index);
                }
                throw new NoSuchElementException();
            }
        
            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        
        };
    }
    
    @Override
    public synchronized Hit get(int i) {
        try {
            ensureHitsRead(i + 1);
        } catch (InterruptedException e) {
            // Thread was interrupted. Required hit hasn't been gathered;
            // we will just return null.
            Thread.currentThread().interrupt();
        }
        if (i >= hits.size())
            return null;
        return hits.get(i);
    }
    
    public Hits getHitsInDoc(int docid) {
        try {
            ensureAllHitsRead();
        } catch (InterruptedException e) {
            // Interrupted. Just return no hits;
            // client should detect thread was interrupted if it
            // wants to use background threads.
            Thread.currentThread().interrupt();
            return Hits.emptyList(queryInfo());
        }
        List<Hit> hitsInDoc = new ArrayList<>();
        for (Hit hit : hits) {
            if (hit.doc() == docid)
                hitsInDoc.add(hit);
        }
        return Hits.fromList(queryInfo(), hitsInDoc);
    }
    
    // Stats
    // ---------------------------------------------------------------

    public boolean hitsProcessedAtLeast(int lowerBound) {
        try {
            // Try to fetch at least this many hits
            ensureHitsRead(lowerBound);
        } catch (InterruptedException e) {
            // Thread was interrupted; abort operation
            // and let client decide what to do
            Thread.currentThread().interrupt();
        }

        return hits.size() >= lowerBound;
    }

    public int hitsProcessedTotal() {
        try {
            // Probably not all hits have been seen yet. Collect them all.
            ensureAllHitsRead();
        } catch (InterruptedException e) {
            // Abort operation. Result may be wrong, but
            // interrupted results shouldn't be shown to user anyway.
            Thread.currentThread().interrupt();
        }
        return hits.size();
    }

    public int hitsProcessedSoFar() {
        return hits.size();
    }

    public int hitsCountedTotal() {
        try {
            ensureAllHitsRead();
        } catch (InterruptedException e) {
            // Abort operation. Result may be wrong, but
            // interrupted results shouldn't be shown to user anyway.
            Thread.currentThread().interrupt();
        }
        return hitsCounted;
    }

    public int docsProcessedTotal() {
        try {
            ensureAllHitsRead();
        } catch (InterruptedException e) {
            // Abort operation. Result may be wrong, but
            // interrupted results shouldn't be shown to user anyway.
            Thread.currentThread().interrupt();
        }
        return docsRetrieved;
    }

    public int docsCountedTotal() {
        try {
            ensureAllHitsRead();
        } catch (InterruptedException e) {
            // Abort operation. Result may be wrong, but
            // interrupted results shouldn't be shown to user anyway.
            Thread.currentThread().interrupt();
        }
        return docsCounted;
    }

    public int hitsCountedSoFar() {
        return hitsCounted;
    }

    public int docsCountedSoFar() {
        return docsCounted;
    }

    public int docsProcessedSoFar() {
        return docsRetrieved;
    }

    // Deriving other Hits / Results instances
    //--------------------------------------------------------------------
    
    public Hits sortedBy(HitProperty sortProp) {
        return sortProp.sortHits(this);
    }
    
    public Hits window(Hit hit) {
        try {
            ensureAllHitsRead();
        } catch (InterruptedException e) {
            // (should be detected by the client)
        }
        return window(hits.indexOf(hit), 1);
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
        return new Concordances(this, type, contextSize);
    }

    public Kwics kwics(ContextSize contextSize) {
        return new Kwics(this, contextSize);
    }

    /**
     * Get the raw list of hits.
     * 
     * Clients shouldn't use this. Used internally for certain performance-sensitive
     * operations like sorting.
     * 
     * The list will only contain whatever hits have been processed; if you want all the hits,
     * call ensureAllHitsRead(), size() or hitsProcessedTotal() first. 
     * 
     * @return the list of hits
     */
    public List<Hit> hitsList() {
        return hits;
    }

    /**
     * Check if we're done retrieving/counting hits.
     *
     * If you're retrieving hits in a background thread, call this method from
     * another thread to check if all hits have been processed.
     *
     * @return true iff all hits have been retrieved/counted.
     */
    public abstract boolean doneProcessingAndCounting();


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

}
