package nl.inl.blacklab.search.results;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.search.BooleanQuery.TooManyClauses;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.spans.SpanWeight;
import org.apache.lucene.search.spans.SpanWeight.Postings;
import org.apache.lucene.search.spans.Spans;

import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.resultproperty.HitPropValue;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.BlackLabIndexImpl;
import nl.inl.blacklab.search.Prioritizable;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.Span;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.BLSpans;
import nl.inl.blacklab.search.lucene.HitQueryContext;
import nl.inl.util.ThreadPriority;

public class Hits implements Iterable<Hit>, Prioritizable {

    private static final Logger logger = LogManager.getLogger(Hits.class);
    
    
    // Factory methods
    //--------------------------------------------------------------------

    /**
     * Construct an empty Hits object.
     *
     * @param searcher the searcher object
     * @param field field our hits are from
     * @param settings search settings, or null for default
     * @return hits found
     */
    public static Hits emptyList(BlackLabIndex searcher, AnnotatedField field, HitsSettings settings) {
        return fromList(searcher, field, (List<Hit>) null, settings);
    }

    /**
     * Make a wrapper Hits object for a list of Hit objects.
     *
     * Does not copy the list, but reuses it.
     *
     * @param searcher the searcher object
     * @param field field our hits are from
     * @param hits the list of hits to wrap, or null for empty Hits object
     * @param settings search settings, or null for default
     * @return hits found
     */
    public static Hits fromList(BlackLabIndex searcher, AnnotatedField field, List<Hit> hits, HitsSettings settings) {
        return new Hits(searcher, field, hits, settings);
    }

    /**
     * Construct a Hits object from a SpanQuery.
     *
     * @param searcher the searcher object
     * @param query the query to execute to get the hits
     * @param settings search settings
     * @return hits found
     */
    public static Hits fromSpanQuery(BlackLabIndex searcher, BLSpanQuery query, HitsSettings settings) {
        return new Hits(searcher, searcher.annotatedField(query.getField()), query, settings);
    }

    /**
     * Construct a Hits object from a Spans.
     *
     * Used for testing. Don't use this in applications, but construct a Hits object
     * from a SpanQuery, as it's more efficient.
     *
     * @param searcher the searcher object
     * @param field field our hits came from
     * @param source where to retrieve the Hit objects from
     * @param settings search settings
     * @return hits found
     */
    public static Hits fromSpans(BlackLabIndex searcher, AnnotatedField field, BLSpans source, HitsSettings settings) {
        return new Hits(searcher, field, source, settings);
    }

    // Hits object ids
    //--------------------------------------------------------------------

    /** Id the next Hits instance will get */
    private static int nextHitsObjId = 0;

    private synchronized static int getNextHitsObjId() {
        return nextHitsObjId++;
    }

    /** Unique id of this Hits instance */
    private final int hitsObjId = getNextHitsObjId();
    
    public int getHitsObjId() {
        return hitsObjId;
    }

    
    // Instance variables
    //--------------------------------------------------------------------
    
    // General stuff
    
    BlackLabIndex index;

    /**
     * Settings for retrieving hits.
     */
    HitsSettings settings;
    
    /**
     * The field these hits came from (will also be used as concordance field)
     */
    private AnnotatedField field;

    /**
     * Helper object for implementing query thread priority (making sure queries
     * don't hog the CPU for way too long).
     */
    ThreadPriority threadPriority;
    
    // Hit information

    /**
     * The hits.
     */
    protected List<Hit> hits;

    /** Context of our query; mostly used to keep track of captured groups. */
    private HitQueryContext hitQueryContext;

    /**
     * The captured groups, if we have any.
     */
    protected Map<Hit, Span[]> capturedGroups;
    
    // Low-level Lucene hit fetching

    /**
     * Our SpanQuery.
     */
    private BLSpanQuery spanQuery;

    /**
     * The SpanWeight for our SpanQuery, from which we can get the next Spans when
     * the current one's done.
     */
    private SpanWeight weight;

    /**
     * The LeafReaderContexts we should query in succession.
     */
    private List<LeafReaderContext> atomicReaderContexts;

    /**
     * What LeafReaderContext we're querying now.
     */
    private int atomicReaderContextIndex = -1;

    /**
     * Term contexts for the terms in the query.
     */
    private Map<Term, TermContext> termContexts;

