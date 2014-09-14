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
public abstract class BLSpans extends SpansAbstract {

	/**
	 * When hit B follows hit A, is it guaranteed that B.end &gt;= A.end?
	 * Also, if A.end == B.end, is B.start &gt; A.start?
	 *
	 * @return true if this is guaranteed, false if not
	 */
	public boolean hitsEndPointSorted() {
		return true;
	}

	/**
	 * When hit B follows hit A, is it guaranteed that B.start &gt;= A.start?
	 * Also, if A.start == B.start, is B.end &gt; A.end?
	 *
	 * @return true if this is guaranteed, false if not
	 */
	public boolean hitsStartPointSorted() {
		return true;
	}

	/**
	 * Are all hits the same number of tokens long?
	 * @return true if this is guaranteed, false if not
	 */
	public boolean hitsAllSameLength() {
		return true;
	}

	/**
	 * If all hits are the same number of tokens long, how long?
	 * @return the length if all hits are guaranteed to be the same length and that length is known, or a negative number otherwise.
	 */
	public int hitsLength() {
		return 1;
	}

	/**
	 * Is it guaranteed that no two hits have the same start position?
	 * @return true if this is guaranteed, false if not
	 */
	public boolean hitsHaveUniqueStart() {
		return true;
	}

	/**
	 * Is it guaranteed that no two hits have the same end position?
	 * @return true if this is guaranteed, false if not
	 */
	public boolean hitsHaveUniqueEnd() {
		return true;
	}

	/**
	 * Is it guaranteed that no two hits have the same start and end position?
	 * @return true if this is guaranteed, false if not
	 */
	public boolean hitsAreUnique() {
		return true;
	}

	/**
	 * Makes a new Hit object from the document id, start and end positions.
	 *
	 * Subclasses that already have a Hit object available should override this and return the
	 * existing Hit object, to avoid excessive Hit instantiations.	 *
	 * @return the Hit object for the current hit
	 */
	public Hit getHit() {
		return new Hit(doc(), start(), end());
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
		return new Span(start(), end());
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
	abstract public void setHitQueryContext(HitQueryContext context);

	/**
	 * Get the start and end position for the captured groups contained in
	 * this BLSpans (sub)tree.
	 *
	 * @param capturedGroups an array the size of the total number of groups in the query;
	 *   the start and end positions for the groups in this subtree will be placed in here.
	 */
	abstract public void getCapturedGroups(Span[] capturedGroups);

}
