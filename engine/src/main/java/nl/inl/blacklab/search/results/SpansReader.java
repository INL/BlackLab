package nl.inl.blacklab.search.results;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongUnaryOperator;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.spans.Spans;
import org.apache.lucene.search.spans.SpanWeight.Postings;
import org.apache.lucene.util.Bits;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.InterruptedSearch;
import nl.inl.blacklab.search.Span;
import nl.inl.blacklab.search.lucene.BLSpanWeight;
import nl.inl.blacklab.search.lucene.BLSpans;
import nl.inl.blacklab.search.lucene.HitQueryContext;
import nl.inl.util.ThreadAborter;

/** 
 * Helper class for use with {@link HitsFromQueryParallel} <br><br>
 * 
 * HitsFromQueryParallel generally constructs one SpansReader instance per segment ({@link LeafReaderContext}) of the index.
 * The SpansReader will then produce results for the segment, periodically merging them back to the global resultset passed in.
 */
class SpansReader implements Runnable {

    /** How many hits should we collect (at least) before we add them to the global results? */
    private static final int ADD_HITS_TO_GLOBAL_THRESHOLD = 100;

    BLSpanWeight weight; // Weight is set when this is uninitialized, spans is set otherwise
    BLSpans spans; // usually lazy initialization - takes a long time to set up and holds a large amount of memory.
                   // Set to null after we're finished

    /**
     * Root hitQueryContext, needs to be shared between instances of SpansReader due to some internal global state.
     *
     * TODO refactor or improve documentation in HitQueryContext, the internal backing array is now shared between
     * instances of it, and is modified in copyWith(spans), which seems...dirty and it's prone to errors.
     */
    HitQueryContext sourceHitQueryContext; // Set when uninitialized (needed to construct own hitQueryContext)
    HitQueryContext hitQueryContext; // Set after initialization. Set to null after we're finished

    // Used to check if doc has been removed from the index. Set to null after we're finished.
    LeafReaderContext leafReaderContext;

    // Global counters, shared between instances of SpansReader in order to coordinate progress
    final AtomicLong globalDocsProcessed;
    final AtomicLong globalDocsCounted;
    final AtomicLong globalHitsProcessed;
    final AtomicLong globalHitsCounted;
    /** Target number of hits to store in the {@link #globalResults} list */
    final AtomicLong globalHitsToProcess;
    /** Target number of hits to count, must always be >= {@link #globalHitsToProcess} */
    final AtomicLong globalHitsToCount;
    /** Master list of hits, shared between SpansReaders, should always be locked before writing! */
    private final HitsInternalMutable globalResults;
    /** Master list of capturedGroups (only set if any groups to capture. Should always be locked before writing! */
    private CapturedGroupsImpl globalCapturedGroups;

    // Internal state
    boolean isDone = false;
    private final ThreadAborter threadAborter = ThreadAborter.create();
    private boolean isInitialized;
    private final int docBase;

    private boolean hasPrefetchedHit = false;
    private int prevDoc = -1;

