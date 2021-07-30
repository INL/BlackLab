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

import org.apache.lucene.search.spans.SpanCollector;

import nl.inl.blacklab.search.Span;
import nl.inl.blacklab.search.lucene.SpanQueryPositionFilter.Operation;

/**
 * Finds hits from a set that contain one or more hits from the second set, or
 * finds hits from a set that are contained by hit(s) from the second set.
 */
class SpansPositionFilter extends BLSpans {
    /** The spans we're (possibly) looking for */
    private BLSpans producer;

    /** The spans we use to filter the producer spans */
    private SpansInBuckets filter;

    /** What doc is the producer in? */
    private int producerDoc = -1;

    /** What doc is the filter in? */
    private int filterDoc = -1;

    /** What start pos is the producer at? */
    private int producerStart = -1;

    /**
     * Which index in the filter bucket did we use? (needed for getting captured
     * groups)
     */
    private int filterIndex = -1;

    /** What filter operation to use */
    private Operation op;

    /** How to adjust the left edge of the producer hits while matching */
    private int leftAdjust;

    /** How to adjust the right edge of the producer hits while matching */
    private int rightAdjust;

    /**
     * Are we already at the first match in a new document, before
     * nextStartPosition() has been called? Necessary because we have to make sure
     * nextDoc()/advance() actually puts us in a document with at least one match.
     */
    private boolean alreadyAtFirstMatch = false;

    /**
     * If true, produce hits that DON'T match the filter instead.
     */
    private boolean invert;

    /** Are the filter hits guaranteed to have the same length? */
    private boolean filterFixedLength;

    /**
     * Find hits from producer, filtered by the filter according to the specified op
     *
     * Both producer and filter should be start-point sorted.
     *
     * @param producer the hits we may be interested in
     * @param filter the hits used to filter the producer hits
     * @param filterFixedLength true if the filter hits are all the same length.
     *            Used for optimization.
     * @param op filter operation to use
     * @param invert if true, produce hits that DON'T match the filter instead
     * @param leftAdjust how to adjust the left edge of the producer hits while
     *            matching
     * @param rightAdjust how to adjust the right edge of the producer hits while
     *            matching
     */
    public SpansPositionFilter(BLSpans producer, SpansInBuckets filter, boolean filterFixedLength, Operation op,
            boolean invert, int leftAdjust, int rightAdjust) {
        this.producer = producer; // Sort
        this.op = op;
        this.invert = invert;
        this.filter = filter;
        this.filterFixedLength = filterFixedLength;
        this.leftAdjust = leftAdjust;
        this.rightAdjust = rightAdjust;
    }

    @Override
    public int docID() {
        return producerDoc;
    }

    @Override
    public int endPosition() {
        if (alreadyAtFirstMatch)
            return -1; // nextStartPosition() hasn't been called yet
        return producer.endPosition();
    }

    @Override
    public int nextDoc() throws IOException {
        alreadyAtFirstMatch = false;

        // Are we done yet?
        if (producerDoc == NO_MORE_DOCS)
            return NO_MORE_DOCS;

        // Advance container
        producerDoc = producer.nextDoc();
        producerStart = -1;
        if (producerDoc == NO_MORE_DOCS)
            return NO_MORE_DOCS; // no more containers; we're done.

        // Find first matching producer span from here
        return findDocWithMatch();
    }

    @Override
    public int nextStartPosition() throws IOException {
        if (producerDoc == NO_MORE_DOCS)
            return NO_MORE_POSITIONS;

        if (alreadyAtFirstMatch) {
            // We're already at the first match in the doc. Return it.
            alreadyAtFirstMatch = false;
            return producerStart;
        }

        // Are we done yet?
        if (producerStart == NO_MORE_POSITIONS)
            return NO_MORE_POSITIONS;

        // Find first matching producer span from here
        producerStart = producer.nextStartPosition();
        return synchronizePos();
    }

    @Override
    public int advanceStartPosition(int target) throws IOException {
        if (producerDoc == NO_MORE_DOCS)
            return NO_MORE_POSITIONS;

        if (alreadyAtFirstMatch) {
            alreadyAtFirstMatch = false;
            if (producerStart >= target)
                return producerStart;
        }

        // Are we done yet?
        if (producerStart == NO_MORE_POSITIONS)
            return NO_MORE_POSITIONS;

        producerStart = producer.advanceStartPosition(target);

        // Find first matching producer span from here
        return synchronizePos();
    }

