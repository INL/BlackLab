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
import org.apache.lucene.search.spans.SpanCollector;

import nl.inl.blacklab.search.Span;

/**
 * Expands the source spans to the left and right to form N-grams.
 *
 * This is used to speed up queries of the form
 * <code>[]{2,4} containing "a" "b"</code>: N-grams of certain lengths
 * containing some construct.
 *
 * Note that this class will generate all possible N-grams, so it will likely
 * produce multiple N-gram hits per source hit.
 *
 * Note that the hits coming out of this class may contain duplicates and will
 * not always be properly sorted. Both are undesirable; the user doesn't want to
 * see duplicates, and Lucene expects Spans to always be sorted by start point
 * (and possible by end point after that, although that is not certain; should
 * be checked).
 *
 * Therefore, objects of this class should be wrapped in a class that sort the
 * matches per document and eliminates duplicates (hence the 'raw' in the name -
 * not suitable for consumption yet).
 */
class SpansFilterNGramsRaw extends BLSpans {

    /** The clause to expand */
    private BLSpans clause;

    /** Whether or not there's more docs in the clause */
    private int currentDoc = -1;

    /** Current startPosition() in the clause */
    private int srcStart = -1;

    /** End of the current source hit */
    private int srcEnd = -1;

    /** How to expand the hits */
    private SpanQueryPositionFilter.Operation op;

    /** Minimum number of tokens to expand */
    private int min;

    /** Maximum number of tokens to expand (MAX_UNLIMITED = infinite) */
    private int max;

    /** How to adjust the left edge of the producer hits (N-grams) while matching */
    private int leftAdjust;

    /** How to adjust the right edge of the producer hits (N-grams) while matching */
    private int rightAdjust;

    /** Start of the current expanded hit */
    private int start = -1;

    /** End of the current expanded hit */
    private int end = -1;

    /** For which document do we have the token length? */
    private int tokenLengthDocId = -1;

    /** Token length for the doc specified in tokenLengthDocId */
    private int tokenLength;

    /** Used to get the field length in tokens for a document */
    DocFieldLengthGetter lengthGetter;

    /** How much to subtract from length (for ignoring "extra closing token") */
    private int subtractClosingToken;

    private boolean alreadyAtFirstHit;

    public SpansFilterNGramsRaw(LeafReader reader, String fieldName, BLSpans clause,
            SpanQueryPositionFilter.Operation op, int min, int max, int leftAdjust, int rightAdjust) {
        subtractClosingToken = 1;
        if (op != SpanQueryPositionFilter.Operation.CONTAINING_AT_END && op != SpanQueryPositionFilter.Operation.ENDS_AT
                && op != SpanQueryPositionFilter.Operation.MATCHES) {
            // We need to know document length to properly do expansion to the right
            // TODO: cache this in BlackLabIndex..?
            lengthGetter = new DocFieldLengthGetter(reader, fieldName);
        }
        this.clause = clause;
        this.op = op;
        this.min = min;
        this.max = max == -1 ? MAX_UNLIMITED : max;
        if (min > this.max)
            throw new IllegalArgumentException("min > max");
        if (min < 0 || this.max < 0)
            throw new IllegalArgumentException("Expansions cannot be negative");
        this.leftAdjust = leftAdjust;
        this.rightAdjust = rightAdjust;
    }

    /**
     * For test, we don't have an index reader, so use default values (all docs are
     * 5 tokens long)
     *
     * @param b true if we're testing, false if not
     */
    public void setTest(boolean b) {
        if (lengthGetter != null)
            lengthGetter.setTest(b);
    }

    @Override
    public int docID() {
        return currentDoc;
    }

    @Override
    public int endPosition() {
        if (alreadyAtFirstHit)
            return -1; // .nextStartPosition() not called yet
        return end;
    }

    @Override
    public int nextDoc() throws IOException {
        alreadyAtFirstHit = false;
        if (currentDoc != NO_MORE_DOCS) {
            do {
                currentDoc = clause.nextDoc();
                if (currentDoc == NO_MORE_DOCS)
                    return NO_MORE_DOCS;
                srcStart = srcEnd = start = end = -1;
                goToNextClauseSpan();
            } while (start == NO_MORE_POSITIONS);
            alreadyAtFirstHit = true;
        }
        return currentDoc;
    }

