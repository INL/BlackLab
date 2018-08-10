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

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.spans.SpanWeight;
import org.apache.lucene.search.spans.SpanWeight.Postings;
import org.apache.lucene.search.spans.Spans;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.WildcardTermTooBroad;
import nl.inl.blacklab.resultproperty.HitPropValue;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.BlackLabIndexImpl;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.Span;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.BLSpans;
import nl.inl.blacklab.search.lucene.HitQueryContext;
import nl.inl.util.ThreadPriority;

public class HitsImpl extends HitsAbstract {
    
    // Factory methods
    //--------------------------------------------------------------------

    /**
     * Construct an empty Hits object.
     *
     * @param index the index object
     * @param field field our hits are from
     * @param settings search settings, or null for default
     * @return hits found
     */
    public static HitsImpl emptyList(BlackLabIndex index, AnnotatedField field, HitsSettings settings) {
        return fromList(index, field, (List<Hit>) null, settings);
    }

    /**
     * Make a wrapper Hits object for a list of Hit objects.
     *
     * Does not copy the list, but reuses it.
     *
     * @param index the index object
     * @param field field our hits are from
     * @param hits the list of hits to wrap, or null for empty Hits object
     * @param settings search settings, or null for default
     * @return hits found
     */
    public static HitsImpl fromList(BlackLabIndex index, AnnotatedField field, List<Hit> hits, HitsSettings settings) {
        return new HitsImpl(index, field, hits, settings);
    }

    /**
     * Construct a Hits object from a SpanQuery.
     *
     * @param index the index object
     * @param query the query to execute to get the hits
     * @param settings search settings
     * @return hits found
     * @throws WildcardTermTooBroad if a wildcard term matches too many terms in the index
     */
    public static HitsImpl fromSpanQuery(BlackLabIndex index, BLSpanQuery query, HitsSettings settings) throws WildcardTermTooBroad {
        return new HitsImpl(index, index.annotatedField(query.getField()), query, settings);
    }

    /**
     * Construct a Hits object from a Spans.
     *
     * Used for testing. Don't use this in applications, but construct a Hits object
     * from a SpanQuery, as it's more efficient.
     *
     * @param index the index object
     * @param field field our hits came from
     * @param source where to retrieve the Hit objects from
     * @param settings search settings
     * @return hits found
     */
    public static HitsImpl fromSpans(BlackLabIndex index, AnnotatedField field, BLSpans source, HitsSettings settings) {
        return new HitsImpl(index, field, source, settings);
    }

    
    // Instance variables
    //--------------------------------------------------------------------
    
    // General stuff
    
    
    
    // Hit information

    /**
     * The hits.
     */
    protected List<Hit> hits;

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

    /** Context of our query; mostly used to keep track of captured groups. */
    private HitQueryContext hitQueryContext;

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

    private List<String> capturedGroupNames;


    // Constructors
    //--------------------------------------------------------------------

    public HitsImpl(BlackLabIndex index, AnnotatedField field, HitsSettings settings) {
        super(index, field, settings);
        hitQueryContext = new HitQueryContext(); // to keep track of captured groups, etc.
    }
    
