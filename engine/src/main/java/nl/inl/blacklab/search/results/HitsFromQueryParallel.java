package nl.inl.blacklab.search.results;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.IntUnaryOperator;
import java.util.stream.Collectors;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.spans.SpanWeight.Postings;
import org.apache.lucene.search.spans.Spans;
import org.apache.lucene.util.Bits;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.InterruptedSearch;
import nl.inl.blacklab.requestlogging.LogLevel;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.Span;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.BLSpanWeight;
import nl.inl.blacklab.search.lucene.BLSpans;
import nl.inl.blacklab.search.lucene.HitQueryContext;
import nl.inl.blacklab.search.lucene.optimize.ClauseCombinerNfa;
import nl.inl.blacklab.search.results.Hits.HitsArrays.HitIterator;
import nl.inl.util.ThreadAborter;

public class HitsFromQueryParallel extends Hits {

    /** If another thread is busy fetching hits and we're monitoring it, how often should we check? */
    private static final int HIT_POLLING_TIME_MS = 50;

    private static class SpansReader implements Runnable {

        /** How many hits should we collect (at least) before we add them to the global results? */
        private static final int ADD_HITS_TO_GLOBAL_THRESHOLD = 100;

        BLSpanWeight weight; // Weight is set when this is uninitialized, spans is set otherwise
        BLSpans spans; // usually lazy initialization - takes a long time to set up and holds a large amount of memory. Nulled after we're finished

        /**
         * Root hitQueryContext, needs to be shared between instances of SpansReader due to some internal global state.
         *
         * TODO refactor or improve documentation in HitQueryContext, the internal backing array is now shared between
         * instances of it, and is modified in copyWith(spans), which seems...dirty and it's prone to errors.
         */
        HitQueryContext sourceHitQueryContext; // Set when uninitialized (needed to construct own hitQueryContext)
        HitQueryContext hitQueryContext; // Set after initializion. Nulled after we're finished

        // Used to check if doc has been removed from the index. Nulled after we're finished.
        LeafReaderContext leafReaderContext;

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
        private final HitsArrays globalResults;
        /** Master list of capturedGroups (only set if any groups to capture. Should always be locked before writing! */
        private CapturedGroups globalCapturedGroups;

        // Internal state
        private boolean isDone = false;
        private final ThreadAborter threadAborter = ThreadAborter.create();
        private boolean isInitialized;
        private final int docBase;

        private boolean hasPrefetchedHit = false;
        private int prevDoc = -1;

