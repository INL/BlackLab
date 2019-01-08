package nl.inl.blacklab.search.results;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.spans.Spans;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.InterruptedSearch;
import nl.inl.blacklab.exceptions.WildcardTermTooBroad;
import nl.inl.blacklab.search.Span;
import nl.inl.blacklab.search.lucene.BLSpans;
import nl.inl.blacklab.search.lucene.HitQueryContext;
import nl.inl.util.ThreadPauser;

/**
 * A Hits object that is filled from a BLSpanQuery.
 */
public class SpansReader {
    /** Our Spans object, which may not have been fully read yet. */
    private BLSpans spans;

    /** docBase of the segment we're currently in */
    private int docBase;

    private List<Hit> results = new ArrayList<>();

    /** Our captured groups, or null if we have none. */
    private CapturedGroupsImpl capturedGroups = null;
    
    /** Did we completely read our Spans object? */
    private boolean spansFullyRead = true;

    private Lock ensureHitsReadLock = new ReentrantLock();
    
    /** Context of our query; mostly used to keep track of captured groups. */
    private HitQueryContext hitQueryContext;
    
    private ThreadPauser threadPauser = ThreadPauser.create();

    private boolean interrupted = false;

    private boolean shouldCancel = false;

    /**
     * Construct a Hits object from a SpanQuery.
     *
     * @param queryInfo query info
     * @param sourceQuery the query to execute to get the hits
     * @param searchSettings search settings
     * @throws WildcardTermTooBroad if the query is overly broad (expands to too many terms)
     */
    protected SpansReader(BLSpans spans, int docBase, HitQueryContext hitQueryContext) {
        this.docBase = docBase;
        this.spans = spans;
        
        // Update the hit query context with our new spans,
        // and notify the spans of the hit query context
        this.hitQueryContext = hitQueryContext.copyWith(spans);
        spans.setHitQueryContext(this.hitQueryContext); // let captured groups register themselves
        if (hitQueryContext.numberOfCapturedGroups() > 0) {
            capturedGroups = new CapturedGroupsImpl(hitQueryContext.getCapturedGroupNames());
        }
        
        int doc;
        try {
            doc = spans.nextDoc();
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
        spansFullyRead = doc == DocIdSetIterator.NO_MORE_DOCS;
    }

    public boolean isInterrupted() {
        return interrupted;
    }
    
    @Override
    public String toString() {
        return "SpansReader(spans=" + spans +  ", fullyRead=" + spansFullyRead + ", hitsSoFar=" + results.size() + ")";
    }
    
    public int processedTotal() {
        ensureResultsRead(Results.NO_LIMIT);
        return results.size();
    }
    
    public int processedSoFar() {
        return results.size();
    }

    public boolean processedAtLeast(int lowerBound) {
        ensureResultsRead(lowerBound);
        return results.size() >= lowerBound;
    }

    public boolean done() {
        return spansFullyRead;
    }
    
    public List<Hit> resultsList() {
        return results;
    }

    /**
     * Ensure that we have read at least as many hits as specified in the parameter.
     *
     * @param number the minimum number of hits that will have been read when this
     *            method returns (unless there are fewer hits than this); if
     *            negative (e.g. Results.NO_LIMIT), reads all hits
     */
    public void ensureResultsRead(int number) {
        try {
            // Prevent locking when not required
            if (spansFullyRead || (number >= 0 && results.size() > number))
                return;
    
            // At least one hit needs to be fetched.
            // Make sure we fetch at least FETCH_HITS_MIN while we're at it, to avoid too much locking.
            if (number >= 0 && number - results.size() < Hits.FETCH_HITS_MIN)
                number = results.size() + Hits.FETCH_HITS_MIN;
    
            while (!ensureHitsReadLock.tryLock()) {
                /*
                 * Another thread is already counting, we don't want to straight up block until it's done
                 * as it might be counting/retrieving all results, while we might only want trying to retrieve a small fraction
                 * So instead poll our own state, then if we're still missing results after that just count them ourselves
                 */
                Thread.sleep(50);
                if (spansFullyRead || (number >= 0 && results.size() >= number))
                    return;
            }
            try {
                boolean readAllHits = number < 0;
                while (!spansFullyRead && (readAllHits || results.size() < number)) {
    
                    // Pause if asked
                    threadPauser.waitIfPaused();
                    
                    if (shouldCancel) {
                        // We've been asked to stop fetching hits.
                        throw new InterruptedException();
                    }
    
                    // Advance to next hit
                    int start = spans.nextStartPosition();
                    if (start == Spans.NO_MORE_POSITIONS) {
                        int doc = spans.nextDoc();
                        if (doc != DocIdSetIterator.NO_MORE_DOCS) {
                            // Go to first hit in doc
                            start = spans.nextStartPosition();
                        } else {
                            // Spans exhausted
                            spansFullyRead = true;
                        }
                    }

                    if (!spansFullyRead) {
                        // Count the hit and add it (unless we've reached the maximum number of hits we want)
                        Hit hit = Hit.create(spans.docID() + docBase, spans.startPosition(), spans.endPosition());
                        if (capturedGroups != null) {
                            Span[] groups = new Span[hitQueryContext.numberOfCapturedGroups()];
                            hitQueryContext.getCapturedGroups(groups);
                            capturedGroups.put(hit, groups);
                        }
                        results.add(hit);
                    }
                }
            } catch (InterruptedException e) {
                // We've stopped retrieving/counting
                interrupted = true;
                throw e;
            } catch (IOException e) {
                throw BlackLabRuntimeException.wrap(e);
            } finally {
                ensureHitsReadLock.unlock();
            }
        } catch (InterruptedException e) {
            throw new InterruptedSearch(e);
        }
    }

    public void interrupt() {
        shouldCancel = true;
    }
    
    public CapturedGroupsImpl capturedGroups() {
        return capturedGroups;
    }

}
