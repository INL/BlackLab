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

import nl.inl.blacklab.search.Hit;
import nl.inl.blacklab.search.Span;


/**
 * Will be the base class for all our own Spans classes. Is able to give extra
 * guarantees about the hits in this Spans object, such as if every
 * hit is equal in length, if there may be duplicates, etc. This information
 * will help us optimize certain operations, such as sequence queries, in certain
 * cases.
 *
 * The default implementation is appropriate for Spans classes that return only
 * single-term hits.
 */
public abstract class BLSpans extends Spans {

	/** Should we ask our clauses for captured groups?
	 *  If the clauses don't capture any groups, this will be set to false
	 *  to improve performance.
	 */
	protected boolean childClausesCaptureGroups = true;

	/**
	 * Makes a new Hit object from the document id, start and end positions.
	 *
	 * Subclasses that already have a Hit object available should override this and return the
	 * existing Hit object, to avoid excessive Hit instantiations.
	 *
	 * @return the Hit object for the current hit
	 */
	public Hit getHit() {
		return new Hit(docID(), startPosition(), endPosition());
	}

	/**
	 * Makes a new HitSpan object from the start and end positions (no document id).
	 *
	 * Subclasses that already have a HitSpan object available could override this
	 * and return the existing HitSpan object, to avoid excessive HitSpan instantiations.
	 * (Right now, no classes use HitSpan internally, however)
	 *
	 * @return the HitSpan object for the current hit
	 */
	public Span getSpan() {
		return new Span(startPosition(), endPosition());
	}

	/**
	 * Give the BLSpans tree a way to access captured groups, and the capture
	 * groups themselves a way to register themselves..
	 *
	 * subclasses should override this method, pass the context to their child
	 * clauses (if any), and either:
	 * - register the captured group they represent with the context (SpansCaptureGroup does this), OR
	 * - store the context so they can later use it to access captured groups (SpansBackreference does this)
	 *
	 * @param context the hit query context, that e.g. keeps track of captured groups
	 */
	public void setHitQueryContext(HitQueryContext context) {
		int before = context.getCaptureRegisterNumber();
		passHitQueryContextToClauses(context);
		if (context.getCaptureRegisterNumber() == before) {
			// Our clauses don't capture any groups; optimize
			childClausesCaptureGroups = false;
		}
	}

	/**
	 * Called by setHitQueryContext() to pass the context to child clauses.
	 *
	 * @param context the hit query context, that e.g. keeps track of captured groups
	 */
	abstract protected void passHitQueryContextToClauses(HitQueryContext context);

	/**
	 * Get the start and end position for the captured groups contained in
	 * this BLSpans (sub)tree.
	 *
	 * @param capturedGroups an array the size of the total number of groups in the query;
	 *   the start and end positions for the groups in this subtree will be placed in here.
	 */
	abstract public void getCapturedGroups(Span[] capturedGroups);

	/**
	 * Advance the start position in the current doc to target or beyond.
	 *
	 * Always at least advances to the next hit, even if the current start
	 * position is already at or beyond the target.
	 *
	 * @param target target start position to advance to
	 * @return new start position, or Spans.NO_MORE_POSITIONS if we're done with this document
	 * @throws IOException
	 */
	public int advanceStartPosition(int target) throws IOException {
		// Naive implementations; subclasses may provide a faster version.
		int pos;
		do {
			pos = nextStartPosition();
		} while(pos < target && pos != NO_MORE_POSITIONS);
		return pos;
	}

	@Override
	public long cost() {
		// returns a completely arbitrary constant value, but it's for
		// optimizing scoring and we don't generally use that
		return 100;
	}

	public static BLSpans optSortUniq(BLSpans spans, boolean sort, boolean removeDuplicates) {
		if (spans == null)
			return null;
		BLSpans result = spans;
		if (sort)
			return new PerDocumentSortedSpans(result, PerDocumentSortedSpans.cmpStartPoint, removeDuplicates);
		if (removeDuplicates)
			return new SpansUnique(result);
		return result;
	}
}