    /**
     * docBase of the segment we're currently in
     */
    private int currentDocBase;

    /**
     * Our Spans object, which may not have been fully read yet.
     */
    private BLSpans currentSourceSpans;

    /**
     * Did we completely read our Spans object?
     */
    private boolean sourceSpansFullyRead = true;

    private Lock ensureHitsReadLock = new ReentrantLock();
    
    // Sort

    /**
     * The sort order, if we've sorted, or null if not.
     * 
     * Note that, after initial creation of the Hits object, sortOrder is immutable.
     */
    private Integer[] sortOrder;
    
    // Display

    // Stats

    /**
     * If true, we've stopped retrieving hits because there are more than the
     * maximum we've set.
     */
    private boolean maxHitsRetrieved = false;

    /**
     * If true, we've stopped counting hits because there are more than the maximum
     * we've set.
     */
    private boolean maxHitsCounted = false;

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

    /**
     * Document the previous hit was in, so we can count separate documents.
     */
    protected int previousHitDoc = -1;


    // Constructors
    //--------------------------------------------------------------------

    public Hits(BlackLabIndex searcher, AnnotatedField field, HitsSettings settings) {
        this.index = searcher;
        this.field = field;
        this.settings = settings == null ? searcher.hitsSettings() : settings;
        hitQueryContext = new HitQueryContext(); // to keep track of captured groups, etc.
    }
    
    /**
     * Make a wrapper Hits object for a list of Hit objects.
     *
     * Does not copy the list, but reuses it.
     *
     * @param searcher the searcher object
     * @param field field our hits came from
     * @param hits the list of hits to wrap
     * @param settings settings, or null for default
     */
    protected Hits(BlackLabIndex searcher, AnnotatedField field, List<Hit> hits, HitsSettings settings) {
        this(searcher, field, settings);
        this.hits = hits == null ? new ArrayList<>() : hits;
        hitsCounted = this.hits.size();
        int prevDoc = -1;
        docsRetrieved = docsCounted = 0;
        for (Hit h : this.hits) {
            if (h.doc() != prevDoc) {
                docsRetrieved++;
                docsCounted++;
                prevDoc = h.doc();
            }
        }
        threadPriority = new ThreadPriority();
    }

    /**
     * Construct a Hits object from a SpanQuery.
     *
     * @param searcher the searcher object
     * @param field field our hits came from
     * @param sourceQuery the query to execute to get the hits
     * @throws TooManyClauses if the query is overly broad (expands to too many
     *             terms)
     */
    private Hits(BlackLabIndex searcher, AnnotatedField field, BLSpanQuery sourceQuery, HitsSettings settings) throws TooManyClauses {
        this(searcher, field, (List<Hit>) null, settings);
        try {
            IndexReader reader = searcher.reader();
            if (BlackLabIndexImpl.isTraceQueryExecution())
                logger.debug("Hits(): optimize");
            BLSpanQuery optimize = ((BLSpanQuery) sourceQuery).optimize(reader);

            if (BlackLabIndexImpl.isTraceQueryExecution())
                logger.debug("Hits(): rewrite");
            spanQuery = optimize.rewrite(reader);

            //System.err.println(spanQuery);
            termContexts = new HashMap<>();
            Set<Term> terms = new HashSet<>();
            spanQuery = BLSpanQuery.ensureSortedUnique(spanQuery);
            if (BlackLabIndexImpl.isTraceQueryExecution())
                logger.debug("Hits(): createWeight");
            weight = spanQuery.createWeight(searcher.searcher(), false);
            weight.extractTerms(terms);
            threadPriority = new ThreadPriority();
            if (BlackLabIndexImpl.isTraceQueryExecution())
                logger.debug("Hits(): extract terms");
            for (Term term : terms) {
                try {
                    threadPriority.behave();
                } catch (InterruptedException e) {
                    // Taking too long, break it off.
                    // Not a very graceful way to do it... but at least it won't
                    // be stuck forever.
                    Thread.currentThread().interrupt(); // client can check this
                    throw new BlackLabException("Query matches too many terms; aborted.");
                }
                termContexts.put(term, TermContext.build(reader.getContext(), term));
            }

            currentSourceSpans = null;
            atomicReaderContexts = reader == null ? null : reader.leaves();
            atomicReaderContextIndex = -1;
        } catch (IOException e) {
            throw BlackLabException.wrap(e);
        }

        sourceSpansFullyRead = false;
        if (BlackLabIndexImpl.isTraceQueryExecution())
            logger.debug("Hits(): done");
    }

