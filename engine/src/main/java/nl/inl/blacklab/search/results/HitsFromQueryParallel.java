package nl.inl.blacklab.search.results;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.IntUnaryOperator;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.DocIdSetIterator;
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
import nl.inl.blacklab.search.lucene.BLSpanWeight;
import nl.inl.blacklab.search.lucene.BLSpans;
import nl.inl.blacklab.search.lucene.HitQueryContext;
import nl.inl.blacklab.search.lucene.optimize.ClauseCombinerNfa;
import nl.inl.util.BlockTimer;
import nl.inl.util.ThreadPauser;

public class HitsFromQueryParallel extends Hits {
    private static class SpansReader implements Runnable {
        // Things to get results
        final HitQueryContext hitQueryContext;
        final LeafReaderContext leafReaderContext;
        final BLSpans spans;

        // Global counters, shared between instances of SpansReader in order to coordinate progress
        final AtomicInteger globalDocsProcessed;
        final AtomicInteger globalDocsCounted;
        final AtomicInteger globalHitsProcessed;
        final AtomicInteger globalHitsCounted;
        /** Target number of hits to store in the {@link globalResults} list */
        final AtomicInteger globalHitsToProcess;
        /** Target number of hits to count, must always be >= {@link globalHitsToProcess} */
        final AtomicInteger globalHitsToCount;
        /** Master list of hits, shared between SpansReaders, should always be locked before writing! */
        private final List<Hit> globalResults; 
        /** Master list of capturedGroups (only set if any groups to capture. Should always be locked before writing! */
        private final CapturedGroupsImpl globalCapturedGroups;

        // Internal state
        private final ThreadPauser threadPauser = ThreadPauser.create();
        private final int docBase;
        private boolean isDone = false;

        /**
         * 
         * @param spans
         * @param leafReaderContext
         * @param capturedGroups 
         * @param globalHitsProcessed
         * @param globalHitsCounted
         */
        public SpansReader(
            BLSpans spans, 
            LeafReaderContext leafReaderContext,
            HitQueryContext hitQueryContext,
            List<Hit> globalResults,
            CapturedGroupsImpl globalCapturedGroups, 
            AtomicInteger globalDocsProcessed,
            AtomicInteger globalDocsCounted,
            AtomicInteger globalHitsProcessed, 
            AtomicInteger globalHitsCounted, 
            AtomicInteger globalHitsToProcess, 
            AtomicInteger globalHitsToCount
        ) {
            this.spans = spans;
            this.hitQueryContext = hitQueryContext;
            this.leafReaderContext = leafReaderContext;
            
            this.globalResults = globalResults;
            this.globalCapturedGroups = globalCapturedGroups;
            this.globalDocsProcessed = globalDocsProcessed;
            this.globalDocsCounted = globalDocsCounted;
            this.globalHitsProcessed = globalHitsProcessed;
            this.globalHitsCounted = globalHitsCounted;
            this.globalHitsToCount = globalHitsToCount;
            this.globalHitsToProcess = globalHitsToProcess;
            
            this.docBase = leafReaderContext.docBase;
        }

        /**
         * Step through all hits in all documents in this spans object.
         * 
         * @param spans
         * @param liveDocs used to check if the document is still alive in the index. 
         * @return true if the spans has been advanced to the next hit, false if out of hits.
         * @throws IOException
         */
        private static boolean advanceSpansToNextHit(BLSpans spans, Bits liveDocs) throws IOException {
            if (spans.docID() == DocIdSetIterator.NO_MORE_DOCS && spans.startPosition() == Spans.NO_MORE_POSITIONS)  
                return false;
        
            int start = spans.nextStartPosition();
            while (start == Spans.NO_MORE_POSITIONS || start == -1) {
                int doc = spans.nextDoc();
                if (doc == DocIdSetIterator.NO_MORE_DOCS) {
                    return false;
                }
                if (liveDocs != null && !liveDocs.get(doc))
                    continue;
                start = spans.nextStartPosition();
            }
            return true;
        }

