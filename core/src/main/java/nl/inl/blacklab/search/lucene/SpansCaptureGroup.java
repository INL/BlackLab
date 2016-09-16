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
 * Captures its clause as a captured group.
 *
 * Registers itself with the HitQueryContext so others can
 * access its start() and end() when they want to.
 */
class SpansCaptureGroup extends BLSpans {

	/** clause to capture as a group */
	private BLSpans clause;

	/** group name */
	private String name;

	/** group index (where in the Spans[] to place our start/end position in getCapturedGroups()) */
	private int groupIndex;

	/**
	 * Constructs a SpansCaptureGroup.
	 * @param clause the clause to capture
	 * @param name group name
	 */
	public SpansCaptureGroup(BLSpans clause, String name) {
		this.clause = clause;
		this.name = name;
	}

	/**
	 * @return the Lucene document id of the current hit
	 */
	@Override
	public int docID() {
		return clause.docID();
	}

	/**
	 * @return start position of current hit
	 */
	@Override
	public int startPosition() {
		return clause.startPosition();
	}

	/**
	 * @return end position of current hit
	 */
	@Override
	public int endPosition() {
		return clause.endPosition();
	}

	@Override
	public int nextDoc() throws IOException {
		return clause.nextDoc();
	}

	@Override
	public int nextStartPosition() throws IOException {
		return clause.nextStartPosition();
	}

	@Override
	public int advanceStartPosition(int target) throws IOException {
		return clause.advanceStartPosition(target);
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
	public int advance(int doc) throws IOException {
		return clause.advance(doc);
	}

	@Override
	public String toString() {
		return "SpansCaptureGroup(" + clause + ", " + name + ")";
	}

	@Override
	public void setHitQueryContext(HitQueryContext context) {
		super.setHitQueryContext(context);
		this.groupIndex = context.registerCapturedGroup(name);
	}

	@Override
	protected void passHitQueryContextToClauses(HitQueryContext context) {
		clause.setHitQueryContext(context);
	}

	@Override
	public void getCapturedGroups(Span[] capturedGroups) {
		if (childClausesCaptureGroups)
			clause.getCapturedGroups(capturedGroups);

		// Place our start and end position at the correct index in the array
		capturedGroups[groupIndex] = this.getSpan();
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