    /**
     * Construct a Hits object from a Spans.
     *
     * If possible, don't use this constructor, use the one that takes a SpanQuery,
     * as it's more efficient.
     *
     * Note that the Spans provided must be start-point sorted and contain unique
     * hits.
     *
     * @param searcher the searcher object
     * @param field field our hits came from
     * @param source where to retrieve the Hit objects from
     */
    private Hits(BlackLabIndex searcher, AnnotatedField field, BLSpans source, HitsSettings settings) {
        this(searcher, field, (List<Hit>) null, settings);

        currentSourceSpans = source;
        try {
            sourceSpansFullyRead = currentSourceSpans.nextDoc() != DocIdSetIterator.NO_MORE_DOCS;
        } catch (IOException e) {
            throw BlackLabException.wrap(e);
        }
    }

    /**
     * Construct a Hits object from an existing Hits object.
     *
     * The same hits list is reused. Context and sort order are not copied. All
     * other fields are.
     *
     * @param copyFrom the Hits object to copy
     * @param settings settings to override, or null to copy
     */
    private Hits(Hits copyFrom, HitsSettings settings) {
        this(copyFrom.index, copyFrom.field, settings == null ? copyFrom.settings : settings);
        try {
            copyFrom.ensureAllHitsRead();
        } catch (InterruptedException e) {
            // (should be detected by the client)
        }
        hits = copyFrom.hits;
        sourceSpansFullyRead = true;
        hitsCounted = copyFrom.countSoFarHitsCounted();
        docsRetrieved = copyFrom.countSoFarDocsRetrieved();
        docsCounted = copyFrom.countSoFarDocsCounted();
        previousHitDoc = copyFrom.previousHitDoc;
        
        copyMaxAndContextFrom(copyFrom);

        threadPriority = new ThreadPriority();
    }


    // Load balancing
    //--------------------------------------------------------------------

    /**
     * Set the thread priority level for this Hits object.
     *
     * Allows us to set a query to low-priority, or to (almost) pause it.
     *
     * @param level the desired priority level
     */
    @Override
    public void setPriorityLevel(ThreadPriority.Level level) {
        threadPriority.setPriorityLevel(level);
    }

    /**
     * Get the thread priority level for this Hits object.
     *
     * Can be normal, low-priority, or (almost) paused.
     *
     * @return the current priority level
     */
    @Override
    public ThreadPriority.Level getPriorityLevel() {
        return threadPriority.getPriorityLevel();
    }

    
    // Copying hits objects (and their relevant settings)
    //--------------------------------------------------------------------
    
    /**
     * Return a copy of this Hits object.
     *
     * NOTE: Why not use clone()/Cloneable? See
     * http://www.artima.com/intv/bloch13.html
     * 
     * @param settings settings to use, or null to copy settings too
     *
     * @return a copy of this Hits object
     */
    public Hits copy(HitsSettings settings) {
        return new Hits(this, settings);
    }

    /**
     * Copy maxHitsRetrieved/-Counted and hitQueryContext from another Hits object.
     *
     * NOTE: this should be phased out, and copy() or adapters should be used.
     *
     * @param copyFrom where to copy stuff from
     */
    public void copyMaxAndContextFrom(Hits copyFrom) {
        this.maxHitsRetrieved = copyFrom.maxHitsRetrieved();
        this.maxHitsCounted = copyFrom.maxHitsCounted();
        this.hitQueryContext = copyFrom.hitQueryContext();
    }
    

    // Deriving other Hits / Results instances
    //--------------------------------------------------------------------
    
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
    public Hits sortedBy(final HitProperty sortProp) {
        return sortedBy(sortProp, false);
    }

