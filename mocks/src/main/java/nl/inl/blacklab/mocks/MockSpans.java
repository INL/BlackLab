package nl.inl.blacklab.mocks;

import java.io.IOException;

import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.spans.SpanCollector;
import org.apache.lucene.util.BytesRef;

import nl.inl.blacklab.analysis.PayloadUtils;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.lucene.BLSpans;
import nl.inl.blacklab.search.lucene.HitQueryContext;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.lucene.SpanGuarantees;

/**
 * Stub Spans class for testing. Takes arrays and iterates through 'hits' from
 * these arrays.
 */
public class MockSpans extends BLSpans {

    private final class MockPostingsEnum extends PostingsEnum {
        private int currentDoc = -1;
        private int currentHit = -1;
        private boolean alreadyAtFirstMatch = false;

        public MockPostingsEnum() {
            // NOP
        }

        @Override
        public int nextDoc() {
            if (currentDoc != NO_MORE_DOCS) {
                alreadyAtFirstMatch = false;
                while (currentHit < doc.length && (currentHit == -1 || doc[currentHit] == currentDoc)) {
                    currentHit++;
                }
                if (currentHit >= doc.length) {
                    currentDoc = NO_MORE_DOCS;
                    return NO_MORE_DOCS;
                }
                alreadyAtFirstMatch = true;
                currentDoc = doc[currentHit];
            }
            return currentDoc;
        }

        @Override
        public int docID() {
            return currentDoc;
        }

        @Override
        public long cost() {
            return 0;
        }

        @Override
        public int advance(int target) {
            if (currentDoc != NO_MORE_DOCS) {
                alreadyAtFirstMatch = false;
                do {
                    currentDoc = nextDoc();
                } while (currentDoc != NO_MORE_DOCS && currentDoc < target);
            }
            return currentDoc;
        }

        @Override
        public int startOffset() {
            if (currentHit < 0 || alreadyAtFirstMatch)
                return -1;
            if (currentDoc == NO_MORE_DOCS || currentHit >= doc.length || doc[currentHit] != currentDoc)
                return NO_MORE_POSITIONS;
            return start[currentHit];
        }

        @Override
        public int nextPosition() {
            if (currentDoc == NO_MORE_DOCS)
                return NO_MORE_POSITIONS;
            if (alreadyAtFirstMatch) {
                alreadyAtFirstMatch = false;
                return startOffset();
            }
            if (currentHit < 0)
                throw new BlackLabRuntimeException("nextDoc() not called yet!");
            if (currentHit < doc.length && doc[currentHit] == currentDoc) {
                currentHit++;
                return startOffset(); // may return NO_MORE_POSITIONS if we're at the next doc
            }
            return NO_MORE_POSITIONS;
        }

        @Override
        public BytesRef getPayload() {
            if (payloads == null)
                return null;
            if (currentHit < 0 || alreadyAtFirstMatch)
                return null;
            if (currentDoc == NO_MORE_DOCS || currentHit >= doc.length || doc[currentHit] != currentDoc)
                return null;
            return payloads[currentHit];
        }

        @Override
        public int freq() {
            // Find start of next document
            int i;
            for (i = currentHit + 1; i < doc.length && doc[i] == currentDoc; i++) {
                // NOP
            }
            return i - currentHit;
        }

        @Override
        public int endOffset() {
            if (currentHit < 0 || alreadyAtFirstMatch)
                return -1;
            if (currentDoc == NO_MORE_DOCS || currentHit >= doc.length || doc[currentHit] != currentDoc)
                return NO_MORE_POSITIONS;
            return end[currentHit];
        }
    }

    private final int[] doc;

    private final int[] start;

    private final int[] end;

    private final MatchInfo[][] matchInfo;

    private final MockPostingsEnum postings;

    private final MyTermSpans spans;

    private int currentHitIndex;

    private boolean noMoreDocs = false;

    private boolean noMoreHitsInDoc = true;

    private BytesRef[] payloads = null;

    private int endPos = -1;

    public MockSpans(int[] doc, int[] start, int[] end, SpanGuarantees guarantees) {
        this(doc, start, end, null, guarantees);
    }

    public void setGuarantees(SpanGuarantees guarantees) {
        this.guarantees = guarantees;
    }

    public MockSpans(int[] doc, int[] start, int[] end, MatchInfo[][] matchInfo, SpanGuarantees guarantees) {
        super(guarantees);
        this.doc = doc;
        this.start = start;
        this.end = end;
        this.matchInfo = matchInfo;
        postings = new MockPostingsEnum();
        spans = new MyTermSpans(postings, new Term("test", "dummy"), 1);
    }

    public MockSpans(int[] doc, int[] start, int[] end) {
        this(doc, start, end, null, SpanGuarantees.SORTED_UNIQUE);
    }

    @Override
    public int docID() {
        return spans.docID();
    }

    @Override
    public int endPosition() {
        return endPos; //spans.endPosition();
    }

