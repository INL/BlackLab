package nl.inl.blacklab.search.results;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.search.spans.SpanWeight;
import org.apache.lucene.search.spans.SpanWeight.Postings;

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
public class HitsFromQueryParallel extends Hits {

    private static final boolean DO_PARALLEL = true;

    /** Settings such as max. hits to process/count. */
    private SearchSettings searchSettings;
    
    /** Did we exceed the maximums? */
    private MaxStats maxStats;
    
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
     * Our Spans object, which may not have been fully read yet.
     */
    private List<SpansReader> spansReaders;
    
    /** Iterator through our SpansReaders*/
    private Iterator<SpansReader> itSpansReader;
    
    private SpansReader currentSpansReader;
    
    private int hitIndexInCurrentSpansReader;

    /**
     * Term contexts for the terms in the query.
     */
    private Map<Term, TermContext> termContexts;

    /**
     * Did we completely read our Spans object?
     */
    private boolean allSourceSpansFullyRead = true;

    private Lock ensureHitsReadLock = new ReentrantLock();
    
    /** Context of our query; mostly used to keep track of captured groups. */
    private HitQueryContext hitQueryContext;
    
    /**
     * Document the previous hit was in, so we can count separate documents.
     */
    private int previousHitDoc = -1;

    private boolean parallelSpansReaderTasksStarted = false;

    /** If we've started tasks to read spans readers, here's the futures, so we can cancel them if necessary. */
    private List<Future<?>> futures = new ArrayList<>();