    /**
     * Find a producer span (not necessarily in this document) matching with filter,
     * starting from the current producer span.
     *
     * @return docID if found, NO_MORE_DOCS if no such producer span exists (i.e.
     *         we're done)
     * @throws IOException
     */
    private int findDocWithMatch() throws IOException {
        // Find the next "valid" container, if there is one.
        while (producerDoc != NO_MORE_DOCS) {

            // Are filter and producer in the same document?
            while (filterDoc != producerDoc) {
                if (filterDoc < producerDoc) {
                    // No, advance filter to be in the same document as the producer
                    filterDoc = filter.advance(producerDoc);
                    if (filterDoc == NO_MORE_DOCS) {
                        if (!invert) {
                            // Positive filter, but no more filter hits. We're done.
                            return NO_MORE_DOCS;
                        }
                    } else
                        filter.nextBucket();
                } else if (producerDoc < filterDoc) {
                    if (invert) {
                        // For negative filters, lagging producer spans is ok. This just means
                        // all hits in the current producer doc are matches.
                        break;
                    }
                    // No, advance producer to be in the same document as the producer
                    producerDoc = producer.advance(filterDoc);
                    if (producerDoc == NO_MORE_DOCS)
                        return NO_MORE_DOCS; // No more producer results, we're done.
                }
            }

            // Are there search results in this document?
            if (producerStart != NO_MORE_POSITIONS) {
                producerStart = producer.nextStartPosition();
            }
            producerStart = synchronizePos();
            if (producerStart != NO_MORE_POSITIONS) {
                alreadyAtFirstMatch = true;
                return producerDoc;
            }

            // No search results found in the current container.
            // Advance to the next container.
            producerDoc = producer.nextDoc();
            producerStart = -1;
        }
        return producerDoc;
    }

