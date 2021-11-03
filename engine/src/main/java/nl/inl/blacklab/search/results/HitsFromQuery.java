package nl.inl.blacklab.search.results;

import java.io.IOException;
import java.util.ArrayList;
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
import org.apache.lucene.util.Bits;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.InterruptedSearch;
import nl.inl.blacklab.exceptions.WildcardTermTooBroad;
import nl.inl.blacklab.requestlogging.LogLevel;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.Span;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.BLSpans;
import nl.inl.blacklab.search.lucene.HitQueryContext;
import nl.inl.blacklab.search.lucene.optimize.ClauseCombinerNfa;

/**
 * A Hits object that is filled from a BLSpanQuery.
 */
public class HitsFromQuery extends Hits {

    /** Settings such as max. hits to process/count. */
    SearchSettings searchSettings;

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

    private Lock ensureHitsReadLock = new ReentrantLock();

    /** Context of our query; mostly used to keep track of captured groups. */
    private HitQueryContext hitQueryContext;

    /**
     * Document the previous hit was in, so we can count separate documents.
     */
    private int previousHitDoc = -1;

    private boolean loggedSpans;

    /**
     * This class holds all of the data that is modified inside the lock in ensureResultsRead.
     * All data is copied before being modified, so that it's not visible to other threads until
     * it can be published in a consistent state.
     *
     * Possible future work: use persistent data structures (such as pcollections) to cut down on
     * unnecessary copying.
     */
    private static class HitsResultsContext {
        boolean sourceSpansFullyRead;
        List<Hit> results;
        CapturedGroupsImpl capturedGroups;
        int hitsCounted;
        int docsRetrieved;
        int docsCounted;
        MaxStats maxStats;

        HitsResultsContext() {
            this.results = new ArrayList<>();
            this.maxStats = new MaxStats();
        }

        HitsResultsContext(HitsResultsContext context) {
            this.sourceSpansFullyRead = context.sourceSpansFullyRead;
            this.results = new ArrayList<>(context.results);
            this.capturedGroups = context.capturedGroups != null ? new CapturedGroupsImpl(context.capturedGroups) : null;
            this.hitsCounted = context.hitsCounted;
            this.docsRetrieved = context.docsRetrieved;
            this.docsCounted = context.docsCounted;
            this.maxStats = new MaxStats(context.maxStats.hitsProcessedExceededMaximum(), context.maxStats.hitsCountedExceededMaximum());
        }
    }

    /**
     * A volatile variable that holds the state. Writing to this variable publishes
     * the context so it can be seen by other threads.
     */
    private volatile HitsResultsContext hitsResultsContext;