    /**
     * Construct a Hits object from a SpanQuery.
     *
     * @param queryInfo query info
     * @param sourceQuery the query to execute to get the hits
     * @param searchSettings search settings
     * @throws WildcardTermTooBroad if the query is overly broad (expands to too many terms)
     */
    protected HitsFromQueryParallel(QueryInfo queryInfo, BLSpanQuery sourceQuery, SearchSettings searchSettings) throws WildcardTermTooBroad {
        super(queryInfo);
        this.searchSettings = searchSettings;
        this.maxStats = new MaxStats();
        hitsCounted = 0;
        hitQueryContext = new HitQueryContext();
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
                    threadPauser.waitIfPaused();
                } catch (InterruptedException e) {
                    throw new InterruptedSearch(e);
                }
                termContexts.put(term, TermContext.build(reader.getContext(), term));
            }

            boolean loggedSpans = false;
            atomicReaderContexts = reader.leaves();
            spansReaders = new ArrayList<>(atomicReaderContexts.size());
            for (LeafReaderContext context: atomicReaderContexts) {
                BLSpans spans = (BLSpans) weight.getSpans(context, Postings.OFFSETS);
                if (!loggedSpans) {
                    queryInfo().log(LogLevel.EXPLAIN, "got Spans: " + spans);
                    loggedSpans = true;
                }
                if (spans != null)
                    spansReaders.add(new SpansReader(spans, context.docBase, hitQueryContext));
            }
            currentSpansReader = null;
            itSpansReader = spansReaders.iterator();
            hitIndexInCurrentSpansReader = -1;
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }

        allSourceSpansFullyRead = false;
    }
    
    @Override
    public String toString() {
        return "Hits#" + hitsObjId + " (fullyRead=" + allSourceSpansFullyRead + ", hitsSoFar=" + results.size() + ")";
    }

    /**
     * Ensure that we have read at least as many hits as specified in the parameter.
     *
     * @param number the minimum number of hits that will have been read when this
     *            method returns (unless there are fewer hits than this); if
     *            negative, reads all hits
     */
    @Override
    protected void ensureResultsRead(int number) {
        synchronized (this) {
            if (number < 0 && !parallelSpansReaderTasksStarted) {
                // We want all the hits. Start tasks to fetch hits from all SpansReaders in parallel.
                startParallelSpansReaderTasks();
                parallelSpansReaderTasksStarted = true;
            }
        }
        
        try {
            // Prevent locking when not required
            if (allSourceSpansFullyRead || (number >= 0 && results.size() > number))
                return;
    
            // At least one hit needs to be fetched.
            // Make sure we fetch at least FETCH_HITS_MIN while we're at it, to avoid too much locking.
            if (number >= 0 && number - results.size() < FETCH_HITS_MIN)
                number = results.size() + FETCH_HITS_MIN;
    
            while (!ensureHitsReadLock.tryLock()) {
                /*
                 * Another thread is already counting, we don't want to straight up block until it's done
                 * as it might be counting/retrieving all results, while we might only want trying to retrieve a small fraction
                 * So instead poll our own state, then if we're still missing results after that just count them ourselves
                 */
                Thread.sleep(50);
                if (allSourceSpansFullyRead || (number >= 0 && results.size() >= number))
                    return;
            }
            try {
                boolean readAllHits = number < 0;
                int maxHitsToCount = searchSettings.maxHitsToCount();
                int maxHitsToProcess = searchSettings.maxHitsToProcess();
                while (readAllHits || results.size() < number) {
    
                    // Pause if asked
                    threadPauser.waitIfPaused();
    
                    // Stop if we're at the maximum number of hits we want to count
                    if (maxHitsToCount >= 0 && hitsCounted >= maxHitsToCount) {
                        maxStats.setHitsCountedExceededMaximum();
                        break;
                    }
    
                    // Get the next hit from the spans, moving to the next
                    // segment when necessary.
                    Hit hit = null;
                    while (true) {
                        
                        if (currentSpansReader == null) {
                            // Exhausted (or not started yet); get next segment spans.

                            if (!itSpansReader.hasNext()) {
                                setFinished();
                                break;
                            }
                            currentSpansReader = itSpansReader.next();
                            hitIndexInCurrentSpansReader = -1;
                        }
                        
                        if (allSourceSpansFullyRead)
                            break;
    
                        // Advance to next hit
                        hitIndexInCurrentSpansReader++;
                        currentSpansReader.ensureResultsRead(hitIndexInCurrentSpansReader + 1);
                        List<Hit> spansResults = currentSpansReader.resultsList();
                        if (spansResults.size() <= hitIndexInCurrentSpansReader) {
                            // Done with this one.
                            currentSpansReader = null;
                        } else {
                            // We're at the next hit
                            hit = spansResults.get(hitIndexInCurrentSpansReader);
                            break;
                        }
                    }
                    
                    if (allSourceSpansFullyRead)
                        break;
    
                    // Count the hit and add it (unless we've reached the maximum number of hits we
                    // want)
                    hitsCounted++;
                    boolean maxHitsProcessed = maxStats.hitsProcessedExceededMaximum();
                    if (hit.doc() != previousHitDoc) {
                        docsCounted++;
                        if (!maxHitsProcessed)
                            docsRetrieved++;
                        previousHitDoc = hit.doc();
                    }
                    if (!maxHitsProcessed) {
                        if (capturedGroups != null) {
                            Span[] groups = new Span[hitQueryContext.numberOfCapturedGroups()];
                            hitQueryContext.getCapturedGroups(groups);
                            capturedGroups.put(hit, groups);
                        }
                        results.add(hit);
                        if (maxHitsToProcess >= 0 && results.size() >= maxHitsToProcess) {
                            maxStats.setHitsProcessedExceededMaximum();
                        }
                    }
                }
            } catch (InterruptedException e) {
                // We've stopped retrieving/counting
                maxStats.setHitsProcessedExceededMaximum();
                maxStats.setHitsCountedExceededMaximum();
                throw e;
            } finally {
                ensureHitsReadLock.unlock();
            }
        } catch (InterruptedException e) {
            throw new InterruptedSearch(e);
        }
    }
    
    /** Read all hits in a number of SpansReaders. */
    private static final class ReadSpansReaders implements Runnable {
        private List<SpansReader> readers;

        public ReadSpansReaders(List<SpansReader> readers) {
            this.readers = readers;
        }
        
        @Override
        public void run() {
            for (SpansReader reader: readers) {
                reader.ensureResultsRead(-1);
            }
        }
    }
    
    private void startParallelSpansReaderTasks() {
        if (DO_PARALLEL) {
            int maxTasksPerSearch = queryInfo().index().blackLab().maxThreadsPerSearch();
            int tasksToStart = Math.min(maxTasksPerSearch, spansReaders.size());
            if (tasksToStart == 0)
                return; // No spansReaders or maxTasksPerSearch == 0 (!?), avoid divide by zero
            int spansReadersPerTask = (spansReaders.size() + tasksToStart - 1) / tasksToStart;
            int currentSpansReader = 0;
            ExecutorService executorService = queryInfo().index().blackLab().searchExecutorService();
            for (int i = 0; i < tasksToStart; i++) {
                List<SpansReader> readers = new ArrayList<>();
                for (int j = 0; j < spansReadersPerTask && currentSpansReader < spansReaders.size(); j++) {
                    readers.add(spansReaders.get(currentSpansReader));
                    currentSpansReader++;
                }
                if (!readers.isEmpty()) {
                    ReadSpansReaders task = new ReadSpansReaders(readers);
                    futures.add(executorService.submit(task));
                }
            }
            System.err.println("PARALLEL: Started " + tasksToStart + " tasks with " + spansReadersPerTask + " spansReaders each (total " + spansReaders.size() + " readers)");
        }
    }

    private void setFinished() {
        allSourceSpansFullyRead = true;
        
        // We no longer need these; allow them to be GC'ed
        weight = null;
        atomicReaderContexts = null;
        termContexts = null;
        hitQueryContext = null;
        spansReaders = null;
        itSpansReader = null;
        currentSpansReader = null;
    }

    @Override
    public boolean doneProcessingAndCounting() {
        return allSourceSpansFullyRead || maxStats.hitsCountedExceededMaximum();
    }
    
    @Override
    public MaxStats maxStats() {
        return maxStats;
    }

}