    /**
     * Construct an uninitialized SpansReader that will retrieve its own Spans object on when it's ran.
     *
     * HitsFromQueryParallel will immediately initialize one SpansReader (meaning its Spans object, HitQueryContext and
     * CapturedGroups objects are set) and leave the other ones to self-initialize when needed.
     *
     * It is done this way because of an initialization order issue with capture groups.
     * The issue is as follows:
     * - we want to lazy-initialize Spans objects:
     * 1. because they hold a lot of memory for large indexes.
     * 2. because only a few SpansReaders are active at a time.
     * 3. because they take a long time to setup.
     * 4. because we might not even need them all if a hits limit has been set.
     *
     * So if we pre-create them all, we're doing a lot of upfront work we possibly don't need to.
     * We'd also hold a lot of ram hostage (>10GB in some cases!) because all Spans objects exist
     * simultaneously even though we're not using them simultaneously.
     * However, in order to know whether a query (such as A:([pos="A.*"]) "ship") uses/produces capture groups (and how many groups)
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
     * @param weight                span weight we're querying
     * @param leafReaderContext     leaf reader we're running on
     * @param sourceHitQueryContext source HitQueryContext from HitsFromQueryParallel; we'll derive our own context from it
     * @param globalResults         global results object (must be locked before writing)
     * @param globalCapturedGroups  global captured groups object (must be locked before writing)
     * @param globalDocsProcessed   global docs retrieved counter
     * @param globalDocsCounted     global docs counter (includes ones that weren't retrieved because of max. settings)
     * @param globalHitsProcessed   global hits retrieved counter
     * @param globalHitsCounted     global hits counter (includes ones that weren't retrieved because of max. settings)
     * @param globalHitsToProcess   how many more hits to retrieve
     * @param globalHitsToCount     how many more hits to count
     */
    SpansReader(
        BLSpanWeight weight,
        LeafReaderContext leafReaderContext,
        HitQueryContext sourceHitQueryContext,

        HitsInternalMutable globalResults,
        CapturedGroupsImpl globalCapturedGroups,
        AtomicLong globalDocsProcessed,
        AtomicLong globalDocsCounted,
        AtomicLong globalHitsProcessed,
        AtomicLong globalHitsCounted,
        AtomicLong globalHitsToProcess,
        AtomicLong globalHitsToCount
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

    void initialize() {
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
     * @param spans spans to advance
     * @param liveDocs used to check if the document is still alive in the index.
     * @return true if the spans has been advanced to the next hit, false if out of hits.
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
        final ArrayList<Span[]> capturedGroups = numCaptureGroups > 0 ? new ArrayList<>() : null;

        final HitsInternalMutable results = HitsInternal.create(-1, true, true);
        final Bits liveDocs = leafReaderContext.reader().getLiveDocs();
        final LongUnaryOperator incrementCountUnlessAtMax = c -> c < this.globalHitsToCount.get() ? c + 1 : c; // only increment if doing so won't put us over the limit.
        final LongUnaryOperator incrementProcessUnlessAtMax = c -> c < this.globalHitsToProcess.get() ? c + 1 : c; // only increment if doing so won't put us over the limit.

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
                        //
                        // [NOTE JN: does this matter? and if so, doesn't it also matter that docId increases throughout the
                        //     master list? Probably not, unless we wrap the Hits inside a Spans again, which generally
                        //     require these properties to hold.]
                        //     Contexts, used for sort/group by context words, does need increasing doc ids
                        //     per leaf reader, but that property is guaranteed to hold because we don't
                        //     re-order hits from a single leaf reader, we just randomly merge them with hits
                        //     from other leafreaders without changing their order.
                        //
                        //     Still, all of the above is a smell indicating that we should try to perform more
                        //     operations per leaf reader instead of merging results from leaf readers at an early
                        //     stage like we do here.]

                        addToGlobalResults(results, capturedGroups);
                        results.clear();
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
            if (results.size() > 0) {
                addToGlobalResults(results, capturedGroups);
                results.clear();
            }
        }

        // If we're here, the loop reached its natural end - we're done.
        // Free some objects to avoid holding on to memory
        this.isDone = true;
        this.spans = null;
        this.hitQueryContext = null;
        this.leafReaderContext = null;
    }

    void addToGlobalResults(HitsInternal hits, List<Span[]> capturedGroups) {
        globalResults.addAll(hits);

        if (globalCapturedGroups != null) {
            synchronized (globalCapturedGroups) {
                HitsInternal.Iterator it = hits.iterator();
                int i = 0;
                while (it.hasNext()) {
                    Hit h = it.next().toHit();
                    globalCapturedGroups.put(h, capturedGroups.get(i));
                    ++i;
                }
                capturedGroups.clear();
            }
        }
    }

    public HitQueryContext getHitContext() {
        return hitQueryContext;
    }

    public void setCapturedGroups(CapturedGroupsImpl capturedGroups) {
        globalCapturedGroups = capturedGroups;
    }
}