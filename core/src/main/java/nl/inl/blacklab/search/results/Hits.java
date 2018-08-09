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
import org.eclipse.collections.api.map.primitive.MutableIntIntMap;
import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.eclipse.collections.api.tuple.primitive.IntIntPair;
import org.eclipse.collections.impl.factory.primitive.IntIntMaps;
import org.eclipse.collections.impl.factory.primitive.IntObjectMaps;

import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.forwardindex.ForwardIndex;
import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.resultproperty.HitPropValue;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.BlackLabIndexImpl;
import nl.inl.blacklab.search.Concordance;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.Doc;
import nl.inl.blacklab.search.Kwic;
import nl.inl.blacklab.search.Prioritizable;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.Span;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.Field;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.BLSpans;
import nl.inl.blacklab.search.lucene.HitQueryContext;
import nl.inl.util.StringUtil;
import nl.inl.util.ThreadPriority;
import nl.inl.util.XmlHighlighter;

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
        hitDisplay.kwics = copyFrom.hitDisplay().kwics;
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
        return getCollocations(null, null, true);
    }

    /**
     * Count occurrences of context words around hit.
     *
     * @param annotation annotation to use for the collocations, or null if default
     * @param ctx query execution context, containing the sensitivity settings
     * @param sort whether or not to sort the list by descending frequency
     *
     * @return the frequency of each occurring token
     */
    public synchronized TermFrequencyList getCollocations(Annotation annotation, QueryExecutionContext ctx, boolean sort) {
        if (annotation == null)
            annotation = index.mainAnnotatedField().annotations().main();
        
        // TODO: use sensitivity settings
//        if (ctx == null)
//            ctx = searcher.defaultExecutionContext(settings().concordanceField());
//        ctx = ctx.withAnnotation(annotation);
        
        Contexts contexts = new Contexts(this, Arrays.asList(annotation));
        MutableIntIntMap coll = IntIntMaps.mutable.empty();
        for (int j = 0; j < hits.size(); j++) {
            int[] context = contexts.getContext(j);

            // Count words
            int contextHitStart = context[Contexts.CONTEXTS_HIT_START_INDEX];
            int contextRightStart = context[Contexts.CONTEXTS_RIGHT_START_INDEX];
            int contextLength = context[Contexts.CONTEXTS_LENGTH_INDEX];
            int indexInContent = Contexts.CONTEXTS_NUMBER_OF_BOOKKEEPING_INTS;
            for (int i = 0; i < contextLength; i++, indexInContent++) {
                if (i >= contextHitStart && i < contextRightStart)
                    continue; // don't count words in hit itself, just around [option..?]
                int w = context[indexInContent];
                int n;
                if (!coll.contains(w))
                    n = 1;
                else
                    n = coll.get(w) + 1;
                coll.put(w, n);
            }
        }

        // Get the actual words from the sort positions
        MatchSensitivity sensitivity = index.defaultMatchSensitivity();
        Terms terms = index.forwardIndex(contexts.getContextAnnotations().get(0)).terms();
        Map<String, Integer> wordFreq = new HashMap<>();
        for (IntIntPair e : coll.keyValuesView()) {
            int key = e.getOne();
            int value = e.getTwo();
            String word = terms.get(key);
            if (!sensitivity.isDiacriticsSensitive()) {
                word = StringUtil.stripAccents(word);
            }
            if (!sensitivity.isCaseSensitive()) {
                word = word.toLowerCase();
            }
            // Note that multiple ids may map to the same word (because of sensitivity settings)
            // Here, those groups are merged.
            Integer n = wordFreq.get(word);
            if (n == null) {
                n = 0;
            }
            n += value;
            wordFreq.put(word, n);
        }

        // Transfer from map to list
        return new TermFrequencyList(wordFreq, sort);
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

    
    // Hits display
    //--------------------------------------------------------------------

    private HitDisplay hitDisplay = new HitDisplay();
    
    public HitDisplay hitDisplay() {
        return hitDisplay;
    }
    
    public class HitDisplay {
        
        /**
         * The KWIC data, if it has been retrieved.
         *
         * NOTE: this will always be null if not all the hits have been retrieved.
         */
        private Map<Hit, Kwic> kwics;

        /**
         * The concordances, if they have been retrieved.
         *
         * NOTE: when making concordances from the forward index, this will always be
         * null, because Kwics will be used internally. This is only used when making
         * concordances from the content store (the old default).
         */
        private Map<Hit, Concordance> concordances;
        
        /**
         * Return the concordance for the specified hit.
         *
         * The first call to this method will fetch the concordances for all the hits in
         * this Hits object. So make sure to select an appropriate HitsWindow first:
         * don't call this method on a Hits set with >1M hits unless you really want to
         * display all of them in one go.
         *
         * @param h the hit
         * @return concordance for this hit
         */
        public Concordance getConcordance(Hit h) {
            return getConcordance(h, settings().contextSize());
        }

        /**
         * Return the KWIC for the specified hit.
         *
         * The first call to this method will fetch the KWICs for all the hits in this
         * Hits object. So make sure to select an appropriate HitsWindow first: don't
         * call this method on a Hits set with >1M hits unless you really want to
         * display all of them in one go.
         *
         * @param h the hit
         * @return KWIC for this hit
         */
        public Kwic getKwic(Hit h) {
            return getKwic(h, settings().contextSize());
        }

        /**
         * Retrieve a single KWIC (KeyWord In Context). Only use if you need a larger
         * snippet around a single hit. If you need KWICs for a set of hits, just
         * instantiate a HitsWindow and call getKwic() on that; it will fetch all KWICs
         * in the window in a batch, which is more efficient.
         *
         * @param field field to use for building the KWIC
         * @param hit the hit for which we want a KWIC
         * @param contextSize the desired number of words around the hit
         * @return the KWIC
         */
        public Kwic getKwic(AnnotatedField field, Hit hit, int contextSize) {
            List<Hit> oneHit = Arrays.asList(hit);
            Hits h = new Hits(index, index.mainAnnotatedField(), oneHit, settings);
            h.copyMaxAndContextFrom(Hits.this); // concordance type, etc.
            Map<Hit, Kwic> oneConc = h.hitDisplay().retrieveKwics(contextSize, field);
            return oneConc.get(hit);
        }

        /**
         * Get a KWIC with a custom context size.
         *
         * Don't call this directly for displaying a list of results. In that case, just
         * instantiate a HitsWindow, call setContextSize() on it to set a default
         * context size and call getKwic(Hit) for each hit. That's more efficient if
         * you're dealing with many hits.
         *
         * This method is mostly just for getting a larger snippet around a single hit.
         *
         * @param h the hit
         * @param contextSize the context size for this KWIC (only use if you want a
         *            different one than the preset preference)
         * @return KWIC for this hit
         */
        public synchronized Kwic getKwic(Hit h, int contextSize) {
            if (contextSize != settings().contextSize()) {
                // Different context size than the default for the whole set;
                // We probably want to show a hit with a larger snippet around it
                // (say, 50 words or so). Don't clobber the context of the other
                // hits, just fetch this snippet separately.
                return getKwic(field(), h, contextSize);
            }

            // Default context size. Read all hits and find concordances for all of them
            // in batch.
            try {
                ensureAllHitsRead();
            } catch (InterruptedException e) {
                // Thread was interrupted. Just go ahead with the hits we did
                // get, so at least we can return a valid concordance object and
                // not break the calling method. It is responsible for checking
                // for thread interruption (only some applications use this at all,
                // so throwing exceptions from all methods is too inconvenient)
                Thread.currentThread().interrupt();
            }
            if (kwics == null) {
                findKwics(); // just try to find the default concordances
            }
            Kwic kwic = kwics.get(h);
            if (kwic == null)
                throw new BlackLabException("KWIC for hit not found: " + h);
            return kwic;
        }

        /**
         * Retrieve a single concordance. Only use if you need a larger snippet around a
         * single hit. If you need concordances for a set of hits, just instantiate a
         * HitsWindow and call getConcordance() on that; it will fetch all concordances
         * in the window in a batch, which is more efficient.
         *
         * @param field field to use for building the concordance
         * @param hit the hit for which we want a concordance
         * @param contextSize the desired number of words around the hit
         * @return the concordance
         */
        public synchronized Concordance getConcordance(AnnotatedField field, Hit hit, int contextSize) {
            List<Hit> oneHit = Arrays.asList(hit);
            Hits h = new Hits(index, index.mainAnnotatedField(), oneHit, settings);
            h.copyMaxAndContextFrom(Hits.this); // concordance type, etc.
            if (settings().concordanceType() == ConcordanceType.FORWARD_INDEX) {
                Map<Hit, Kwic> oneKwic = h.hitDisplay().retrieveKwics(contextSize, field);
                return oneKwic.get(hit).toConcordance();
            }
            Map<Hit, Concordance> oneConc = h.hitDisplay().retrieveConcordancesFromContentStore(contextSize, field);
            return oneConc.get(hit);
        }

        /**
         * Get a concordance with a custom context size.
         *
         * Don't call this directly for displaying a list of results. In that case, just
         * instantiate a HitsWindow, call setContextSize() on it to set a default
         * context size and call getConcordance(Hit) for each hit. That's more efficient
         * if you're dealing with many hits.
         *
         * This method is mostly just for getting a larger snippet around a single hit.
         *
         * @param h the hit
         * @param contextSize the context size for this concordance (only use if you
         *            want a different one than the preset preference)
         * @return concordance for this hit
         */
        public synchronized Concordance getConcordance(Hit h, int contextSize) {
            if (settings().concordanceType() == ConcordanceType.FORWARD_INDEX)
                return getKwic(h, contextSize).toConcordance();

            if (contextSize != settings().contextSize()) {
                // Different context size than the default for the whole set;
                // We probably want to show a hit with a larger snippet around it
                // (say, 50 words or so). Don't clobber the context of the other
                // hits, just fetch this snippet separately.
                return getConcordance(field(), h, contextSize);
            }

            // Default context size. Read all hits and find concordances for all of them
            // in batch.
            try {
                ensureAllHitsRead();
            } catch (InterruptedException e) {
                // Thread was interrupted. Just go ahead with the hits we did
                // get, so at least we can return a valid concordance object and
                // not break the calling method. It is responsible for checking
                // for thread interruption (only some applications use this at all,
                // so throwing exceptions from all methods is too inconvenient)
                Thread.currentThread().interrupt();
            }
            if (concordances == null) {
                findConcordances(); // just try to find the default concordances
            }
            Concordance conc = concordances.get(h);
            if (conc == null)
                throw new BlackLabException("Concordance for hit not found: " + h);
            return conc;
        }

        /**
         * Retrieve concordances for the hits.
         *
         * You shouldn't have to call this manually, as it's automatically called when
         * you call getConcordance() for the first time.
         */
        private synchronized void findConcordances() {
            if (settings.concordanceType() == ConcordanceType.FORWARD_INDEX) {
                findKwics();
                return;
            }

            try {
                ensureAllHitsRead();
            } catch (InterruptedException e) {
                // Thread was interrupted. Just go ahead with the hits we did
                // get, so at least we'll have valid concordances.
                Thread.currentThread().interrupt();
            }
            // Make sure we don't have the desired concordances already
            if (concordances != null) {
                return;
            }

            // Get the concordances
            concordances = retrieveConcordancesFromContentStore(settings().contextSize(), field());
        }

        /**
         * Retrieves the concordance information (left, hit and right context) for a
         * number of hits in the same document from the ContentStore.
         *
         * NOTE1: it is assumed that all hits in this Hits object are in the same
         * document!
         * 
         * @param field field to make conc for
         * @param wordsAroundHit number of words left and right of hit to fetch
         * @param conc where to add the concordances
         * @param hl
         */
        private synchronized void makeConcordancesSingleDocContentStore(Field field, int wordsAroundHit,
                Map<Hit, Concordance> conc,
                XmlHighlighter hl) {
            if (hits.isEmpty())
                return;
            Doc doc = index.doc(hits.get(0).doc());
            int arrayLength = hits.size() * 2;
            int[] startsOfWords = new int[arrayLength];
            int[] endsOfWords = new int[arrayLength];

            // Determine the first and last word of the concordance, as well as the
            // first and last word of the actual hit inside the concordance.
            int startEndArrayIndex = 0;
            for (Hit hit : hits) {
                int hitStart = hit.start();
                int hitEnd = hit.end() - 1;

                int start = hitStart - wordsAroundHit;
                if (start < 0)
                    start = 0;
                int end = hitEnd + wordsAroundHit;

                startsOfWords[startEndArrayIndex] = start;
                startsOfWords[startEndArrayIndex + 1] = hitStart;
                endsOfWords[startEndArrayIndex] = hitEnd;
                endsOfWords[startEndArrayIndex + 1] = end;

                startEndArrayIndex += 2;
            }

            // Get the relevant character offsets (overwrites the startsOfWords and endsOfWords
            // arrays)
            doc.getCharacterOffsets(field, startsOfWords, endsOfWords, true);

            // Make all the concordances
            List<Concordance> newConcs = doc.makeConcordancesFromContentStore(field, startsOfWords, endsOfWords, hl);
            for (int i = 0; i < hits.size(); i++) {
                conc.put(hits.get(i), newConcs.get(i));
            }
        }

        /**
         * Generate concordances from content store (slower).
         *
         * @param contextSize how many words around the hit to retrieve
         * @param fieldName field to use for building concordances
         * @return the concordances
         */
        private Map<Hit, Concordance> retrieveConcordancesFromContentStore(int contextSize, AnnotatedField field) {
            XmlHighlighter hl = new XmlHighlighter(); // used to make fragments well-formed
            hl.setUnbalancedTagsStrategy(index.defaultUnbalancedTagsStrategy());
            // Group hits per document
            MutableIntObjectMap<List<Hit>> hitsPerDocument = IntObjectMaps.mutable.empty();
            for (Hit key : hits) {
                List<Hit> hitsInDoc = hitsPerDocument.get(key.doc());
                if (hitsInDoc == null) {
                    hitsInDoc = new ArrayList<>();
                    hitsPerDocument.put(key.doc(), hitsInDoc);
                }
                hitsInDoc.add(key);
            }
            Map<Hit, Concordance> conc = new HashMap<>();
            for (List<Hit> l : hitsPerDocument.values()) {
                Hits hitsInThisDoc = new Hits(index, field, l, settings);
                hitsInThisDoc.copyMaxAndContextFrom(Hits.this);
                hitsInThisDoc.hitDisplay().makeConcordancesSingleDocContentStore(field, contextSize, conc, hl);
            }
            return conc;
        }

        
        /**
         * Retrieve KWICs for the hits.
         *
         * You shouldn't have to call this manually, as it's automatically called when
         * you call getKwic() for the first time.
         *
         */
        private synchronized void findKwics() {
            try {
                ensureAllHitsRead();
            } catch (InterruptedException e) {
                // Thread was interrupted. Just go ahead with the hits we did
                // get, so at least we'll have valid concordances.
                Thread.currentThread().interrupt();
            }
            // Make sure we don't have the desired concordances already
            if (kwics != null) {
                return;
            }

            // Get the concordances
            kwics = retrieveKwics(settings().contextSize(), field());
        }

        /**
         * Retrieve KWICs for a (sub)list of hits.
         *
         * KWICs are the hit words 'centered' with a certain number of context words
         * around them.
         *
         * The size of the left and right context (in words) may be set using
         * Searcher.setConcordanceContextSize().
         *
         * @param contextSize how many words around the hit to retrieve
         * @param fieldName field to use for building KWICs
         *
         * @return the KWICs
         */
        private Map<Hit, Kwic> retrieveKwics(int contextSize, AnnotatedField field) {
            // Group hits per document
            MutableIntObjectMap<List<Hit>> hitsPerDocument = IntObjectMaps.mutable.empty();
            for (Hit key: Hits.this) {
                List<Hit> hitsInDoc = hitsPerDocument.get(key.doc());
                if (hitsInDoc == null) {
                    hitsInDoc = new ArrayList<>();
                    hitsPerDocument.put(key.doc(), hitsInDoc);
                }
                hitsInDoc.add(key);
            }

            // All FIs except word and punct are attributes
            Map<Annotation, ForwardIndex> attrForwardIndices = new HashMap<>();
            for (Annotation annotation: field.annotations()) {
                if (annotation.hasForwardIndex() && !annotation.name().equals(Kwic.DEFAULT_CONC_WORD_PROP) && !annotation.name().equals(Kwic.DEFAULT_CONC_PUNCT_PROP)) {
                    attrForwardIndices.put(annotation, index.forwardIndex(annotation));
                }
            }
            ForwardIndex wordForwardIndex = index.forwardIndex(field.annotations().get(Kwic.DEFAULT_CONC_WORD_PROP));
            ForwardIndex punctForwardIndex = index.forwardIndex(field.annotations().get(Kwic.DEFAULT_CONC_PUNCT_PROP));
            Map<Hit, Kwic> conc1 = new HashMap<>();
            for (List<Hit> l : hitsPerDocument.values()) {
                Contexts.makeKwicsSingleDocForwardIndex(l, wordForwardIndex, punctForwardIndex, attrForwardIndices, contextSize, conc1);
            }
            return conc1;
        }
        
        
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
            // Thread was interrupted; don't complete the operation but return
            // and let the caller detect and deal with the interruption.
            // Returned value is probably not the correct total number of hits,
            // but will not cause any crashes. The thread was interrupted anyway,
            // the value should never be presented to the user.
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
            // Thread was interrupted; don't complete the operation but return
            // and let the caller detect and deal with the interruption.
            // Returned value is probably not the correct total number of hits,
            // but will not cause any crashes. The thread was interrupted anyway,
            // the value should never be presented to the user.
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
            // Thread was interrupted; don't complete the operation but return
            // and let the caller detect and deal with the interruption.
            // Returned value is probably not the correct total number of hits,
            // but will not cause any crashes. The thread was interrupted anyway,
            // the value should never be presented to the user.
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
            // Thread was interrupted; don't complete the operation but return
            // and let the caller detect and deal with the interruption.
            // Returned value is probably not the correct total number of hits,
            // but will not cause any crashes. The thread was interrupted anyway,
            // the value should never be presented to the user.
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

    public ThreadPriority getThreadPriority() {
        return threadPriority;
    }

}
