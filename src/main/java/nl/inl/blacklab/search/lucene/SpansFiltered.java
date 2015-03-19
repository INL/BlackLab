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

import nl.inl.blacklab.search.Span;

import org.apache.lucene.search.DocIdSet;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.spans.Spans;

/**
 * Apply a Filter to a Spans.
 *
 * This allows us to only consider certain documents (say, only documents in a certain domain) when
 * executing our query.
 */
public class SpansFiltered extends BLSpans {
	BLSpans spans;

	/** Set of accepted docs. NOTE: this is not segment-based, but for the whole index! */
	DocIdSetIterator docIdSetIter;

	boolean more;

	public SpansFiltered(Spans spans, DocIdSet filterDocs) throws IOException {
		this.spans = BLSpansWrapper.optWrap(spans);
		docIdSetIter = filterDocs.iterator();
		more = false;
		if (docIdSetIter != null) {
			more = (docIdSetIter.nextDoc() != DocIdSetIterator.NO_MORE_DOCS);
		}
	}

	private boolean synchronize() throws IOException {
		while (more && spans.doc() != docIdSetIter.docID()) {
			if (spans.doc() < docIdSetIter.docID()) {
				more = spans.skipTo(docIdSetIter.docID());
			} else if (docIdSetIter.advance(spans.doc()) == DocIdSetIterator.NO_MORE_DOCS) {
				more = false;
			}
		}
		return more;
	}

	@Override
	public boolean next() throws IOException {
		if (!more)
			return false;
		more = spans.next();
		return synchronize();
	}

	@Override
	public boolean skipTo(int target) throws IOException {
		if (!more)
			return false;
		more = spans.skipTo(target);
		return synchronize();
	}

	@Override
	public int doc() {
		return spans.doc();
	}

	@Override
	public int end() {
		return spans.end();
	}

	@Override
	public int start() {
		return spans.start();
	}

	@Override
	public boolean hitsEndPointSorted() {
		return spans.hitsEndPointSorted();
	}

	@Override
	public boolean hitsStartPointSorted() {
		return spans.hitsStartPointSorted();
	}

	@Override
	public boolean hitsAllSameLength() {
		return spans.hitsAllSameLength();
	}

	@Override
	public int hitsLength() {
		return spans.hitsLength();
	}

	@Override
	public boolean hitsHaveUniqueStart() {
		return spans.hitsHaveUniqueStart();
	}

	@Override
	public boolean hitsHaveUniqueEnd() {
		return spans.hitsHaveUniqueEnd();
	}

	@Override
	public boolean hitsAreUnique() {
		return spans.hitsAreUnique();
	}

	@Override
	public void passHitQueryContextToClauses(HitQueryContext context) {
		spans.setHitQueryContext(context);
	}

	@Override
	public void getCapturedGroups(Span[] capturedGroups) {
		if (!childClausesCaptureGroups)
			return;
		spans.getCapturedGroups(capturedGroups);
	}

}