    /**
     * Make a wrapper Hits object for a list of Hit objects.
     *
     * Does not copy the list, but reuses it.
     *
     * @param index the index object
     * @param field field our hits came from
     * @param hits the list of hits to wrap
     * @param settings settings, or null for default
     */
    protected HitsImpl(BlackLabIndex index, AnnotatedField field, List<Hit> hits, HitsSettings settings) {
        this(index, field, settings);
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
     * @param index the index object
     * @param field field our hits came from
     * @param sourceQuery the query to execute to get the hits
     * @throws WildcardTermTooBroad if the query is overly broad (expands to too many terms)
     */
    private HitsImpl(BlackLabIndex index, AnnotatedField field, BLSpanQuery sourceQuery, HitsSettings settings) throws WildcardTermTooBroad {
        this(index, field, (List<Hit>) null, settings);
        try {
            IndexReader reader = index.reader();
            if (BlackLabIndexImpl.isTraceQueryExecution())
                logger.debug("Hits(): optimize");
            BLSpanQuery optimize = sourceQuery.optimize(reader);

            if (BlackLabIndexImpl.isTraceQueryExecution())
                logger.debug("Hits(): rewrite");
            spanQuery = optimize.rewrite(reader);

            //System.err.println(spanQuery);
            termContexts = new HashMap<>();
            Set<Term> terms = new HashSet<>();
            spanQuery = BLSpanQuery.ensureSortedUnique(spanQuery);
            if (BlackLabIndexImpl.isTraceQueryExecution())
                logger.debug("Hits(): createWeight");
            weight = spanQuery.createWeight(index.searcher(), false);
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
                    throw new BlackLabRuntimeException("Query matches too many terms; aborted.");
                }
                termContexts.put(term, TermContext.build(reader.getContext(), term));
            }

            currentSourceSpans = null;
            atomicReaderContexts = reader == null ? null : reader.leaves();
            atomicReaderContextIndex = -1;
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
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
     * @param index the index object
     * @param field field our hits came from
     * @param source where to retrieve the Hit objects from
     */
    private HitsImpl(BlackLabIndex index, AnnotatedField field, BLSpans source, HitsSettings settings) {
        this(index, field, (List<Hit>) null, settings);

        currentSourceSpans = source;
        try {
            sourceSpansFullyRead = currentSourceSpans.nextDoc() != DocIdSetIterator.NO_MORE_DOCS;
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
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
    private HitsImpl(HitsImpl copyFrom, HitsSettings settings) {
        this(copyFrom.index, copyFrom.field, settings == null ? copyFrom.settings : settings);
        try {
            copyFrom.ensureAllHitsRead();
        } catch (InterruptedException e) {
            // (should be detected by the client)
        }
        sourceSpansFullyRead = true;
        hits = copyFrom.hits;
        capturedGroups = copyFrom.capturedGroups;
        capturedGroupNames = copyFrom.capturedGroupNames;
        hitsCounted = copyFrom.hitsCountedSoFar();
        docsRetrieved = copyFrom.docsProcessedSoFar();
        docsCounted = copyFrom.docsCountedSoFar();
        previousHitDoc = copyFrom.previousHitDoc;
        copyMaxHitsRetrieved(copyFrom);
    }

    
    // Copying hits objects (and their relevant settings)
    //--------------------------------------------------------------------
    
    @Override
    public HitsImpl copy(HitsSettings settings) {
        return new HitsImpl(this, settings);
    }

    @Override
    public void copyMaxHitsRetrieved(Hits copyFrom) {
        this.maxHitsRetrieved = copyFrom.hitsProcessedExceededMaximum();
        this.maxHitsCounted = copyFrom.hitsCountedExceededMaximum();
    }
    

    // Deriving other Hits / Results instances
    //--------------------------------------------------------------------
    
    @Override
    public Hits sortedBy(final HitProperty sortProp) {
        return sortedBy(sortProp, false);
    }

    @Override
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

        HitsImpl hits = copy(null);
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

    @Override
    public Hits filteredBy(HitProperty property, HitPropValue value) {
        List<Annotation> requiredContext = property.needsContext();
        property.setContexts(new Contexts(this, requiredContext));

        List<Hit> filtered = new ArrayList<>();
        for (int i = 0; i < size(); i++) {
            if (property.get(i).equals(value))
                filtered.add(get(i));
        }
        HitsImpl hits = new HitsImpl(index, field, filtered, settings);
        hits.copyMaxHitsRetrieved(this);
        return hits;
    }

    @Override
    public HitGroups groupedBy(final HitProperty criteria) {
        return ResultsGrouper.fromHits(this, criteria);
    }

    @Override
    public DocResults perDocResults() {
        return DocResults.fromHits(index(), this);
    }

    @Override
    public TermFrequencyList getCollocations() {
        return TermFrequencyList.collocations(this, null, null, true);
    }

    @Override
    public synchronized TermFrequencyList getCollocations(Annotation annotation, QueryExecutionContext ctx, boolean sort) {
        return TermFrequencyList.collocations(this, annotation, ctx, sort);
    }
    
    @Override
    public HitsWindow window(int first, int windowSize) {
        return new HitsWindow(this, first, windowSize, settings());
    }

    @Override
    public HitsWindow window(int first, int windowSize, HitsSettings settings) {
        return new HitsWindow(this, first, windowSize, settings == null ? this.settings() : settings);
    }

    @Override
    public HitsWindow window(Hit hit) {
        int i = hits.indexOf(hit);
        if (i < 0)
            throw new BlackLabRuntimeException("Hit not found in hits list!");
        return window(i, 1);
    }
    
    // General stuff
    //--------------------------------------------------------------------

    @Override
    public String toString() {
        return "Hits#" + hitsObjId + " (fullyRead=" + sourceSpansFullyRead + ", hits.size()=" + hits.size() + ")";
    }
    

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
    
    @Override
    public Iterable<Hit> originalOrder() {
        // Construct a custom iterator that iterates over the hits in the hits
        // list, but can also take into account the Spans object that may not have
        // been fully read. This ensures we don't instantiate Hit objects for all hits
        // if we just want to display the first few.
        return new Iterable<Hit>() {
            @Override
            public Iterator<Hit> iterator() {
                // TODO Auto-generated method stub
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
        };
    }
    
    @Override
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
        return hits.get(sortOrder == null ? i : sortOrder[i]);
    }
    
    @Override
    public Hits getHitsInDoc(int docid) {
        try {
            ensureAllHitsRead();
        } catch (InterruptedException e) {
            // Interrupted. Just return no hits;
            // client should detect thread was interrupted if it
            // wants to use background threads.
            Thread.currentThread().interrupt();
            return HitsImpl.emptyList(index, field, settings);
        }
        List<Hit> hitsInDoc = new ArrayList<>();
        for (Hit hit : hits) {
            if (hit.doc() == docid)
                hitsInDoc.add(hit);
        }
        HitsImpl result = HitsImpl.fromList(index, field, hitsInDoc, settings);
        result.copyMaxHitsRetrieved(this);
        return result;
    }


    // Captured groups
    //--------------------------------------------------------------------

    @Override
    public List<String> getCapturedGroupNames() {
        return capturedGroupNames;
    }

    @Override
    public boolean hasCapturedGroups() {
        return capturedGroups != null;
    }

    @Override
    public Span[] getCapturedGroups(Hit hit) {
        if (capturedGroups == null)
            return null;
        return capturedGroups.get(hit);
    }

    @Override
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
    protected void ensureHitsRead(int number) throws InterruptedException {
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
            int maxHitsToRetrieve = settings.maxHitsToProcess();
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
                                capturedGroupNames = hitQueryContext.getCapturedGroupNames();
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
            throw BlackLabRuntimeException.wrap(e);
        } finally {
            ensureHitsReadLock.unlock();
        }
    }

    @Override
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

    @Override
    public int hitsProcessedTotal() {
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

    @Override
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

    @Override
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

    @Override
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

    @Override
    public int hitsCountedSoFar() {
        return hitsCounted;
    }

    @Override
    public int hitsProcessedSoFar() {
        return hits.size();
    }

    @Override
    public int docsCountedSoFar() {
        return docsCounted;
    }

    @Override
    public int docsProcessedSoFar() {
        return docsRetrieved;
    }

    @Override
    public boolean doneProcessingAndCounting() {
        return sourceSpansFullyRead || maxHitsCounted;
    }

    @Override
    public boolean hitsProcessedExceededMaximum() {
        return maxHitsRetrieved;
    }

    @Override
    public boolean hitsCountedExceededMaximum() {
        return maxHitsCounted;
    }

}
