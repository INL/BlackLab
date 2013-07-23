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

import org.apache.lucene.search.spans.Spans;

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
	 * Constructs a SpansNot.
	 * @param clause the clause to invert, or null if we want all tokens
	 * @param rightEdge
	 */
	public SpansEdge(Spans clause, boolean rightEdge) {
		this.clause = BLSpansWrapper.optWrap(clause);
		this.rightEdge = rightEdge;
	}

	/**
	 * @return the Lucene document id of the current hit
	 */
	@Override
	public int doc() {
		return clause.doc();
	}

	/**
	 * @return start position of current hit
	 */
	@Override
	public int start() {
		return rightEdge ? clause.end() : clause.start();
	}

	/**
	 * @return end position of current hit
	 */
	@Override
	public int end() {
		return rightEdge ? clause.end() : clause.start();
	}

	/**
	 * Go to next span.
	 *
	 * @return true if we're at the next span, false if we're done
	 * @throws IOException
	 */
	@Override
	public boolean next() throws IOException {
		return clause.next();
	}

	/**
	 * Skip to the specified document (or the first document after it containing hits).
	 *
	 * @param doc
	 *            the doc number to skip to (or past)
	 * @return true if we're still pointing to a valid hit, false if we're done
	 * @throws IOException
	 */
	@Override
	public boolean skipTo(int doc) throws IOException {
		return clause.skipTo(doc);
	}

	@Override
	public String toString() {
		return "SpansEdge(" + clause + ", " + (rightEdge ? "RIGHT" : "LEFT") + ")";
	}

	@Override
	public boolean hitsEndPointSorted() {
		return hitsStartPointSorted();
	}

	@Override
	public boolean hitsStartPointSorted() {
		return rightEdge ? clause.hitsEndPointSorted() : clause.hitsStartPointSorted();
	}

	@Override
	public boolean hitsAllSameLength() {
		return true;
	}

	@Override
	public int hitsLength() {
		return 0;
	}

	@Override
	public boolean hitsHaveUniqueStart() {
		return rightEdge ? clause.hitsHaveUniqueEnd() : clause.hitsHaveUniqueStart();
	}

	@Override
	public boolean hitsHaveUniqueEnd() {
		return hitsHaveUniqueStart();
	}

	@Override
	public boolean hitsAreUnique() {
		return hitsHaveUniqueStart();
	}


}