    /**
     * Construct a Hits object from a SpanQuery.
     *
     * @param queryInfo query info
     * @param sourceQuery the query to execute to get the hits
     * @param searchSettings search settings
     * @throws WildcardTermTooBroad if the query is overly broad (expands to too many terms)
     */
    protected HitsFromQuery(QueryInfo queryInfo, BLSpanQuery sourceQuery, SearchSettings searchSettings) throws WildcardTermTooBroad {
        super(queryInfo, false);
        this.searchSettings = searchSettings;
        hitQueryContext = new HitQueryContext();

        this.hitsResultsContext = new HitsResultsContext();
        try {
            BlackLabIndex index = queryInfo.index();
            IndexReader reader = index.reader();

            // Override FI match threshold? (debug use only!)
            long oldFiMatchValue = ClauseCombinerNfa.getNfaThreshold();
            if (searchSettings.fiMatchFactor() != -1) {
                queryInfo.log(LogLevel.OPT, "setting NFA threshold for this query to " + searchSettings.fiMatchFactor());
                ClauseCombinerNfa.setNfaThreshold(searchSettings.fiMatchFactor());
            }

            sourceQuery.setQueryInfo(queryInfo);
            queryInfo.log(LogLevel.EXPLAIN, "Query before optimize()/rewrite(): " + sourceQuery);

            BLSpanQuery optimize = sourceQuery.optimize(reader);
            queryInfo.log(LogLevel.EXPLAIN, "Query after optimize(): " + optimize);

            BLSpanQuery spanQuery = optimize.rewrite(reader);
            queryInfo.log(LogLevel.EXPLAIN, "Query after rewrite(): " + spanQuery);

            // Restore previous FI match threshold
            if (searchSettings.fiMatchFactor() != -1) {
                ClauseCombinerNfa.setNfaThreshold(oldFiMatchValue);
            }

            //System.err.println(spanQuery);
            termContexts = new HashMap<>();
            Set<Term> terms = new HashSet<>();
            spanQuery = BLSpanQuery.ensureSortedUnique(spanQuery);
            weight = spanQuery.createWeight(index.searcher(), false);
            weight.extractTerms(terms);
            for (Term term : terms) {
                try {
                    threadAborter.checkAbort();
                } catch (InterruptedException e) {
                    throw new InterruptedSearch(e);
                }
                termContexts.put(term, TermContext.build(reader.getContext(), term));
            }

            currentSourceSpans = null;
            loggedSpans = false;
            atomicReaderContexts = reader.leaves();
            atomicReaderContextIndex = -1;
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    @Override
    public String toString() {
        HitsResultsContext hitsResultsContext = this.hitsResultsContext;
        return "Hits#" + hitsObjId + " (fullyRead=" + hitsResultsContext.sourceSpansFullyRead + ", hitsSoFar=" + hitsResultsContext.results.size() + ")";
    }

    /**
     * Ensure that we have read at least as many hits as specified in the parameter.
     *
     * @param number the minimum number of hits that will have been read when this
     *            method returns (unless there are fewer hits than this); if
     *            negative, reads all hits
     */
    //TODO Lexion change, this method and the whole class needs check as
    // we are not using the hitsArray change.
    @Override
    protected void ensureResultsRead(int number) {
        try {
            HitsResultsContext hitsResultsContext = this.hitsResultsContext; // Volatile read memory-barrier

            // Prevent locking when not required
            if (hitsResultsContext.sourceSpansFullyRead || (number >= 0 && hitsResultsContext.results.size() > number))
                return;

            // At least one hit needs to be fetched.
            // Make sure we fetch at least FETCH_HITS_MIN while we're at it, to avoid too much locking.
            if (number >= 0 && number - hitsResultsContext.results.size() < FETCH_HITS_MIN)
                number = hitsResultsContext.results.size() + FETCH_HITS_MIN;

            while (!ensureHitsReadLock.tryLock()) {
                /*
                 * Another thread is already counting, we don't want to straight up block until it's done
                 * as it might be counting/retrieving all results, while we might only want trying to retrieve a small fraction
                 * So instead poll our own state, then if we're still missing results after that just count them ourselves
                 */
                Thread.sleep(50);

                hitsResultsContext = this.hitsResultsContext; // Volatile read memory-barrier
                if (hitsResultsContext.sourceSpansFullyRead || (number >= 0 && hitsResultsContext.results.size() > number))
                    return;
            }
            try {
                // One more check in case another thread finished the read.
                hitsResultsContext = this.hitsResultsContext; // Volatile read memory-barrier
                if (hitsResultsContext.sourceSpansFullyRead || (number >= 0 && hitsResultsContext.results.size() > number))
                    return;

                // Make a copy of the context so we can mutate it.
                hitsResultsContext = new HitsResultsContext(hitsResultsContext);

                boolean readAllHits = number < 0;
                int maxHitsToCount = searchSettings.maxHitsToCount();
                int maxHitsToProcess = searchSettings.maxHitsToProcess();
                while (readAllHits || hitsResultsContext.results.size() < number) {
    
                    // Abort if asked
                    threadAborter.checkAbort();

                    // Stop if we're at the maximum number of hits we want to count
                    if (maxHitsToCount >= 0 && hitsResultsContext.hitsCounted >= maxHitsToCount) {
                        hitsResultsContext.maxStats.setHitsCountedExceededMaximum();
                        break;
                    }

                    // Get the next hit from the spans, moving to the next
                    // segment when necessary.
                    while (true) {
                        while (currentSourceSpans == null) {
                            // Exhausted (or not started yet); get next segment spans.

                            atomicReaderContextIndex++;
                            if (atomicReaderContextIndex >= atomicReaderContexts.size()) {
                                hitsResultsContext.sourceSpansFullyRead = true;
                                setFinished();
                                return;
                            }
                            // Get the atomic reader context and get the next Spans from it.
                            LeafReaderContext context = atomicReaderContexts.get(atomicReaderContextIndex);
                            Bits liveDocs = context.reader().getLiveDocs();
                            currentDocBase = context.docBase;
                            currentSourceSpans = (BLSpans) weight.getSpans(context, Postings.OFFSETS);
                            if (!loggedSpans) {
                                queryInfo().log(LogLevel.EXPLAIN, "got Spans: " + currentSourceSpans);
                                loggedSpans = true;
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
                                if (hitsResultsContext.capturedGroups == null && hitQueryContext.numberOfCapturedGroups() > 0) {
                                    hitsResultsContext.capturedGroups = new CapturedGroupsImpl(hitQueryContext.getCapturedGroupNames());
                                }

                                int doc;
                                boolean alive = false;
                                do {
                                    doc = currentSourceSpans.nextDoc();
                                    if (doc == DocIdSetIterator.NO_MORE_DOCS)
                                        currentSourceSpans = null; // no matching docs in this segment, try next
                                    else
                                        alive = liveDocs == null ? true : liveDocs.get(doc);
                                } while(currentSourceSpans != null && !alive);
                            }
                        }

                        LeafReaderContext context = atomicReaderContexts.get(atomicReaderContextIndex);
                        Bits liveDocs = context.reader().getLiveDocs();

                        // Advance to next hit
                        int start = currentSourceSpans.nextStartPosition();
                        if (start == Spans.NO_MORE_POSITIONS) {
                            int doc;
                            do {
                                doc = currentSourceSpans.nextDoc();
                            } while (doc != DocIdSetIterator.NO_MORE_DOCS && liveDocs != null && !liveDocs.get(doc));
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
                    hitsResultsContext.hitsCounted++;
                    int hitDoc = currentSourceSpans.docID() + currentDocBase;
                    boolean maxHitsProcessed = hitsResultsContext.maxStats.hitsProcessedExceededMaximum();
                    if (hitDoc != previousHitDoc) {
                        hitsResultsContext.docsCounted++;
                        if (!maxHitsProcessed)
                            hitsResultsContext.docsRetrieved++;
                        previousHitDoc = hitDoc;
                    }
                    if (!maxHitsProcessed) {
                        Hit hit = Hit.create(currentSourceSpans.docID() + currentDocBase, currentSourceSpans.startPosition(), currentSourceSpans.endPosition(), hitsResultsContext.results.size());
                        if (hitsResultsContext.capturedGroups != null) {
                            Span[] groups = new Span[hitQueryContext.numberOfCapturedGroups()];
                            hitQueryContext.getCapturedGroups(groups);
                            hitsResultsContext.capturedGroups.put(hit, groups);
                        }
                        hitsResultsContext.results.add(hit);
                        if (maxHitsToProcess >= 0 && hitsResultsContext.results.size() >= maxHitsToProcess) {
                            hitsResultsContext.maxStats.setHitsProcessedExceededMaximum();
                        }
                    }
                }
            } catch (InterruptedException e) {
                // We've stopped retrieving/counting
                hitsResultsContext.maxStats.setHitsProcessedExceededMaximum();
                hitsResultsContext.maxStats.setHitsCountedExceededMaximum();
                throw e;
            } catch (IOException e) {
                throw BlackLabRuntimeException.wrap(e);
            } finally {
                this.hitsResultsContext = hitsResultsContext; // Volatile write memory-barrier
                ensureHitsReadLock.unlock();
            }
        } catch (InterruptedException e) {
            throw new InterruptedSearch(e);
        }
    }

    private void setFinished() {
        // We no longer need these; allow them to be GC'ed
        weight = null;
        atomicReaderContexts = null;
        termContexts = null;
        currentSourceSpans = null;
        hitQueryContext = null;
    }

    @Override
    public boolean doneProcessingAndCounting() {
        HitsResultsContext hitsResultsContext = this.hitsResultsContext;
        return hitsResultsContext.sourceSpansFullyRead || hitsResultsContext.maxStats.hitsCountedExceededMaximum();
    }

    @Override
    public MaxStats maxStats() {
        return this.hitsResultsContext.maxStats;
    }

    @Override
    protected List<Hit> getResults() {
        return this.hitsResultsContext.results;
    }

    @Override
    public CapturedGroupsImpl capturedGroups() {
        return this.hitsResultsContext.capturedGroups;
    }

    @Override
    protected int getHitsCounted() {
        return this.hitsResultsContext.hitsCounted;
    }

    @Override
    protected int getDocsRetrieved() {
        return this.hitsResultsContext.docsRetrieved;
    }

    @Override
    protected int getDocsCounted() {
        return this.hitsResultsContext.docsCounted;
    }

}
