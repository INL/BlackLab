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
package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.search.spans.SpanCollector;
import org.apache.lucene.util.Bits;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.search.Span;

/**
 * Return all n-grams of certain lengths.
 */
class SpansNGrams extends BLSpans {
    /** Current document */
    private int currentDoc = -1;

    /** Current document length */
    private long currentDocLength = -1;

    /** Current hit start position */
    private int currentStart = -1;

    /** Current hit end position */
    private int currentEnd = -1;

    /**
     * For testing, we don't have an IndexReader available, so we use test values
     */
    private boolean useTestValues = false;

    /** Used to get the field length in tokens for a document */
    DocFieldLengthGetter lengthGetter;

    /** How much to subtract from length (for ignoring "extra closing token") */
    private int subtractClosingToken;

    /** Highest document id plus one */
    private int maxDoc;

    /** Documents that haven't been deleted */
    private Bits liveDocs;

    private boolean alreadyAtFirstMatch = false;

    private int min;

    private int max;

    /**
     * For testing, we don't have an IndexReader available, so we use test values.
     *
     * The test values are: there are 3 documents (0, 1 and 2) and each is 5 tokens
     * long.
     *
     * @param test whether or not we want to use test values
     * @param maxDoc number of docs in the (mock) test set
     */
    void setTest(boolean test, int maxDoc) {
        useTestValues = test;
        if (useTestValues)
            this.maxDoc = maxDoc;
        lengthGetter.setTest(test);
    }

    /**
     * Constructs a SpansNGrams
     *
     * @param reader the index reader, for getting field lengths
     * @param fieldName the field name, for getting field lengths
     * @param min minimum n-gram length
     * @param max maximum n-gram length
     */
    public SpansNGrams(LeafReader reader, String fieldName, int min, int max) {
        maxDoc = reader == null ? -1 : reader.maxDoc();
        liveDocs = reader == null ? null : MultiFields.getLiveDocs(reader);
        subtractClosingToken = 1;
        this.lengthGetter = new DocFieldLengthGetter(reader, fieldName);
        this.min = min;
        this.max = max;
    }

    /**
     * @return the Lucene document id of the current hit
     */
    @Override
    public int docID() {
        return currentDoc;
    }

    /**
     * @return end position of current hit
     */
    @Override
    public int endPosition() {
        if (alreadyAtFirstMatch)
            return -1; // .nextStartPosition() not called yet by client
        return currentEnd;
    }

    @Override
    public int nextDoc() throws IOException {
        alreadyAtFirstMatch = false;
        do {
            if (currentDoc >= maxDoc) {
                currentDoc = NO_MORE_DOCS;
                currentStart = currentEnd = NO_MORE_POSITIONS;
                return NO_MORE_DOCS;
            }
            boolean currentDocIsDeletedDoc;
            do {
                currentDoc++;
                currentDocIsDeletedDoc = liveDocs != null && !liveDocs.get(currentDoc);
            } while (currentDoc < maxDoc && currentDocIsDeletedDoc);
            if (currentDoc > maxDoc)
                throw new BlackLabRuntimeException("currentDoc > maxDoc!!");
            if (currentDoc == maxDoc) {
                currentDoc = NO_MORE_DOCS;
                currentStart = currentEnd = NO_MORE_POSITIONS;
                return NO_MORE_DOCS; // no more docs; we're done
            }
            currentDocLength = lengthGetter.getFieldLength(currentDoc) - subtractClosingToken;
            currentStart = currentEnd = -1;
        } while (nextStartPosition() == NO_MORE_POSITIONS);
        alreadyAtFirstMatch = true;

        return currentDoc;
    }

    /**
     * Go to next span.
     *
     * @return true if we're at the next span, false if we're done
     * @throws IOException
     */
    @Override
    public int nextStartPosition() throws IOException {
        if (alreadyAtFirstMatch) {
            alreadyAtFirstMatch = false;
            return currentStart;
        }

        if (currentDoc == NO_MORE_DOCS || currentStart == NO_MORE_POSITIONS) {
            return NO_MORE_POSITIONS;
        }

        if (currentDoc < 0)
            return -1; // haven't started

        // Next N-gram
        if (currentStart < 0 || currentEnd - currentStart >= max || currentEnd >= currentDocLength) {
            currentStart++;
            currentEnd = currentStart + min;
            if (currentEnd > currentDocLength) {
                currentStart = currentEnd = NO_MORE_POSITIONS;
                return NO_MORE_POSITIONS;
            }
        } else {
            currentEnd++;
        }
        return currentStart;
    }

    @Override
    public int advanceStartPosition(int target) throws IOException {
        if (alreadyAtFirstMatch) {
            alreadyAtFirstMatch = false;
            if (currentStart >= target)
                return currentStart;
        }
        if (target >= currentDocLength) {
            currentStart = currentEnd = NO_MORE_POSITIONS;
            return NO_MORE_POSITIONS;
        }
        // Advance us to just before the requested start point, then call nextStartPosition().
        currentStart = target - 1;
        currentEnd = currentStart + max;
        return nextStartPosition();
    }

    /**
     * Skip to the specified document (or the first document after it containing
     * hits).
     *
     * @param doc the doc number to skip to (or past)
     * @return true if we're still pointing to a valid hit, false if we're done
     * @throws IOException
     */
    @Override
    public int advance(int doc) throws IOException {
        alreadyAtFirstMatch = false;
        if (currentDoc == NO_MORE_DOCS)
            return NO_MORE_DOCS;
        if (doc >= maxDoc) {
            currentDoc = NO_MORE_DOCS;
            currentStart = currentEnd = NO_MORE_POSITIONS;
            return NO_MORE_DOCS;
        }

        if (currentDoc >= doc) {
            // We can't skip to it because we're already there or beyond.
            // But, as per spec, advance always at least advances to the next document.
            return nextDoc();
        }

        // Advance to first livedoc containing matches at or after requested docID
        currentDoc = doc - 1;
        nextDoc();
        return currentDoc;
    }

    /**
     * @return start of current span
     */
    @Override
    public int startPosition() {
        if (alreadyAtFirstMatch)
            return -1; // .nextStartPosition() not called yet by client
        return currentStart;
    }

    @Override
    public String toString() {
        return "NGRAMS(" + min + ", " + max + ")";
    }

    @Override
    protected void passHitQueryContextToClauses(HitQueryContext context) {
        // no clause, nothing to do
    }

    @Override
    public void getCapturedGroups(Span[] capturedGroups) {
        // no clause, no groups
    }

    @Override
    public int width() {
        return 0;
    }

    @Override
    public void collect(SpanCollector collector) throws IOException {
        // nothing to collect
    }

    @Override
    public float positionsCost() {
        return 0;
    }

}