    /**
     * Find a producer span matching with filter, starting from the current producer
     * span.
     *
     * @return start position if found, NO_MORE_POSITIONS if no such container
     *         exists (i.e. we're done)
     * @throws IOException
     */
    private int synchronizePos() throws IOException {
        // Find the next "valid" producer spans, if there is one.
        while (producerStart != NO_MORE_POSITIONS) {
            if (invert && filterDoc != producerDoc) {
                // No filter hits in this doc, so this is definitely a hit.
                return producerStart;
            }

            // We're at the first unchecked producer spans. Does it match our filter?
            boolean invertedMatch = invert; // if looking for non-matches, keep track if there have been any matches.
            int min = 0, max = filter.bucketSize() - 1;
            switch (op) {
            case CONTAINING:
                // Looking for producer hits with a filter hit inside
                while (min <= max) {
                    int i = (min + max) / 2;
                    if (filterFixedLength && filter.endPosition(i) > producer.endPosition() + rightAdjust) {
                        // Filter end position to the right of producer hit end position.
                        max = i - 1;
                    } else if (filter.startPosition(i) < producerStart + leftAdjust) {
                        // Filter start position to the left of producer hit start position.
                        min = i + 1;
                    } else {
                        // Can't narrow down the edges any further; do linear search from here.
                        break;
                    }
                }
                for (int i = min; i <= max; i++) {
                    if (filter.startPosition(i) >= producerStart + leftAdjust
                            && filter.endPosition(i) <= producer.endPosition() + rightAdjust) {
                        if (invert) {
                            // This producer hit is no good; on to the next.
                            invertedMatch = false;
                            break;
                        }
                        // Yes, this producer hit contains this filter hit
                        filterIndex = i; // remember for captured groups
                        return producerStart;
                    }
                }
                break;
            case CONTAINING_AT_START:
                // Looking for producer hits with a filter hit inside, at the start
                while (min <= max) {
                    int i = (min + max) / 2;
                    if (filterFixedLength && filter.endPosition(i) > producer.endPosition() + rightAdjust) {
                        // Filter end position to the right of producer hit end position.
                        max = i - 1;
                    } else if (filter.startPosition(i) < producerStart + leftAdjust) {
                        // Filter start position to the left of producer hit start position.
                        min = i + 1;
                    } else {
                        // Can't narrow down the edges any further; do linear search from here.
                        break;
                    }
                }
                for (int i = min; i <= max; i++) {
                    if (filter.startPosition(i) == producerStart + leftAdjust
                            && filter.endPosition(i) <= producer.endPosition() + rightAdjust) {
                        if (invert) {
                            // This producer hit is no good; on to the next.
                            invertedMatch = false;
                            break;
                        }
                        // Yes, this producer hit contains this filter hit
                        filterIndex = i; // remember for captured groups
                        return producerStart;
                    }
                }
                break;
            case CONTAINING_AT_END:
                // Looking for producer hits with a filter hit inside, at the end
                while (min <= max) {
                    int i = (min + max) / 2;
                    if (filterFixedLength && filter.endPosition(i) > producer.endPosition() + rightAdjust) {
                        // Filter end position to the right of producer hit end position.
                        max = i - 1;
                    } else if (filter.startPosition(i) < producerStart + leftAdjust) {
                        // Filter start position to the left of producer hit start position.
                        min = i + 1;
                    } else {
                        // Can't narrow down the edges any further; do linear search from here.
                        break;
                    }
                }
                for (int i = min; i <= max; i++) {
                    if (filter.startPosition(i) >= producerStart + leftAdjust
                            && filter.endPosition(i) == producer.endPosition() + rightAdjust) {
                        if (invert) {
                            // This producer hit is no good; on to the next.
                            invertedMatch = false;
                            break;
                        }
                        // Yes, this producer hit contains this filter hit
                        filterIndex = i; // remember for captured groups
                        return producerStart;
                    }
                }
                break;
            case WITHIN:
                // Looking for producer hits contained by a filter hit
                while (min <= max) {
                    int i = (min + max) / 2;
                    if (filter.startPosition(i) > producerStart + leftAdjust) {
                        // Filter start position to the right of producer hit start position.
                        max = i - 1;
                    } else if (filterFixedLength && filter.endPosition(i) < producer.endPosition() + rightAdjust) {
                        // Filter end position to the left of producer hit end position.
                        min = i + 1;
                    } else {
                        // Can't narrow down the edges any further; do linear search from here.
                        break;
                    }
                }
                for (int i = min; i <= max; i++) {
                    if (filter.startPosition(i) <= producerStart + leftAdjust
                            && filter.endPosition(i) >= producer.endPosition() + rightAdjust) {
                        if (invert) {
                            // This producer hit is no good; on to the next.
                            invertedMatch = false;
                            break;
                        }
                        // Yes, this producer hit is contained within this filter hit
                        filterIndex = i; // remember for captured groups
                        return producerStart;
                    }
                }
                break;
            case STARTS_AT:
                // Looking for producer hits starting at a filter hit
                while (min <= max) {
                    int i = (min + max) / 2;
                    if (filter.startPosition(i) > producerStart + leftAdjust) {
                        // Filter start position to the right of producer hit start position.
                        max = i - 1;
                    } else if (filter.startPosition(i) < producerStart + leftAdjust) {
                        // Filter start position to the left of producer hit start position.
                        min = i + 1;
                    } else {
                        // Found filter hit at the start of current producer hit.
                        if (invert) {
                            // This producer hit is no good; on to the next.
                            invertedMatch = false;
                            break;
                        }
                        // Yes, this producer hit starts at this filter hit
                        filterIndex = i; // remember for captured groups
                        return producerStart;
                    }
                }
                break;
            case ENDS_AT:
                // Looking for producer hits ending at a filter hit
                if (filterFixedLength) {
                    while (min <= max) {
                        int i = (min + max) / 2;
                        if (filter.endPosition(i) > producer.endPosition() + rightAdjust) {
                            // Filter end position to the right of producer hit end position.
                            max = i - 1;
                        } else if (filter.endPosition(i) < producer.endPosition() + rightAdjust) {
                            // Filter end position to the left of producer hit end position.
                            min = i + 1;
                        } else {
                            // Can't narrow down the edges any further; do linear search from here.
                            break;
                        }
                    }
                }
                for (int i = min; i <= max; i++) {
                    if (filter.endPosition(i) == producer.endPosition() + rightAdjust) {
                        if (invert) {
                            // This producer hit is no good; on to the next.
                            invertedMatch = false;
                            break;
                        }
                        // Yes, this producer hit ends at this filter hit
                        filterIndex = i; // remember for captured groups
                        return producerStart;
                    }
                }
                break;
            case MATCHES:
                // Looking for producer hits exactly matching a filter hit
                while (min <= max) {
                    int i = (min + max) / 2;
                    if (filter.startPosition(i) < producerStart + leftAdjust
                            || filterFixedLength && filter.endPosition(i) < producer.endPosition() + rightAdjust) {
                        min = i + 1;
                    } else if (filter.startPosition(i) > producerStart + leftAdjust
                            || filterFixedLength && filter.endPosition(i) > producer.endPosition() + rightAdjust) {
                        max = i - 1;
                    } else {
                        // Can't narrow down the edges any further; do linear search from here.
                        break;
                    }
                }
                for (int i = min; i <= max; i++) {
                    if (filter.startPosition(i) == producerStart + leftAdjust
                            && filter.endPosition(i) == producer.endPosition() + rightAdjust) {
                        if (invert) {
                            // This producer hit is no good; on to the next.
                            invertedMatch = false;
                            break;
                        }
                        // Yes, this producer hit exactly matches this filter hit
                        filterIndex = i; // remember for captured groups
                        return producerStart;
                    }
                }
                break;
            default:
                throw new IllegalArgumentException("Unknown filter operation " + op);
            }
            // Were we looking for non-matching producer hits, and have we not found any?
            if (invertedMatch) {
                return producerStart;
            }
            // Didn't match filter; go to the next position.
            producerStart = producer.nextStartPosition();
            if (producerStart == NO_MORE_POSITIONS)
                return NO_MORE_POSITIONS;

        }
        return producerStart;
    }