    @Override
    public int nextStartPosition() throws IOException {
        if (alreadyAtFirstHit) {
            alreadyAtFirstHit = false;
            return start;
        }
        if (currentDoc == NO_MORE_DOCS || srcStart == NO_MORE_POSITIONS)
            return NO_MORE_POSITIONS;

        // Is there another n-gram for this source hit?
        boolean atValidNGram = false;
        switch (op) {
        case CONTAINING:
            end++;
            while (!atValidNGram) {
                if (end - start <= max && end <= tokenLength) {
                    // N-gram is within allowed size and not beyond end of document
                    atValidNGram = true;
                    break;
                }
                // N-gram became too long, or we went past end of document.
                // On to next start position.
                start++;
                if (start + leftAdjust > srcStart) {
                    // No more start positions for N-gram; done with this source hit.
                    break;
                }
                end = Math.max(srcEnd - rightAdjust, start + min); // minimum length still containing source hit
                if (end > tokenLength) {
                    // Smallest n-gram at start position went past the end of the document.
                    // Done with this source hit.
                    break;
                }
            }
            break;
        case CONTAINING_AT_START:
            end++;
            if (end - start > max || end > tokenLength) {
                // N-gram became too long, or we went past end of document.
                // We're done with this source hit.
                break;
            }
            atValidNGram = true;
            break;
        case CONTAINING_AT_END:
            // On to next start position.
            start++;
            if (end - start < min || start + leftAdjust > srcStart) {
                // No more start positions for N-gram; done with this source hit.
                break;
            }
            atValidNGram = true;
            break;
        case WITHIN:
            end++;
            if (end - start > max || end + rightAdjust > srcEnd) {
                // N-gram became too long, or we went past end of source hit.
                // On to next start position.
                start++;
                if (start + min + rightAdjust > srcEnd) {
                    // No more start positions for N-gram; done with this source hit.
                    break;
                }
                end = start + min;
            }
            atValidNGram = true;
            break;

        case MATCHES:
            // Matches only produces one hit per source hit,
            // so we're always done with the source hit here.
            break;

        case STARTS_AT:
        case ENDS_AT:
            throw new UnsupportedOperationException("STARTS_AT and ENDS_AT not implemented for SpansExpandToNGram");
        }
        if (atValidNGram)
            return start;

        return goToNextClauseSpan();
    }

    @Override
    public int advance(int doc) throws IOException {
        alreadyAtFirstHit = false;
        if (currentDoc != NO_MORE_DOCS) {
            if (currentDoc < doc) {
                currentDoc = clause.advance(doc);
                if (currentDoc != NO_MORE_DOCS) {
                    while (true) {
                        srcStart = srcEnd = start = end = -1;
                        goToNextClauseSpan();
                        if (start != NO_MORE_POSITIONS) {
                            alreadyAtFirstHit = true;
                            return currentDoc;
                        }
                        currentDoc = clause.nextDoc();
                        if (currentDoc == NO_MORE_DOCS)
                            return NO_MORE_DOCS;
                    }
                }
            } else {
                nextDoc(); // per Lucene's specification, always at least go to the next doc
            }
        }
        return currentDoc;
    }

