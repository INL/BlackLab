package nl.inl.blacklab.search.results;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.BlackLabIndexImpl;
import nl.inl.blacklab.search.Span;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.BLSpans;
import nl.inl.blacklab.search.lucene.HitQueryContext;
import nl.inl.util.ThreadPauser;

public class HitsFromQuery extends HitsImpl {

    /**
     * Construct a Hits object from a SpanQuery.
     *
     * @param index the index object
     * @param query the query to execute to get the hits
     * @param settings search settings
     * @return hits found
     * @throws WildcardTermTooBroad if a wildcard term matches too many terms in the index
     */
    public static HitsFromQuery fromSpanQuery(BlackLabIndex index, BLSpanQuery query, HitsSettings settings) throws WildcardTermTooBroad {
        return new HitsFromQuery(index, index.annotatedField(query.getField()), query, settings);
    }
    
    // Low-level Lucene hit fetching

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
    
    /** Context of our query; mostly used to keep track of captured groups. */
    private HitQueryContext hitQueryContext;
    
    /**
     * Document the previous hit was in, so we can count separate documents.
     */
    private int previousHitDoc = -1;

    /**
     * Construct a Hits object from a SpanQuery.
     *
     * @param index the index object
     * @param field field our hits came from
     * @param sourceQuery the query to execute to get the hits
     * @throws WildcardTermTooBroad if the query is overly broad (expands to too many terms)
     */
    HitsFromQuery(BlackLabIndex index, AnnotatedField field, BLSpanQuery sourceQuery, HitsSettings settings) throws WildcardTermTooBroad {
        super(index, field, null, settings);
        hitQueryContext = new HitQueryContext();
        try {
            IndexReader reader = index.reader();
            if (BlackLabIndexImpl.isTraceQueryExecution())
                logger.debug("Hits(): optimize");
            BLSpanQuery optimize = sourceQuery.optimize(reader);

            if (BlackLabIndexImpl.isTraceQueryExecution())
                logger.debug("Hits(): rewrite");
            BLSpanQuery spanQuery = optimize.rewrite(reader);

            //System.err.println(spanQuery);
            termContexts = new HashMap<>();
            Set<Term> terms = new HashSet<>();
            spanQuery = BLSpanQuery.ensureSortedUnique(spanQuery);
            if (BlackLabIndexImpl.isTraceQueryExecution())
                logger.debug("Hits(): createWeight");
            weight = spanQuery.createWeight(index.searcher(), false);
            weight.extractTerms(terms);
            threadPauser = new ThreadPauser();
            if (BlackLabIndexImpl.isTraceQueryExecution())
                logger.debug("Hits(): extract terms");
            for (Term term : terms) {
                try {
                    threadPauser.waitIfPaused();
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
    
    @Override
    public String toString() {
        return "Hits#" + hitsObjId + " (fullyRead=" + sourceSpansFullyRead + ", hits.size()=" + hits.size() + ")";
    }
    
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
                threadPauser.waitIfPaused();

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
                                capturedGroups = new CapturedGroupsImpl(hitQueryContext.getCapturedGroupNames());
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
    public boolean doneProcessingAndCounting() {
        return sourceSpansFullyRead || maxHitsCounted;
    }

}
