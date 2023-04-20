package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.MultiBits;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.TwoPhaseIterator;
import org.apache.lucene.search.spans.SpanCollector;
import org.apache.lucene.util.Bits;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.search.BlackLabIndexAbstract;

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

    /** Used to get the field length in tokens for a document */
    final DocFieldLengthGetter lengthGetter;

    /** Highest document id plus one */
    private int maxDoc;

    /** Documents that haven't been deleted */
    private final Bits liveDocs;

    private boolean alreadyAtFirstMatch = false;

    private final int min;

    private final int max;

    /**
     * For testing, we don't have an IndexReader available, so we use test values.
     *
     * The test values are: there are 3 documents (0, 1 and 2) and each is 5 tokens
     * long.
     *
     * @param maxDoc number of docs in the (mock) test set
     */
    void setTest(int maxDoc) {
        // For testing, we don't have an IndexReader available, so we use test values
        this.maxDoc = maxDoc;
        lengthGetter.setTest(true);
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
        liveDocs = reader == null ? null : MultiBits.getLiveDocs(reader);
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
    public int nextDoc() {
        alreadyAtFirstMatch = false;
        do {
            if (currentDoc >= maxDoc) {
                currentDoc = NO_MORE_DOCS;
                currentStart = currentEnd = NO_MORE_POSITIONS;
                return NO_MORE_DOCS;
            }
            boolean currentDocIsDeletedDoc;
            boolean currentDocIsMetadataDoc;
            do {
                currentDoc++;
                currentDocIsDeletedDoc = currentDoc < maxDoc && liveDocs != null && !liveDocs.get(currentDoc);
            } while (currentDoc < maxDoc && currentDocIsDeletedDoc);
            if (currentDoc > maxDoc)
                throw new BlackLabRuntimeException("currentDoc > maxDoc!!");
            if (currentDoc == maxDoc) {
                currentDoc = NO_MORE_DOCS;
                currentStart = currentEnd = NO_MORE_POSITIONS;
                return NO_MORE_DOCS; // no more docs; we're done
            }
            currentDocLength = lengthGetter.getFieldLength(currentDoc) - BlackLabIndexAbstract.IGNORE_EXTRA_CLOSING_TOKEN;
            currentStart = currentEnd = -1;
        } while (nextStartPosition() == NO_MORE_POSITIONS);
        alreadyAtFirstMatch = true;

        return currentDoc;
    }

    /**
     * Go to next span.
     *
     * @return true if we're at the next span, false if we're done
     */
    @Override
    public int nextStartPosition() {
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
    public int advanceStartPosition(int target) {
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
    public void getMatchInfo(MatchInfo[] relationInfo) {
        // no clause, no groups
    }

    @Override
    public int width() {
        return 0;
    }

    @Override
    public void collect(SpanCollector collector) {
        // nothing to collect
    }

    @Override
    public float positionsCost() {
        return 0;
    }

    @Override
    public TwoPhaseIterator asTwoPhaseIterator() {
        DocIdSetIterator approximation = new DocIdSetIterator() {
            int doc = -1;

            @Override
            public int docID() {
                return doc;
            }

            @Override
            public int nextDoc() {
                return advance(doc + 1);
            }

            @Override
            public int advance(int target) {
                if (target >= maxDoc) {
                    return doc = NO_MORE_DOCS;
                }
                return doc = target;
            }

            @Override
            public long cost() {
                return maxDoc;
            }
        };
        return new TwoPhaseIterator(approximation) {
            @Override
            public boolean matches() throws IOException {
                // Any document that can fit the smallest N-gram is a match
                return lengthGetter.getFieldLength(approximation.docID()) >= min;
            }

            @Override
            public float matchCost() {
                return 0;
            }
        };
    }
}