        /**
         * Collect all hits from our spans object.
         * Updates the global counters, shared with other SpansReader objects operating on the same result set.
         * Hits are periodically copied into the {@link SpansReader#globalResults} list when a large enough batch has been gathered.
         * 
         * Updating the maximums while this is running is allowed.
         */
        @Override
        public synchronized void run() {
            if (isDone)
                return;
            
            final int numCaptureGroups = hitQueryContext.numberOfCapturedGroups();
            final Map<Hit, Span[]> capturedGroups = numCaptureGroups > 0 ? new HashMap<Hit, Span[]>() : null;
            
            final List<Hit> results = new ArrayList<>();
            final Bits liveDocs = leafReaderContext.reader().getLiveDocs();
            final IntUnaryOperator incrementUnlessAtMax = c -> c < this.globalHitsToProcess.get() ? c + 1 : c; // only increment if doing so won't put us over the limit.
            
            try {
                int prevDoc = spans.docID();
                while (advanceSpansToNextHit(spans, liveDocs)) {
                    // only if previous value (which is returned) was not yet at the limit (and thus we actually incremented) do we store this hit.
                    final boolean storeThisHit = this.globalHitsProcessed.getAndUpdate(incrementUnlessAtMax) < this.globalHitsToProcess.get(); 
                    final int doc = spans.docID() + docBase;
                    if (doc != prevDoc) {
                        globalDocsCounted.incrementAndGet();
                        if (storeThisHit) {
                            globalDocsProcessed.incrementAndGet();                            
                        }
                        reportHitsIfMoreThan(results, capturedGroups, 100); // only once per doc, so hits from the same doc remain contiguous in the master list
                        prevDoc = doc;
                    }

                    if (storeThisHit) {
                        int start = spans.startPosition();
                        int end = spans.endPosition();
                        Hit hit = Hit.create(doc, start, end); 
                        results.add(hit);
                        if (capturedGroups != null) {
                            Span[] groups = new Span[numCaptureGroups];
                            hitQueryContext.getCapturedGroups(groups);
                            capturedGroups.put(hit, groups);
                        }
                    }

                    // Stop if we're done.
                    if (this.globalHitsCounted.incrementAndGet() >= this.globalHitsToCount.get())
                        return;

                    // Do this at the end so interruptions don't happen halfway a loop and lead to invalid states
                    threadPauser.waitIfPaused();
                }
            } catch (InterruptedException e) {
                throw new InterruptedSearch(e);
            } catch (IOException e) {
                throw BlackLabRuntimeException.wrap(e);
            } finally {
                // write out leftover hits in last document/aborted document
                reportHitsIfMoreThan(results, capturedGroups, 0);
            }
            
            // If we're here, the loop reached its natural end - we're done.
            isDone = true;
        }
        
        void reportHitsIfMoreThan(List<Hit> hits, Map<Hit, Span[]> capturedGroups, int count) {
            if (hits.size() >= count) {
                synchronized (globalResults) {
                    globalResults.addAll(hits);
                    hits.clear();

                    if (capturedGroups != null) {
                        synchronized (globalCapturedGroups) {
                            globalCapturedGroups.putAll(capturedGroups);
                            capturedGroups.clear();
                        }
                    }
                }
            }
        }
    }
    
    // hit count tracking
    protected final SearchSettings searchSettings;
    protected final AtomicInteger globalDocsProcessed = new AtomicInteger();
    protected final AtomicInteger globalDocsCounted = new AtomicInteger();
    protected final AtomicInteger globalHitsProcessed = new AtomicInteger();
    protected final AtomicInteger globalHitsCounted = new AtomicInteger();
    /** Should be normalized and clamped to configured maximum, i.e. always max >= requested >= 1 */ 
    protected final AtomicInteger requestedHitsToProcess = new AtomicInteger();
    /** Should be normalized and clamped to configured maximum, i.e. always max >= requested >= 1 */
    protected final AtomicInteger requestedHitsToCount = new AtomicInteger();
    /** Configured upper limit of requestedHitsToProcess, to which it will always be clamped. */
    protected final int maxHitsToProcess;
    /** Configured upper limit of requestedHitsToCount, to which it will always be clamped. */
    protected final int maxHitsToCount;
    
    
    // state
    protected final HitQueryContext hitQueryContext = new HitQueryContext();
    protected final Lock ensureHitsReadLock = new ReentrantLock();
    protected final List<SpansReader> spansReaders = new ArrayList<>();
//    protected final CapturedGroupsImpl capturedGroups;
    protected boolean allSourceSpansFullyRead = false;

