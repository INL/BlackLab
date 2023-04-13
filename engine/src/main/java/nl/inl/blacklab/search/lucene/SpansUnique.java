package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.search.spans.SpanCollector;
import org.apache.lucene.search.spans.Spans;

import nl.inl.blacklab.search.Span;

/**
 * Remove consecutive duplicate hits from a source spans.
 */
class SpansUnique extends BLSpans {

    private final BLSpans src;

    private int currentDoc = -1;

    private int currentStart = Spans.NO_MORE_POSITIONS;

    /**
     * Construct SpansUnique.
     *
     * @param src (startpoint-sorted) Spans to make unique
     */
    public SpansUnique(BLSpans src) {
        this.src = src; // NOTE: must be startpoint sorted! (caller's responsibility)
    }

    @Override
    public int docID() {
        return currentDoc;
    }

    @Override
    public int startPosition() {
        return currentStart;
    }

    @Override
    public int endPosition() {
        return src.endPosition();
    }

    @Override
    public int nextDoc() throws IOException {
        if (currentDoc != NO_MORE_DOCS) {
            currentDoc = src.nextDoc();
            currentStart = -1;
        }
        return currentDoc;
    }

    @Override
    public int nextStartPosition() throws IOException {
        int prevStart, prevEnd;
        if (currentStart != NO_MORE_POSITIONS) {
            do {
                prevStart = currentStart;
                prevEnd = src.endPosition();
                currentStart = src.nextStartPosition();
            } while (prevStart == currentStart && prevEnd == src.endPosition());
        }
        return currentStart;
    }

    @Override
    public int advanceStartPosition(int target) throws IOException {
        int prevStart, prevEnd;
        if (currentStart != NO_MORE_POSITIONS) {
            prevStart = currentStart;
            prevEnd = src.endPosition();
            currentStart = src.advanceStartPosition(target);
            while (prevStart == currentStart && prevEnd == src.endPosition()) {
                prevStart = currentStart;
                prevEnd = src.endPosition();
                currentStart = src.nextStartPosition();
            }
        }
        return currentStart;
    }

    @Override
    public int advance(int target) throws IOException {
        if (currentDoc != NO_MORE_DOCS) {
            if (target > currentDoc) {
                // Skip to the target doc
                currentDoc = src.advance(target);
                currentStart = -1;
            } else {
                // We're already in or past the target doc. Just go to the next doc.
                nextDoc();
            }
        }
        return currentDoc;
    }

    @Override
    public String toString() {
        return "UniqueSpans(" + src.toString() + ")";
    }

    @Override
    public void passHitQueryContextToClauses(HitQueryContext context) {
        src.setHitQueryContext(context);
    }

    @Override
    public void getCapturedGroups(RelationInfo[] capturedGroups) {
        if (!childClausesCaptureGroups)
            return;
        src.getCapturedGroups(capturedGroups);
    }

    @Override
    public int width() {
        return src.width();
    }

    @Override
    public void collect(SpanCollector collector) throws IOException {
        src.collect(collector);
    }

    @Override
    public float positionsCost() {
        return src.positionsCost();
    }

}