    private void setPayloadsInt(int[] aStart, int[] aEnd, boolean[] aIsPrimary) {
        this.payloads = new BytesRef[aEnd.length];
        for (int i = 0; i < aEnd.length; i++) {
            BytesRef payload = PayloadUtils.tagEndPositionPayload(aStart[i], aEnd[i],
                    BlackLabIndex.IndexType.EXTERNAL_FILES);
            if (aIsPrimary != null)
                payload = PayloadUtils.addIsPrimary(aIsPrimary[i], payload);
            this.payloads[i] = payload;
        }
    }

    private void setPayloadsRelationsInt(int[] aStart, int[] aEnd, boolean[] aIsPrimary) {
        this.payloads = new BytesRef[aEnd.length];
        for (int i = 0; i < aEnd.length; i++) {
            MatchInfo relInfo = new MatchInfo("test", false, aStart[i], aStart[i], aEnd[i], aEnd[i]);
            BytesRef payload = relInfo.serialize(aStart[i]);
            if (aIsPrimary != null)
                payload = PayloadUtils.addIsPrimary(aIsPrimary[i], payload);
            this.payloads[i] = payload;
        }
    }

    private void setPayloadsMatchInfo(int[] aStart, int[] aEnd, MatchInfo[] matchInfo) {
        this.payloads = new BytesRef[aEnd.length];
        for (int i = 0; i < aEnd.length; i++) {
            this.payloads[i] = matchInfo[i].serialize(aStart[i]);
        }
    }

    @Override
    public int nextDoc() throws IOException {
        if (noMoreDocs)
            throw new BlackLabRuntimeException("Called nextDoc() on exhausted spans!");
        endPos = -1;
        int docId = spans.nextDoc();
        if (docId == NO_MORE_DOCS)
            noMoreDocs = true;
        else
            noMoreHitsInDoc = false;
        currentHitIndex = -1;
        return docId;
    }

    @Override
    public int nextStartPosition() throws IOException {
        if (noMoreHitsInDoc)
            throw new BlackLabRuntimeException("Called nextStartPosition() on hit-exhausted spans!");
        int startPos = spans.nextStartPosition();
        currentHitIndex++;
        endPos = startPos == NO_MORE_POSITIONS ? NO_MORE_POSITIONS : postings.endOffset();
        if (startPos == NO_MORE_POSITIONS) {
            noMoreHitsInDoc = true;
        }
        return startPos;
    }

    @Override
    public int advance(int target) throws IOException {
        if (noMoreDocs)
            throw new BlackLabRuntimeException("Called advance() on exhausted spans!");
        endPos = -1;
        if (target <= spans.docID())
            throw new IllegalArgumentException("target <= doc (" + target + " <= " + spans.docID() + ")");
        int docId = spans.advance(target);
        if (docId == NO_MORE_DOCS)
            noMoreDocs = true;
        else
            noMoreHitsInDoc = false;
        currentHitIndex = -1;
        return docId;
    }

    @Override
    public int startPosition() {
        return spans.startPosition();
    }

    @Override
    public void passHitQueryContextToClauses(HitQueryContext context) {
        // just ignore this here
    }

    @Override
    public void getMatchInfo(MatchInfo[] matchInfo) {
        for (int i = 0; i < matchInfo.length; i++) {
            matchInfo[i] = this.matchInfo[currentHitIndex][i];
        }
    }

    public static MockSpans emptySpans() {
        return new MockSpans(new int[0], new int[0], new int[0]);
    }

    public static MockSpans single(int doc, int start, int end) {
        return new MockSpans(new int[] { doc }, new int[] { start }, new int[] { end });
    }

    public static MockSpans fromLists(int[] doc, int[] start, int[] end) {
        return new MockSpans(doc, start, end);
    }

    public static MockSpans withEndInPayload(int[] aDoc, int[] aStart, int[] aEnd, boolean[] aIsPrimary) {
        MockSpans spans = MockSpans.singleWordSpans(aDoc, aStart);
        spans.setPayloadsInt(aStart, aEnd, aIsPrimary);
        return spans;
    }

    public static MockSpans withRelationInfoInPayload(int[] aDoc, int[] aStart, int[] aEnd, boolean[] aIsPrimary) {
        MockSpans spans = MockSpans.singleWordSpans(aDoc, aStart);
        spans.setPayloadsRelationsInt(aStart, aEnd, aIsPrimary);
        return spans;
    }

    public static MockSpans withMatchInfoInPayload(int[] aDoc, int[] aStart, int[] aEnd, MatchInfo[] aMatchInfo) {
        MockSpans spans = MockSpans.singleWordSpans(aDoc, aStart);
        spans.setPayloadsMatchInfo(aStart, aEnd, aMatchInfo);
        return spans;
    }

    private static MockSpans singleWordSpans(int[] aDoc, int[] aStart) {
        int[] aEnd = new int[aStart.length];
        for (int i = 0; i < aStart.length; i++) {
            aEnd[i] = aStart[i] + 1;
        }
        return fromLists(aDoc, aStart, aEnd);
    }

    @Override
    public int width() {
        return 0;
    }

    @Override
    public void collect(SpanCollector collector) throws IOException {
        collector.collectLeaf(postings, -1, null);
    }

    @Override
    public float positionsCost() {
        return start.length;
    }

}
