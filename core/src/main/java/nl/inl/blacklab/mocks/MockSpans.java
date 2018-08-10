/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
/**
 *
 */
package nl.inl.blacklab.mocks;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.spans.SpanCollector;
import org.apache.lucene.util.BytesRef;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.search.Span;
import nl.inl.blacklab.search.lucene.BLSpans;
import nl.inl.blacklab.search.lucene.HitQueryContext;

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
        public int nextDoc() throws IOException {
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
        public int advance(int target) throws IOException {
            if (currentDoc != NO_MORE_DOCS) {
                alreadyAtFirstMatch = false;
                do {
                    currentDoc = nextDoc();
                } while (currentDoc != NO_MORE_DOCS && currentDoc < target);
            }
            return currentDoc;
        }

        @Override
        public int startOffset() throws IOException {
            if (currentHit < 0 || alreadyAtFirstMatch)
                return -1;
            if (currentDoc == NO_MORE_DOCS || currentHit >= doc.length || doc[currentHit] != currentDoc)
                return NO_MORE_POSITIONS;
            return start[currentHit];
        }

        @Override
        public int nextPosition() throws IOException {
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
        public BytesRef getPayload() throws IOException {
            if (payloads == null)
                return null;
            if (currentHit < 0 || alreadyAtFirstMatch)
                return null;
            if (currentDoc == NO_MORE_DOCS || currentHit >= doc.length || doc[currentHit] != currentDoc)
                return null;
            return new BytesRef(payloads[currentHit]);
        }

        @Override
        public int freq() throws IOException {
            // Find start of next document
            int i;
            for (i = currentHit + 1; i < doc.length && doc[i] == currentDoc; i++) {
                // NOP
            }
            return i - currentHit;
        }

        @Override
        public int endOffset() throws IOException {
            if (currentHit < 0 || alreadyAtFirstMatch)
                return -1;
            if (currentDoc == NO_MORE_DOCS || currentHit >= doc.length || doc[currentHit] != currentDoc)
                return NO_MORE_POSITIONS;
            return end[currentHit];
        }
    }

    int[] doc;

    int[] start;

    int[] end;

    private MockPostingsEnum postings;

    private MyTermSpans spans;

    private boolean noMoreDocs = false;

    private boolean noMoreHitsInDoc = true;

    byte[][] payloads = null;

    private int endPos = -1;

    public MockSpans(int[] doc, int[] start, int[] end) {
        this.doc = doc;
        this.start = start;
        this.end = end;
        postings = new MockPostingsEnum();
        spans = new MyTermSpans(postings, new Term("test", "dummy"), 1);
    }

    @Override
    public int docID() {
        return spans.docID();
    }

    @Override
    public int endPosition() {
        return endPos; //spans.endPosition();
    }

    private void setPayloadsInt(int[] aEnd) {
        this.payloads = new byte[aEnd.length][];
        for (int i = 0; i < aEnd.length; i++) {
            this.payloads[i] = ByteBuffer.allocate(4).putInt(aEnd[i]).array();
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
        return docId;
    }

    @Override
    public int nextStartPosition() throws IOException {
        if (noMoreHitsInDoc)
            throw new BlackLabRuntimeException("Called nextStartPosition() on hit-exhausted spans!");
        int startPos = spans.nextStartPosition();
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
    public void getCapturedGroups(Span[] capturedGroups) {
        // just ignore this here
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

    public static MockSpans withEndInPayload(int[] aDoc, int[] aStart, int[] aEnd) {
        MockSpans spans = MockSpans.singleWordSpans(aDoc, aStart);
        spans.setPayloadsInt(aEnd);
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
        return 0;
    }

}