        /**
         * Construct an uninitialized spansreader that will retrieve its own Spans object on when it's ran.
         *
         * HitsFromQueryParallel will immediately initialize one SpansReader (meaning its Spans object, HitQueryContext and
         * CapturedGroups objects are set) and leave the other ones to self-initialize when needed.
         *
         * It is done this way because of an initialization order issue with capture groups.
         * The issue is as follows:
         * - we want to lazy-initialize Spans objects:
         *   1. because they hold a lot of memory for large indexes.
         *   2. because only a few SpansReaders are active at a time.
         *   3. because they take a long time to setup.
         *   4. because we might not even need them all if a hits limit has been set.
         *
         * So if we precreate them all, we're doing a lot of upfront work we possibly don't need to.
         * We'd also hold a lot of ram hostage (>10GB in some cases!) because all Spans objects exist
         * simultaneously even though we're not using them simultaneously.
         * However, in order to know whether a query (such as A:([pos="A.*"]) "schip") uses/produces capture groups (and how many groups)
         * we need to call BLSpans::setHitQueryContext(...) and then check the number capture group names in the HitQueryContext afterwards.
         *
         * No why we need to know this:
         * - To construct the CaptureGroupsImpl we need to know the number of capture groups.
         * - To construct the SpansReaders we need to have already created the CaptureGroupsImpl, as it's shared between all of the SpansReaders.
         *
         * So to summarise: there is an order issue.
         * - we want to lazy-init the Spans.
         * - but we need the capture groups object.
         * - for that we need at least one Spans object created.
         *
         * Hence the explicit initialization of the first SpansReader by HitsFromQueryParallel.
         *
         * This will create one of the Spans objects so we can create and set the CapturedGroups object in this
         * first SpansReader. Then the rest of the SpansReaders receive the same CapturedGroups object and can
         * lazy-initialize when needed.
         *
         * @param weight span weight we're querying
         * @param leafReaderContext leaf reader we're running on
         * @param sourceHitQueryContext source HitQueryContext from HitsFromQueryParallel; we'll derive our own context from it
         * @param globalResults global results object (must be locked before writing)
         * @param globalCapturedGroups global captured groups object (must be locked before writing)
         * @param globalDocsProcessed global docs retrieved counter
         * @param globalDocsCounted global docs counter (includes ones that weren't retrieved because of max. settings)
         * @param globalHitsProcessed  global hits retrieved counter
         * @param globalHitsCounted global hits counter (includes ones that weren't retrieved because of max. settings)
         * @param globalHitsToProcess how many more hits to retrieve
         * @param globalHitsToCount how many more hits to count
         */
        private SpansReader(
            BLSpanWeight weight,
            LeafReaderContext leafReaderContext,
            HitQueryContext sourceHitQueryContext,

            HitsArrays globalResults,
            CapturedGroups globalCapturedGroups,
            AtomicInteger globalDocsProcessed,
            AtomicInteger globalDocsCounted,
            AtomicInteger globalHitsProcessed,
            AtomicInteger globalHitsCounted,
            AtomicInteger globalHitsToProcess,
            AtomicInteger globalHitsToCount
        ) {
            this.spans = null; // inverted for uninitialized version
            this.weight = weight;

            this.hitQueryContext = null;
            this.sourceHitQueryContext = sourceHitQueryContext;

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

            this.isInitialized = false;
            this.isDone = false;
        }