    protected HitsFromQueryParallel(QueryInfo queryInfo, BLSpanQuery sourceQuery, SearchSettings searchSettings) throws WildcardTermTooBroad {
        super(queryInfo);
        this.searchSettings = searchSettings;
        final BlackLabIndex index = queryInfo.index();
        final IndexReader reader = index.reader();
        BLSpanQuery optimizedQuery;
        
        // Ensure maxcount >= maxprocess >= 0
        // After this both will be above 0 and process will never exceed count
        int configuredMaxHitsToCount = searchSettings.maxHitsToCount();
        int configuredMaxHitsToProcess = searchSettings.maxHitsToProcess();
        if (configuredMaxHitsToCount < 0) configuredMaxHitsToCount = Integer.MAX_VALUE;
        if (configuredMaxHitsToProcess < 0 || configuredMaxHitsToProcess > configuredMaxHitsToCount)
            configuredMaxHitsToProcess = configuredMaxHitsToCount;
        this.maxHitsToProcess = configuredMaxHitsToProcess;
        this.maxHitsToCount = configuredMaxHitsToCount;
                
        try {
            // Override FI match threshold? (debug use only!)
            synchronized(ClauseCombinerNfa.class) {
                long oldFiMatchValue = ClauseCombinerNfa.getNfaThreshold();
                if (searchSettings.fiMatchFactor() != -1) {
                    queryInfo.log(LogLevel.OPT, "setting NFA threshold for this query to " + searchSettings.fiMatchFactor());
                    ClauseCombinerNfa.setNfaThreshold(searchSettings.fiMatchFactor());
                }
                
                sourceQuery.setQueryInfo(queryInfo);
                queryInfo.log(LogLevel.EXPLAIN, "Query before optimize()/rewrite(): " + sourceQuery);
                
                optimizedQuery = sourceQuery.optimize(reader);
                queryInfo.log(LogLevel.EXPLAIN, "Query after optimize(): " + optimizedQuery);
                
                optimizedQuery = optimizedQuery.rewrite(reader);
                queryInfo.log(LogLevel.EXPLAIN, "Query after rewrite(): " + optimizedQuery);
                
                optimizedQuery = BLSpanQuery.ensureSortedUnique(optimizedQuery);
                
                // Restore previous FI match threshold
                if (searchSettings.fiMatchFactor() != -1) {
                    ClauseCombinerNfa.setNfaThreshold(oldFiMatchValue);
                }
            }

            BLSpanWeight weight = optimizedQuery.createWeight(index.searcher(), false);
            
            // We need to know the captured group names before we can construct the SpansReaders (because they need them)
            // But we only know the names after we iterated all leaves.
            // So we need 2 iterations: one to get the names (this one), and one to construct the readers.
            List<Pair<BLSpans, LeafReaderContext>> mySpans = new ArrayList<>();
            for (LeafReaderContext leafReaderContext : reader.leaves()) {
                BLSpans spans = weight.getSpans(leafReaderContext, Postings.OFFSETS);
                spans.setHitQueryContext(this.hitQueryContext); // NOTE: modifies hitQueryContext object (adding the capture group names)
                mySpans.add(Pair.of(spans, leafReaderContext));
            }
            
            this.capturedGroups = new CapturedGroupsImpl(this.hitQueryContext.getCapturedGroupNames());
            
            // Wrap each LeafReaderContext in a SpansReader
            for (Pair<BLSpans, LeafReaderContext> spansAndContext : mySpans) {
                spansReaders.add(
                    new SpansReader(
                        spansAndContext.getLeft(),
                        spansAndContext.getRight(),
                        this.hitQueryContext,
                        this.results,
                        this.capturedGroups,
                        this.globalDocsProcessed,
                        this.globalDocsCounted,
                        this.globalHitsProcessed, 
                        this.globalHitsCounted, 
                        this.requestedHitsToProcess,
                        this.requestedHitsToCount
                    )
                );
            }
            if (spansReaders.isEmpty())
                allSourceSpansFullyRead = true;
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    @Override
    protected void ensureResultsRead(int number) {
        number = number < 0 ? maxHitsToCount : Math.min(number, maxHitsToCount);
        
        synchronized(results) { // synchronize because worker threads might be writing
            if (allSourceSpansFullyRead || (results.size() >= number)) {
                return;
            }
        }
        
        // clamp number to [currentrequested, number, maxrequested], defaulting to max if number < 0
        this.requestedHitsToCount.set(Math.max(Math.min(number, maxHitsToCount), requestedHitsToCount.get())); // update count
        this.requestedHitsToProcess.set(Math.max(Math.min(number, maxHitsToProcess), requestedHitsToProcess.get())); // update process
        
        boolean hasLock = false;
        try {
            /*
             * Another thread is already working on hits, we don't want to straight up block until it's done
             * as it might be counting/retrieving all results, while we might only want trying to retrieve a small fraction
             * So instead poll our own state, then if we're still missing results after that just count them ourselves
             */
            while (!ensureHitsReadLock.tryLock()) {
                synchronized(results) {
                    // synchronize on results just to be sure we're not getting invalid state while a worker thread is writing
                    if (allSourceSpansFullyRead || (results.size() >= number)) {
                        return;
                    }
                }
                Thread.sleep(50);
            }
            hasLock = true;
            // This is the blocking portion, retrieve all hits from the other threads.
            try (BlockTimer t = BlockTimer.create("ensureResultsRead " + number)) {
                final ExecutorService executorService = queryInfo().index().blackLab().searchExecutorService();
                
                final AtomicInteger i = new AtomicInteger();
                final int numThreads = Math.max(queryInfo().index().blackLab().maxThreadsPerSearch(), 1);
                List<Future<?>> pendingResults = spansReaders
                    .stream()
                    .collect(Collectors.groupingBy(sr -> i.getAndIncrement() % numThreads)) // subdivide the list, one sublist per thread to use.
                    .values()
                    .stream()
                    .map(list -> executorService.submit(() -> list.forEach(SpansReader::run))) // now submit one task per sublist
                    .collect(Collectors.toList()); // gather the futures
                
                for (Future<?> f : pendingResults) { f.get(); } // wait for workers to complete. 
                Iterator<SpansReader> it = spansReaders.iterator(); 
                while (it.hasNext()) { if (it.next().isDone) it.remove(); } // remove all SpansReaders that have finished.
                this.allSourceSpansFullyRead = spansReaders.isEmpty();
            } catch (Exception e) {
                throw e.getCause(); // Something went wrong in one of the worker threads (interrupted?), process exception using outer catch
            }
        } catch (InterruptedException e) {
            throw new InterruptedSearch(e);
        } catch (Throwable e) {
            if (e instanceof BlackLabRuntimeException) 
                throw (BlackLabRuntimeException) e;
            throw new BlackLabRuntimeException(e);
        } finally {
            if (hasLock) 
                ensureHitsReadLock.unlock();
        }
    }
    
    @Override
    public MaxStats maxStats() {
        synchronized(this.results) {
            return new MaxStats(this.globalHitsCounted.get() >= this.maxHitsToProcess, this.globalHitsCounted.get() >= this.maxHitsToCount);
        }
    }

    @Override
    public boolean doneProcessingAndCounting() {
        return allSourceSpansFullyRead || (maxStats().hitsCountedExceededMaximum() && maxStats().hitsProcessedExceededMaximum());
    }
    
    @Override
    protected int docsCountedSoFar() {
        return this.globalDocsCounted.get();
    }
    @Override
    protected int docsCountedTotal() {
        ensureAllResultsRead();
        return this.globalDocsCounted.get();
    }
    
    @Override
    protected int docsProcessedSoFar() {
        return this.globalDocsProcessed.get();
    }
    
    @Override
    protected int docsProcessedTotal() {
        ensureAllResultsRead();
        return this.globalDocsProcessed.get();
    }
    
    @Override
    protected int hitsCountedSoFar() {
        return this.globalHitsCounted.get();
    }
    
    @Override
    protected int hitsCountedTotal() {
        ensureAllResultsRead();
        return this.globalHitsCounted.get();
    }
    
    @Override
    protected int hitsProcessedSoFar() {
        return this.globalHitsProcessed.get();
    }
    
    @Override
    protected int hitsProcessedTotal() {
        ensureAllResultsRead();
        return this.globalHitsProcessed.get();
    }
    
    @Override
    protected int resultsCountedSoFar() {
        return hitsCountedSoFar();
    }
    
    @Override
    protected int resultsCountedTotal() {
        return hitsCountedTotal();
    }
    
    @Override
    protected int resultsProcessedSoFar() {
        return hitsProcessedSoFar();
    }
    
    @Override
    protected int resultsProcessedTotal() {
        return hitsProcessedTotal();
    }
}
