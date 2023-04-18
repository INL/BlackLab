package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Objects;

import org.apache.lucene.search.TwoPhaseIterator;
import org.apache.lucene.search.spans.FilterSpans;
import org.apache.lucene.search.spans.SpanCollector;
import org.apache.lucene.search.spans.Spans;

/**
 * Wrap a Spans object to filter its spans.
 */
public abstract class BLSpansFilter extends BLSpans {

    /** Did we advance to the first hit already through the two-phase iterator?
     *  If true, startPos will contain the start position of the first hit,
     *  but we should still return -1 to indicate nextStartPosition() hasn't been called
     *  yet.
     */
    private boolean atFirstInCurrentDoc = false;

    /**
     * Start position of current hit, or (if atFirstInCurrentDoc is true) the first
     * hit to return when nextStartPosition() is called.
     */
    private int startPos = -1;

    /** The Spans object we're wrapping */
    protected final Spans source;

    public BLSpansFilter(Spans source) {
        this.source = Objects.requireNonNull(source);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof BLSpansFilter))
            return false;
        BLSpansFilter that = (BLSpansFilter) o;
        return atFirstInCurrentDoc == that.atFirstInCurrentDoc && startPos == that.startPos && source.equals(
                that.source);
    }

    @Override
    public int hashCode() {
        return Objects.hash(atFirstInCurrentDoc, startPos, source);
    }

    @Override
    public abstract String toString();

    @Override
    public void passHitQueryContextToClauses(HitQueryContext context) {
        if (source instanceof BLSpans)
            ((BLSpans) source).setHitQueryContext(context);
    }

    @Override
    public void getMatchInfo(MatchInfo[] relationInfo) {
        if (!childClausesCaptureGroups)
            return;
        if (source instanceof BLSpans) // shouldn't happen, but ok
            ((BLSpans) source).getMatchInfo(relationInfo);
    }

    @Override
    public int nextDoc() throws IOException {
        return source.nextDoc();
    }

    @Override
    public int docID() {
        return source.docID();
    }

    @Override
    public int nextStartPosition() throws IOException {
        if (atFirstInCurrentDoc)
            atFirstInCurrentDoc = false;
        else
            startPos = source.nextStartPosition();
        return startPos;
    }

    @Override
    public int startPosition() {
        if (atFirstInCurrentDoc)
            return -1;
        return startPos;
    }

    @Override
    public int endPosition() {
        if (atFirstInCurrentDoc)
            return -1;
        return source.endPosition();
    }

    @Override
    public int advance(int target) throws IOException {
        int doc = source.advance(target);
        while (doc != NO_MORE_DOCS) {
            if (twoPhaseCurrentDocMatches()) {
                break;
            }
            doc = source.nextDoc();
        }

        return doc;
    }

    private boolean twoPhaseCurrentDocMatches() throws IOException {
        atFirstInCurrentDoc = false;
        startPos = source.nextStartPosition();
        assert startPos != NO_MORE_POSITIONS;
        for (; ; ) {
            switch (accept(source)) {
            case YES:
                atFirstInCurrentDoc = true;
                return true;
            case NO:
                startPos = source.nextStartPosition();
                if (startPos != NO_MORE_POSITIONS) {
                    break;
                }
                // else fallthrough
            case NO_MORE_IN_CURRENT_DOC:
                startPos = -1;
                return false;
            }
        }
    }

    /**
     * Returns YES if the candidate should be an accepted match, NO if it should not, and
     * NO_MORE_IN_CURRENT_DOC if iteration should move on to the next document.
     */
    protected abstract FilterSpans.AcceptStatus accept(Spans candidate) throws IOException;

    @Override
    public int width() {
        return source.width();
    }

    @Override
    public void collect(SpanCollector collector) throws IOException {
        source.collect(collector);
    }

    @Override
    public float positionsCost() {
        return source.positionsCost();
    }

    @Override
    public final TwoPhaseIterator asTwoPhaseIterator() {
        TwoPhaseIterator inner = source.asTwoPhaseIterator();
        if (inner != null) {
            // wrapped instance has an approximation
            return new TwoPhaseIterator(inner.approximation()) {
                @Override
                public boolean matches() throws IOException {
                    return inner.matches() && twoPhaseCurrentDocMatches();
                }

                @Override
                public float matchCost() {
                    return inner.matchCost(); // underestimate
                }

                @Override
                public String toString() {
                    return "BLSpansWrapper@asTwoPhaseIterator(inner=" + inner + ", source=" + source + ")";
                }
            };
        } else {
            // wrapped instance has no approximation, but
            // we can still defer matching until absolutely needed.
            return new TwoPhaseIterator(source) {
                @Override
                public boolean matches() throws IOException {
                    return twoPhaseCurrentDocMatches();
                }

                @Override
                public float matchCost() {
                    return source.positionsCost(); // overestimate
                }

                @Override
                public String toString() {
                    return "BLSpansWrapper@asTwoPhaseIterator(source=" + source + ")";
                }
            };
        }
    }

}