    /**
     * Get a sorted copy of these hits.
     *
     * Note that if the thread is interrupted during this, sort may return without
     * the hits actually being fully read and sorted. We don't want to add throws
     * declarations to our whole API, so we assume the calling method will check for
     * thread interruption if the application uses it.
     *
     * @param sortProp the hit property to sort on
     * @param reverseSort if true, sort in descending order
     * @return sorted hits
     */
    public Hits sortedBy(HitProperty sortProp, boolean reverseSort) {
        // Sort hits
        try {
            ensureAllHitsRead();
        } catch (InterruptedException e) {
            // Thread was interrupted; don't complete the operation but return
            // and let the caller detect and deal with the interruption.
            Thread.currentThread().interrupt();
            return this;
        }

        Hits hits = copy(null);
        sortProp = sortProp.copyWithHits(hits);
        
        // Make sure we have a sort order array of sufficient size
        if (hits.sortOrder == null || hits.sortOrder.length < hits.size()) {
            hits.sortOrder = new Integer[hits.size()];
        }
        // Fill the array with the original hit order (0, 1, 2, ...)
        int n = hits.size();
        for (int i = 0; i < n; i++)
            hits.sortOrder[i] = i;

        // If we need context, make sure we have it.
        List<Annotation> requiredContext = sortProp.needsContext();
        if (requiredContext != null)
            sortProp.setContexts(new Contexts(hits, requiredContext));

        // Perform the actual sort.
        Arrays.sort(hits.sortOrder, sortProp);

        if (reverseSort) {
            // Instead of creating a new Comparator that reverses the order of the
            // sort property (which adds an extra layer of indirection to each of the
            // O(n log n) comparisons), just reverse the hits now (which runs
            // in linear time).
            for (int i = 0; i < n / 2; i++) {
                hits.sortOrder[i] = hits.sortOrder[n - i - 1];
            }
        }
        return hits;
    }

    /**
     * Select only the hits where the specified property has the specified value.
     * 
     * @param property property to select on, e.g. "word left of hit"
     * @param value value to select on, e.g. 'the'
     * @return filtered hits
     */
    public Hits filteredBy(HitProperty property, HitPropValue value) {
        List<Annotation> requiredContext = property.needsContext();
        property.setContexts(new Contexts(this, requiredContext));

        List<Hit> filtered = new ArrayList<>();
        for (int i = 0; i < size(); i++) {
            if (property.get(i).equals(value))
                filtered.add(get(i));
        }
        Hits hits = new Hits(index, field, filtered, settings);
        hits.copyMaxAndContextFrom(this);
        return hits;
    }

    /**
     * Group these hits by a criterium (or several criteria).
     *
     * @param criteria the hit property to group on
     * @return a HitGroups object representing the grouped hits
     */
    public HitGroups groupedBy(final HitProperty criteria) {
        return ResultsGrouper.fromHits(this, criteria);
    }

    /**
     * Return a per-document view of these hits.
     *
     * @return the per-document view.
     */
    public DocResults perDocResults() {
        return DocResults.fromHits(index(), this);
    }

    /**
     * Count occurrences of context words around hit.
     *
     * Uses the default contents field for collocations, and the default sensitivity
     * settings.
     *
     * @return the frequency of each occurring token
     */
    public TermFrequencyList getCollocations() {
        return TermFrequencyList.collocations(this, null, null, true);
    }

    public synchronized TermFrequencyList getCollocations(Annotation annotation, QueryExecutionContext ctx, boolean sort) {
        return TermFrequencyList.collocations(this, annotation, ctx, sort);
    }
    
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
    public HitsWindow window(int first, int windowSize) {
        return new HitsWindow(this, first, windowSize, settings());
    }

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
     * @param settings settings to use, or null to inherit
     * @return the window
     */
    public HitsWindow window(int first, int windowSize, HitsSettings settings) {
        return new HitsWindow(this, first, windowSize, settings == null ? this.settings() : settings);
    }

    /**
     * Get a window with a single hit in it.
     * 
     * @param hit the hit we want (must be in this Hits object)
     * @return window
     */
    public HitsWindow window(Hit hit) {
        int i = hits.indexOf(hit);
        if (i < 0)
            throw new BlackLabException("Hit not found in hits list!");
        return window(i, 1);
    }
    
    // Settings, general stuff
    //--------------------------------------------------------------------

    /**
     * Returns the searcher object.
     *
     * @return the searcher object.
     */
    public BlackLabIndex index() {
        return index;
    }
    
    public AnnotatedField field() {
        return field;
    }

    public HitsSettings settings() {
        return settings;
    }

    private HitQueryContext hitQueryContext() {
        return hitQueryContext;
    }