    /**
     * Go to the next position in the source clause and reset the expansion process.
     *
     * Note that we may discover we can't do the minimum expansion (because the hit
     * is at the start of the document, for example), so we may have to advance the
     * clause again, and may actually run out of hits while doing so.
     *
     * @return the start position if we're at a valid hit and have reset the
     *         expansion, NO_MORE_POSITIONS if we're done
     * @throws IOException
     */
    private int goToNextClauseSpan() throws IOException {
        srcStart = clause.nextStartPosition();
        if (srcStart == NO_MORE_POSITIONS) {
            start = end = srcEnd = NO_MORE_POSITIONS;
            return NO_MORE_POSITIONS;
        }
        while (true) {
            // Determine limits and set to initial n-gram
            srcEnd = clause.endPosition();
            switch (op) {
            case MATCHES:
                int len = srcEnd - rightAdjust - (srcStart - leftAdjust);
                if (len >= min && len <= max) {
                    // Only one n-gram
                    start = srcStart - leftAdjust;
                    end = srcEnd - rightAdjust;
                    return start;
                }
                // Hit length doesn't match, so no n-grams.
                break;
            case CONTAINING:
                // Do we know this document's length already?
                if (currentDoc != tokenLengthDocId) {
                    // No, determine length now
                    tokenLengthDocId = currentDoc;
                    tokenLength = lengthGetter.getFieldLength(tokenLengthDocId) - subtractClosingToken;
                }

                // First n-gram containing source hit: minimum start position,
                // smallest possible length
                start = srcEnd - rightAdjust - max;
                if (start < 0)
                    start = 0;
                while (start <= srcStart - leftAdjust && start <= srcEnd - rightAdjust - min) {
                    end = Math.max(srcEnd - rightAdjust, start + min); // minimum length still containing source hit
                    if (end <= start + max && end <= tokenLength) {
                        // Valid n-gram containing hit.
                        return start;
                    }
                    start++; // couldn't get valid match from this start position; try next
                }
                // Cannot make valid n-gram (too long or would go past end of document), so no n-grams.
                break;
            case CONTAINING_AT_START:
                // Do we know this document's length already?
                if (currentDoc != tokenLengthDocId) {
                    // No, determine length now
                    tokenLengthDocId = currentDoc;
                    tokenLength = lengthGetter.getFieldLength(tokenLengthDocId) - subtractClosingToken;
                }

                // First n-gram containing source hit: minimum start position,
                // smallest possible length
                start = srcStart - leftAdjust;
                end = Math.max(srcEnd - rightAdjust, start + min); // minimum length still containing source hit
                if (end <= start + max && end <= tokenLength) {
                    // Valid n-gram containing hit.
                    return start;
                }
                // First n-gram would go past end of document, so no n-grams.
                break;
            case CONTAINING_AT_END:
                // First n-gram containing source hit: minimum start position, maximum length.
                start = srcEnd - rightAdjust - max;
                if (start < 0)
                    start = 0;
                end = srcEnd - rightAdjust;
                if (start <= srcStart - leftAdjust) {
                    // Valid n-gram containing hit.
                    return start;
                }
                // Hit too large to be contained by specified n-grams.
                break;
            case WITHIN:
                // First n-gram within source hit: starts at source start, minimum length
                start = srcStart - leftAdjust;
                end = start + min;
                if (end <= srcEnd - rightAdjust) {
                    // n-gram is contained within hit, so valid.
                    return start;
                }
                // Minimum length n-gram doesn't fit in hit, so no n-grams.
                break;
            case STARTS_AT:
            case ENDS_AT:
                throw new UnsupportedOperationException("STARTS_AT and ENDS_AT not implemented for SpansExpandToNGram");
            }

            // No, try the next hit, if there is one
            if (nextStartPosition() == NO_MORE_POSITIONS) {
                start = end = srcStart = srcEnd = NO_MORE_POSITIONS;
                return NO_MORE_POSITIONS; // No hits left, we're done
            }
        }
    }

    /**
     * @return start of the current hit
     */
    @Override
    public int startPosition() {
        if (alreadyAtFirstHit)
            return -1; // .nextStartPosition() not called yet
        return start;
    }

    @Override
    public String toString() {
        String adj = (leftAdjust != 0 || rightAdjust != 0) ? ", " + leftAdjust + ", " + rightAdjust : "";
        return "SpansFilterNGrams(" + clause + ", " + op + ", " + min + ", " + inf(max) + adj + ")";
    }

    @Override
    public void passHitQueryContextToClauses(HitQueryContext context) {
        clause.setHitQueryContext(context);
    }

    @Override
    public void getCapturedGroups(Span[] capturedGroups) {
        if (!childClausesCaptureGroups)
            return;
        clause.getCapturedGroups(capturedGroups);
    }

    @Override
    public int width() {
        return clause.width();
    }

    @Override
    public void collect(SpanCollector collector) throws IOException {
        clause.collect(collector);
    }

    @Override
    public float positionsCost() {
        return clause.positionsCost();
    }
}
