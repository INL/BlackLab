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

/**
 * Returns either the left edge or right edge of the specified query.
 *
 * Note that the results of this query are zero-length spans.
 */
class SpansEdge extends BLSpans {

    /** query the query to determine edges from */
    private BLSpans clause;

    /** if true, return the right edges; if false, the left */
    private boolean rightEdge;

    /**
     * Constructs a SpansEdge.
     * 
     * @param clause the clause to get an edge from
     * @param rightEdge whether or not to get the right edge
     */
    public SpansEdge(BLSpans clause, boolean rightEdge) {
        this.clause = clause;
        this.rightEdge = rightEdge;
    }

    @Override
    public int docID() {
        return clause.docID();
    }

    @Override
    public int startPosition() {
        return rightEdge ? clause.endPosition() : clause.startPosition();
    }

    @Override
    public int endPosition() {
        return rightEdge ? clause.endPosition() : clause.startPosition();
    }

    @Override
    public int nextDoc() throws IOException {
        return clause.nextDoc();
    }

    @Override
    public int nextStartPosition() throws IOException {
        if (rightEdge) {
            if (clause.nextStartPosition() == NO_MORE_POSITIONS)
                return NO_MORE_POSITIONS;
            return clause.endPosition();
        }
        return clause.nextStartPosition();
    }

    @Override
    public int advanceStartPosition(int target) throws IOException {
        if (rightEdge) {
            // We can't skip because we're looking at end positions
            return super.advanceStartPosition(target);
        }
        return clause.advanceStartPosition(target);
    }

    @Override
    public int advance(int doc) throws IOException {
        return clause.advance(doc);
    }

    @Override
    public String toString() {
        return "SpansEdge(" + clause + ", " + (rightEdge ? "RIGHT" : "LEFT") + ")";
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