    @Override
    public int advance(int doc) throws IOException {
        alreadyAtFirstMatch = false;

        // Skip both to doc
        producerDoc = producer.advance(doc);
        producerStart = -1;
        if (producerDoc == NO_MORE_DOCS)
            return NO_MORE_DOCS;

        // Find first matching producer span from here
        return findDocWithMatch();
    }

    @Override
    public int startPosition() {
        if (alreadyAtFirstMatch)
            return -1; // nextStartPosition() hasn't been called yet
        if (producerDoc == NO_MORE_DOCS)
            return NO_MORE_POSITIONS;
        return producerStart;
    }

    @Override
    public String toString() {
        String not = invert ? "not " : "";
        String ign = (leftAdjust != 0 || rightAdjust != 0) ? ", " + leftAdjust + ", " + rightAdjust : "";
        switch (op) {
        case CONTAINING:
            return "POSFILTER(" + producer + " " + not + "containing " + filter + ign + ")";
        case WITHIN:
            return "POSFILTER(" + producer + " " + not + "within " + filter + ign + ")";
        case STARTS_AT:
            return "POSFILTER(" + producer + " " + not + "starts at " + filter + ign + ")";
        case ENDS_AT:
            return "POSFILTER(" + producer + " " + not + "ends at " + filter + ign + ")";
        case MATCHES:
            return "POSFILTER(" + producer + " " + not + "matches " + filter + ign + ")";
        default:
            throw new IllegalArgumentException("Unknown filter operation " + op);
        }
    }

    @Override
    public void passHitQueryContextToClauses(HitQueryContext context) {
        producer.setHitQueryContext(context);
        filter.setHitQueryContext(context);
    }

    @Override
    public void getCapturedGroups(Span[] capturedGroups) {
        if (!childClausesCaptureGroups)
            return;
        producer.getCapturedGroups(capturedGroups);
        filter.getCapturedGroups(filterIndex, capturedGroups);
    }

    @Override
    public int width() {
        return producer.width();
    }

    @Override
    public void collect(SpanCollector collector) throws IOException {
        producer.collect(collector);
    }

    @Override
    public float positionsCost() {
        return producer.positionsCost();
    }

}