        private void initialize() {
            try {
                this.isInitialized = true;
                this.spans = this.weight.getSpans(this.leafReaderContext, Postings.OFFSETS); // do we need to synchronize this call between SpansReaders?
                this.weight = null;
                if (spans == null) { // This is normal, sometimes a section of the index does not contain hits.
                    this.isDone = true;
                    return;
                }

                this.hitQueryContext = this.sourceHitQueryContext.copyWith(this.spans);
                this.spans.setHitQueryContext(this.hitQueryContext);
                this.sourceHitQueryContext = null;
            } catch (IOException e) {
                throw BlackLabRuntimeException.wrap(e);
            }
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

            int doc = spans.docID();
            if (doc == -1) // initial document
                spans.nextDoc();

            int start = spans.nextStartPosition();
            while (start == Spans.NO_MORE_POSITIONS || (liveDocs != null && !liveDocs.get(spans.docID()))) {
                doc = spans.nextDoc();
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
            if (!isInitialized)
                this.initialize();

            if (isDone) // NOTE: initialize() may instantly set isDone to true, so order is important here.
                return;

            final int numCaptureGroups = hitQueryContext.numberOfCapturedGroups();
            final ArrayList<Span[]> capturedGroups = numCaptureGroups > 0 ? new ArrayList<Span[]>() : null;

            final HitsArrays results = new HitsArrays();
            final Bits liveDocs = leafReaderContext.reader().getLiveDocs();
            final IntUnaryOperator incrementCountUnlessAtMax = c -> c < this.globalHitsToCount.get() ? c + 1 : c; // only increment if doing so won't put us over the limit.
            final IntUnaryOperator incrementProcessUnlessAtMax = c -> c < this.globalHitsToProcess.get() ? c + 1 : c; // only increment if doing so won't put us over the limit.

            try {
                // Try to set the spans to a valid hit.
                // Mark if it is at a valid hit.
                // Count and store the hit (if we're not at the limits yet)

                if (!hasPrefetchedHit) {
                    prevDoc = spans.docID();
                    hasPrefetchedHit = advanceSpansToNextHit(spans, liveDocs);
                }

                while (hasPrefetchedHit) {
                    // Only if previous value (which is returned) was not yet at the limit (and thus we actually incremented) do we count this hit.
                    // Otherwise, don't store it either. We're done, just return.
                    final boolean abortBeforeCounting = this.globalHitsCounted.getAndUpdate(incrementCountUnlessAtMax) >= this.globalHitsToCount.get();
                    if (abortBeforeCounting)
                        return;

                    // only if previous value (which is returned) was not yet at the limit (and thus we actually incremented) do we store this hit.
                    final boolean storeThisHit = this.globalHitsProcessed.getAndUpdate(incrementProcessUnlessAtMax) < this.globalHitsToProcess.get();

                    final int doc = spans.docID() + docBase;
                    if (doc != prevDoc) {
                        globalDocsCounted.incrementAndGet();
                        if (storeThisHit) {
                            globalDocsProcessed.incrementAndGet();
                        }
                        if (results.size() >= ADD_HITS_TO_GLOBAL_THRESHOLD) {
                            // We've built up a batch of hits. Add them to the global results.
                            // We do this only once per doc, so hits from the same doc remain contiguous in the master list.
                            // [NOTE JN: does this matter? and if so, doesn't it also matter that docId increases throughout the
                            //           master list? Probably not, unless we wrap the Hits inside a Spans again, which generally
                            //           require these properties to hold.]
                            addToGlobalResults(results, capturedGroups);
                        }
                    }

                    if (storeThisHit) {
                        int start = spans.startPosition();
                        int end = spans.endPosition();
                        results.add(doc, start, end);
                        if (capturedGroups != null) {
                            Span[] groups = new Span[numCaptureGroups];
                            hitQueryContext.getCapturedGroups(groups);
                            capturedGroups.add(groups);
                        }
                    }

                    hasPrefetchedHit = advanceSpansToNextHit(spans, liveDocs);
                    prevDoc = doc;

                    // Do this at the end so interruptions don't happen halfway through a loop and lead to invalid states
                    threadAborter.checkAbort();
                }
            } catch (InterruptedException e) {
                throw new InterruptedSearch(e);
            } catch (IOException e) {
                throw BlackLabRuntimeException.wrap(e);
            } finally {
                // write out leftover hits in last document/aborted document
                if (results.size() > 0)
                    addToGlobalResults(results, capturedGroups);
            }

            // If we're here, the loop reached its natural end - we're done.
            // Free some objects to avoid holding on to memory
            this.isDone = true;
            this.spans = null;
            this.hitQueryContext = null;
            this.leafReaderContext = null;
        }

        void addToGlobalResults(HitsArrays hits, List<Span[]> capturedGroups) {
            globalResults.addAll(hits);

            if (globalCapturedGroups != null) {
                synchronized (globalCapturedGroups) {
                    HitIterator it = hits.iterator();
                    int i = 0;
                    while (it.hasNext()) {
                        Hit h = it.next().toHit();
                        globalCapturedGroups.put(h, capturedGroups.get(i));
                        ++i;
                    }
                    capturedGroups.clear();
                }
            }

            hits.withWriteLock(__ -> {
                hits.docs().clear();
                hits.starts().clear();
                hits.ends().clear();
            });
        }

        public HitQueryContext getHitContext() {
            return hitQueryContext;
        }

        public void setCapturedGroups(CapturedGroups capturedGroups) {
            globalCapturedGroups = capturedGroups;
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
    protected boolean allSourceSpansFullyRead = false;

    protected HitsFromQueryParallel(QueryInfo queryInfo, BLSpanQuery sourceQuery, SearchSettings searchSettings) {
        super(queryInfo, new HitsArrays()); // explicitly construct HitsArrays so they're writeable
        this.searchSettings = searchSettings;
        final BlackLabIndex index = queryInfo.index();
        final IndexReader reader = index.reader();
        BLSpanQuery optimizedQuery;

        // Ensure maxcount >= maxprocess >= 0
        // After this both will be above 0 and process will never exceed count
        int configuredMaxHitsToCount = searchSettings.maxHitsToCount();
        int configuredMaxHitsToProcess = searchSettings.maxHitsToProcess();
        if (configuredMaxHitsToCount < 0)
            configuredMaxHitsToCount = Integer.MAX_VALUE;
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

            // This call can take a long time
            BLSpanWeight weight = optimizedQuery.createWeight(index.searcher(), false);

            boolean hasInitialized = false;
            for (LeafReaderContext leafReaderContext : reader.leaves()) {
                SpansReader spansReader = new SpansReader(
                    weight,
                    leafReaderContext,
                    this.hitQueryContext,
                    this.hitsArrays,
                    this.capturedGroups,
                    this.globalDocsProcessed,
                    this.globalDocsCounted,
                    this.globalHitsProcessed,
                    this.globalHitsCounted,
                    this.requestedHitsToProcess,
                    this.requestedHitsToCount
                );
                spansReaders.add(spansReader);

                if (!hasInitialized) {
                    // We haven't initialized the HitQueryContext and CapturedGroups yet,
                    // because this is the first SpansReader (or at least the first one that
                    // contains at least one hit). Initialize them now.

                    // NOTE: this will initialize our HitQueryContext with any capture group names!
                    spansReader.initialize();
                    if (spansReader.isDone)
                        continue;
                    HitQueryContext hitQueryContextForThisSpans = spansReader.getHitContext();

                    // Now figure out if we have capture groups
                    // Needs to be null if unused!
                    if (hitQueryContextForThisSpans.getCaptureRegisterNumber() > 0) {
                        capturedGroups = new CapturedGroupsImpl(hitQueryContextForThisSpans.getCapturedGroupNames());
                        spansReader.setCapturedGroups(capturedGroups);
                    }

                    hasInitialized = true;
                }
            }

            if (spansReaders.isEmpty())
                allSourceSpansFullyRead = true;
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    @Override
    protected void ensureResultsRead(int number) {
        final int clampedNumber = number = number < 0 ? maxHitsToCount : Math.min(number, maxHitsToCount);

        if (allSourceSpansFullyRead || (hitsArrays.size() >= clampedNumber)) {
            return;
        }

        // clamp number to [currentrequested, number, maxrequested], defaulting to max if number < 0
        this.requestedHitsToCount.getAndUpdate(c -> Math.max(Math.min(clampedNumber, maxHitsToCount), c)); // update count
        this.requestedHitsToProcess.getAndUpdate(c -> Math.max(Math.min(clampedNumber, maxHitsToProcess), c)); // update process

        boolean hasLock = false;
        try {
            /*
             * Another thread is already working on hits, we don't want to straight up block until it's done
             * as it might be counting/retrieving all results, while we might only want trying to retrieve a small fraction
             * So instead poll our own state, then if we're still missing results after that just count them ourselves
             */
            while (!ensureHitsReadLock.tryLock()) {
                if (allSourceSpansFullyRead || (hitsArrays.size() >= clampedNumber)) {
                    return;
                }

                Thread.sleep(HIT_POLLING_TIME_MS);
            }
            hasLock = true;
            // This is the blocking portion, retrieve all hits from the other threads.
            try {
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

                // Wait for workers to complete.
                try {
                    while (!pendingResults.stream().allMatch(f -> f.isCancelled() || f.isDone())) {
                        Thread.sleep(HIT_POLLING_TIME_MS);
                        threadAborter.checkAbort();
                    }
                } catch (InterruptedException e) {
                    // Interrupt our worker threads as well
                    pendingResults.forEach(f -> f.cancel(true));
                    throw e;
                }

                // Remove all SpansReaders that have finished.
                Iterator<SpansReader> it = spansReaders.iterator();
                while (it.hasNext()) {
                    if (it.next().isDone)
                        it.remove();
                }
                this.allSourceSpansFullyRead = spansReaders.isEmpty();
            } catch (Exception e) {
                if (!(e instanceof InterruptedException))
                    e.printStackTrace();
                throw e.getCause(); // Something went wrong in one of the worker threads (interrupted?), process exception using outer catch
            }
        } catch (InterruptedException e) {
            throw new InterruptedSearch(e);
        } catch (Throwable e) {
            throw BlackLabRuntimeException.wrap(e);
        } finally {
            if (hasLock)
                ensureHitsReadLock.unlock();
        }
    }

    @Override
    public MaxStats maxStats() {
        return new MaxStats(this.globalHitsCounted.get() >= this.maxHitsToProcess, this.globalHitsCounted.get() >= this.maxHitsToCount);
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