    @Override
    public String toString() {
        return "Hits#" + hitsObjId + " (fullyRead=" + sourceSpansFullyRead + ", hits.size()=" + hits.size() + ")";
    }
    

    // Getting / iterating over the hits
    //--------------------------------------------------------------------

    /**
     * Return an iterator over these hits.
     *
     * The order is the sorted order, not the original order. Use
     * hitsInOriginalOrder() to iterate in the original order.
     *
     * @return the iterator
     */
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
                    return hits.get(sortOrder == null ? index : sortOrder[index]);
                }
                throw new NoSuchElementException();
            }
        
            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        
        };
    }
    
    /**
     * Return the specified hit number, based on the order they were originally
     * found (not the sorted order).
     *
     * @param i index of the desired hit
     * @return the hit, or null if it's beyond the last hit
     */
    public Hit getByOriginalOrder(int i) {
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

    /**
     * Return the specified hit.
     *
     * @param i index of the desired hit
     * @return the hit, or null if it's beyond the last hit
     */
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
        return hits.get(sortOrder == null ? i : sortOrder[i]);
    }
    
    /**
     * Convenience method to get all hits in a single doc from a larger hitset.
     *
     * Don't use this for grouping or per-document results as it's relatively
     * inefficient.
     *
     * @param docid the doc id to get hits for
     * @return the list of hits in this doc (if any)
     */
    public Hits getHitsInDoc(int docid) {
        try {
            ensureAllHitsRead();
        } catch (InterruptedException e) {
            // Interrupted. Just return no hits;
            // client should detect thread was interrupted if it
            // wants to use background threads.
            Thread.currentThread().interrupt();
            return Hits.emptyList(index, field, settings);
        }
        List<Hit> hitsInDoc = new ArrayList<>();
        for (Hit hit : hits) {
            if (hit.doc() == docid)
                hitsInDoc.add(hit);
        }
        Hits result = Hits.fromList(index, field, hitsInDoc, settings);
        result.copyMaxAndContextFrom(this);
        return result;
    }


    // Captured groups
    //--------------------------------------------------------------------

    /**
     * Get the captured group name information.
     *
     * @return the captured group names, in index order
     */
    public List<String> getCapturedGroupNames() {
        if (hitQueryContext == null)
            return null;
        return hitQueryContext.getCapturedGroupNames();
    }

    public boolean hasCapturedGroups() {
        return capturedGroups != null;
    }

    /**
     * Get the captured group information for this hit, if any.
     *
     * The names of the captured groups can be obtained through the
     * getCapturedGroupNames() method.
     *
     * @param hit the hit to get captured group information for
     * @return the captured group information, or null if none
     */
    public Span[] getCapturedGroups(Hit hit) {
        if (capturedGroups == null)
            return null;
        return capturedGroups.get(hit);
    }

    /**
     * Get the captured group information in map form.
     *
     * Relatively slow; use getCapturedGroups() and getCapturedGroupNames() for a
     * faster alternative.
     *
     * @param hit hit to get the captured group map for
     * @return the captured group information map
     */
    public Map<String, Span> getCapturedGroupMap(Hit hit) {
        if (capturedGroups == null)
            return null;
        Map<String, Span> result = new TreeMap<>(); // TreeMap to maintain group ordering
        List<String> names = getCapturedGroupNames();
        Span[] groups = capturedGroups.get(hit);
        for (int i = 0; i < names.size(); i++) {
            result.put(names.get(i), groups[i]);
        }
        return result;
    }

    
    // Hits fetching
    //--------------------------------------------------------------------

    void ensureAllHitsRead() throws InterruptedException {
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
    void ensureHitsRead(int number) throws InterruptedException {
        // Prevent locking when not required
        if (sourceSpansFullyRead || (number >= 0 && hits.size() >= number))
            return;

        while (!ensureHitsReadLock.tryLock()) {
            /*
             * Another thread is already counting, we don't want to straight up block until it's done
             * as it might be counting/retrieving all results, while we might only want trying to retrieve a small fraction
             * So instead poll our own state, then if we're still missing results after that just count them ourselves
             */
            Thread.sleep(50);
            if (sourceSpansFullyRead || (number >= 0 && hits.size() >= number))
                return;
        }

        boolean readAllHits = number < 0;
        try {
            int maxHitsToCount = settings.maxHitsToCount();
            int maxHitsToRetrieve = settings.maxHitsToRetrieve();
            while (readAllHits || hits.size() < number) {

                // Don't hog the CPU, don't take too long
                threadPriority.behave();

                // Stop if we're at the maximum number of hits we want to count
                if (maxHitsToCount >= 0 && hitsCounted >= maxHitsToCount) {
                    maxHitsCounted = true;
                    break;
                }

                // Get the next hit from the spans, moving to the next
                // segment when necessary.
                while (true) {
                    while (currentSourceSpans == null) {
                        // Exhausted (or not started yet); get next segment spans.

                        if (spanQuery == null) {
                            // We started from a Spans, not a SpanQuery. We're done now.
                            // (only used in deprecated methods or while testing)
                            return;
                        }

                        atomicReaderContextIndex++;
                        if (atomicReaderContexts != null && atomicReaderContextIndex >= atomicReaderContexts.size()) {
                            sourceSpansFullyRead = true;
                            return;
                        }
                        if (atomicReaderContexts != null) {
                            // Get the atomic reader context and get the next Spans from it.
                            LeafReaderContext context = atomicReaderContexts.get(atomicReaderContextIndex);
                            currentDocBase = context.docBase;
                            BLSpans spans = (BLSpans) weight.getSpans(context, Postings.OFFSETS);
                            currentSourceSpans = spans; //BLSpansWrapper.optWrapSortUniq(spans);
                        } else {
                            // TESTING
                            currentDocBase = 0;
                            if (atomicReaderContextIndex > 0) {
                                sourceSpansFullyRead = true;
                                return;
                            }
                            BLSpans spans = (BLSpans) weight.getSpans(null, Postings.OFFSETS);
                            currentSourceSpans = spans; //BLSpansWrapper.optWrapSortUniq(spans);
                        }

                        if (currentSourceSpans != null) {
                            // Update the hit query context with our new spans,
                            // and notify the spans of the hit query context
                            // (TODO: figure out if we need to call setHitQueryContext()
                            //    for each segment or not; if it's just about capture groups
                            //    registering themselves, we only need that for the first Spans.
                            //    But it's probably required for backreferences, etc. anyway,
                            //    and there won't be that many segments, so it's probably ok)
                            hitQueryContext.setSpans(currentSourceSpans);
                            currentSourceSpans.setHitQueryContext(hitQueryContext); // let captured groups register themselves
                            if (capturedGroups == null && hitQueryContext.numberOfCapturedGroups() > 0) {
                                capturedGroups = new HashMap<>();
                            }

                            int doc = currentSourceSpans.nextDoc();
                            if (doc == DocIdSetIterator.NO_MORE_DOCS)
                                currentSourceSpans = null; // no matching docs in this segment, try next
                        }
                    }

                    // Advance to next hit
                    int start = currentSourceSpans.nextStartPosition();
                    if (start == Spans.NO_MORE_POSITIONS) {
                        int doc = currentSourceSpans.nextDoc();
                        if (doc != DocIdSetIterator.NO_MORE_DOCS) {
                            // Go to first hit in doc
                            start = currentSourceSpans.nextStartPosition();
                        } else {
                            // This one is exhausted; go to the next one.
                            currentSourceSpans = null;
                        }
                    }
                    if (currentSourceSpans != null) {
                        // We're at the next hit.
                        break;
                    }
                }

                // Count the hit and add it (unless we've reached the maximum number of hits we
                // want)
                hitsCounted++;
                int hitDoc = currentSourceSpans.docID() + currentDocBase;
                if (hitDoc != previousHitDoc) {
                    docsCounted++;
                    if (!maxHitsRetrieved)
                        docsRetrieved++;
                    previousHitDoc = hitDoc;
                }
                if (!maxHitsRetrieved) {
                    Hit hit = currentSourceSpans.getHit();
                    Hit offsetHit = HitStored.create(hit.doc() + currentDocBase, hit.start(), hit.end());
                    if (capturedGroups != null) {
                        Span[] groups = new Span[hitQueryContext.numberOfCapturedGroups()];
                        hitQueryContext.getCapturedGroups(groups);
                        capturedGroups.put(offsetHit, groups);
                    }
                    hits.add(offsetHit);
                    maxHitsRetrieved = maxHitsToRetrieve >= 0 && hits.size() >= maxHitsToRetrieve;
                }
            }
        } catch (InterruptedException e) {
            maxHitsRetrieved = maxHitsCounted = true; // we've stopped retrieving/counting
            throw e;
        } catch (IOException e) {
            throw BlackLabException.wrap(e);
        } finally {
            ensureHitsReadLock.unlock();
        }
    }

    public ThreadPriority getThreadPriority() {
        return threadPriority;
    }
    
    // Stats about hits fetching
    // --------------------------------------------------------------------------
    
    private ResultsNumber resultsNumberHitsProcessed = new ResultsNumber() {
        @Override
        public int total() {
            return size();
        }

        @Override
        public boolean atLeast(int lowerBound) {
            return sizeAtLeast(lowerBound);
        }

        @Override
        public int soFar() {
            return countSoFarHitsRetrieved();
        }

        @Override
        public boolean done() {
            return doneFetchingHits();
        }

        @Override
        public boolean exceededMaximum() {
            return maxHitsRetrieved;
        }

        @Override
        public int maximum() {
            return settings.maxHitsToRetrieve();
        }
    };
    
    ResultsNumber resultsNumberHitsCounted = new ResultsNumber() {
        @Override
        public int total() {
            return totalSize();
        }

        @Override
        public boolean atLeast(int lowerBound) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int soFar() {
            return countSoFarHitsCounted();
        }

        @Override
        public boolean done() {
            return doneFetchingHits();
        }

        @Override
        public boolean exceededMaximum() {
            return maxHitsCounted;
        }

        @Override
        public int maximum() {
            return settings.maxHitsToCount();
        }
    };
    
    private ResultsNumber resultsNumberDocsProcessed = new ResultsNumber() {
        @Override
        public int total() {
            return numberOfDocs();
        }

        @Override
        public boolean atLeast(int lowerBound) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int soFar() {
            return countSoFarDocsRetrieved();
        }

        @Override
        public boolean done() {
            return doneFetchingHits();
        }

        @Override
        public boolean exceededMaximum() {
            return maxHitsRetrieved;
        }

        @Override
        public int maximum() {
            throw new UnsupportedOperationException();
        }
    };
    
    private ResultsNumber resultsNumberDocsCounted = new ResultsNumber() {
        @Override
        public int total() {
            if (done())
                return soFar();
            throw new UnsupportedOperationException("Cannot return total docs until all hits have been fetched");
        }

        @Override
        public boolean atLeast(int lowerBound) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int soFar() {
            return countSoFarDocsCounted();
        }

        @Override
        public boolean done() {
            return doneFetchingHits();
        }

        @Override
        public boolean exceededMaximum() {
            return maxHitsCounted;
        }

        @Override
        public int maximum() {
            throw new UnsupportedOperationException();
        }
    };
    
    private ResultsStats hitStats = new ResultsStats() {
        @Override
        public ResultsNumber processed() {
            return resultsNumberHitsProcessed;
        }

        @Override
        public ResultsNumber counted() {
            return resultsNumberHitsCounted;
        }
    };
    
    private ResultsStats docStats = new ResultsStats() {
        @Override
        public ResultsNumber processed() {
            return resultsNumberDocsProcessed;
        }

        @Override
        public ResultsNumber counted() {
            return resultsNumberDocsCounted;
        }
    };
    
    private ResultsStatsHitsDocs hitsDocsStats = new ResultsStatsHitsDocs() {
        @Override
        public ResultsStats hits() {
            return hitStats;
        }

        @Override
        public ResultsStats docs() {
            return docStats;
        }
        
    };
    
    public ResultsStatsHitsDocs stats() {
        return hitsDocsStats;
    }
    
    /**
     * Determines if there are at least a certain number of hits
     *
     * This may be used if we don't want to process all hits (which may be a lot)
     * but we do need to know something about the size of the result set (such as
     * for paging).
     *
     * Note that this method applies to the hits retrieved, which may be less than
     * the total number of hits (depending on maxHitsToRetrieve).
     *
     * @param lowerBound the number we're testing against
     *
     * @return true if the size of this set is at least lowerBound, false otherwise.
     */
    public boolean sizeAtLeast(int lowerBound) {
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

    /**
     * Return the number of hits available.
     *
     * Note that this method applies to the hits retrieved, which may be less than
     * the total number of hits (depending on maxHitsToRetrieve). Use totalSize() to
     * find the total hit count (which may also be limited depending on
     * maxHitsToCount).
     *
     * @return the number of hits available
     */
    public int size() {
        try {
            // Probably not all hits have been seen yet. Collect them all.
            ensureAllHitsRead();
        } catch (InterruptedException e) {
            // Abort operation. Result may be wrong, but
            // interrupted results shouldn't be shown to user anyway.
            maxHitsCounted = true; // indicate that we've stopped counting
            Thread.currentThread().interrupt();
        }
        return hits.size();
    }

    /**
     * Return the total number of hits.
     *
     * NOTE: Depending on maxHitsToRetrieve, hit retrieval may stop before all hits
     * are seen. We do keep counting hits though (until we reach maxHitsToCount, or
     * that value is negative). This method returns our total hit count. Some of
     * these hits may not be available.
     *
     * @return the total hit count
     */
    public int totalSize() {
        try {
            ensureAllHitsRead();
        } catch (InterruptedException e) {
            // Abort operation. Result may be wrong, but
            // interrupted results shouldn't be shown to user anyway.
            Thread.currentThread().interrupt();
        }
        return hitsCounted;
    }

    /**
     * Return the number of documents in the hits we've retrieved.
     *
     * @return the number of documents.
     */
    public int numberOfDocs() {
        try {
            ensureAllHitsRead();
        } catch (InterruptedException e) {
            // Abort operation. Result may be wrong, but
            // interrupted results shouldn't be shown to user anyway.
            Thread.currentThread().interrupt();
        }
        return docsRetrieved;
    }

    /**
     * Return the total number of documents in all hits. This counts documents even
     * in hits that are not stored, only counted.
     *
     * @return the total number of documents.
     */
    public int totalNumberOfDocs() {
        try {
            ensureAllHitsRead();
        } catch (InterruptedException e) {
            // Abort operation. Result may be wrong, but
            // interrupted results shouldn't be shown to user anyway.
            Thread.currentThread().interrupt();
        }
        return docsCounted;
    }

    /**
     * Return the number of hits counted so far.
     *
     * If you're retrieving hit in a background thread, call this method from
     * another thread to get an update of the count so far.
     *
     * @return the current total hit count
     */
    public int countSoFarHitsCounted() {
        return hitsCounted;
    }

    /**
     * Return the number of hits retrieved so far.
     *
     * If you're retrieving hits in a background thread, call this method from
     * another thread to get an update of the count so far.
     *
     * @return the current total hit count
     */
    public int countSoFarHitsRetrieved() {
        return hits.size();
    }

    /**
     * Return the number of documents counted so far.
     *
     * If you're retrieving hit in a background thread, call this method from
     * another thread to get an update of the count so far.
     *
     * @return the current total hit count
     */
    public int countSoFarDocsCounted() {
        return docsCounted;
    }

    /**
     * Return the number of documents retrieved so far.
     *
     * If you're retrieving hits in a background thread, call this method from
     * another thread to get an update of the count so far.
     *
     * @return the current total hit count
     */
    public int countSoFarDocsRetrieved() {
        return docsRetrieved;
    }

    /**
     * Check if we're done retrieving/counting hits.
     *
     * If you're retrieving hits in a background thread, call this method from
     * another thread to check if all hits have been processed.
     *
     * @return true iff all hits have been retrieved/counted.
     */
    public boolean doneFetchingHits() {
        return sourceSpansFullyRead || maxHitsCounted;
    }

    /**
     * Did we stop retrieving hits because we reached the maximum?
     * 
     * @return true if we reached the maximum and stopped retrieving hits
     */
    public boolean maxHitsRetrieved() {
        return maxHitsRetrieved;
    }

    /**
     * Did we stop counting hits because we reached the maximum?
     * 
     * @return true if we reached the maximum and stopped counting hits
     */
    public boolean maxHitsCounted() {
        return maxHitsCounted;
    }

    // Hits display
    //--------------------------------------------------------------------

    public Concordances concordances(int contextSize) {
        return new Concordances(this, contextSize);
    }
    
    public Kwics kwics(int contextSize) {
        return new Kwics(this, contextSize);
    }
    
    

}
