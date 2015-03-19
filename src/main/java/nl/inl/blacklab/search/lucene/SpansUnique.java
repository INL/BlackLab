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

import org.apache.lucene.search.spans.Spans;

/**
 * Remove consecutive duplicate hits from a source spans.
 */
public class SpansUnique extends BLSpans {
	private int prevDoc = -1;

	private int prevStart = -1;

	private int prevEnd = -1;

	private BLSpans src;

	private boolean more = true;

	private boolean nexted = false;

	public SpansUnique(Spans src) {
		this.src = BLSpansWrapper.optWrapSort(src);
	}

	@Override
	public int doc() {
		return src.doc();
	}

	@Override
	public int start() {
		return src.start();
	}

	@Override
	public int end() {
		return src.end();
	}

	@Override
	public boolean next() throws IOException {
		if (!more)
			return false;
		do {
			if (nexted) {
				// Save previous hit
				prevDoc = src.doc();
				prevStart = src.start();
				prevEnd = src.end();
			}
			more = src.next();
			nexted = true;
			if (!more)
				return false;
		} while (prevDoc >= 0 && prevDoc == src.doc()
				&& prevStart == src.start() && prevEnd == src.end());
		return true;
	}

	@Override
	public boolean skipTo(int target) throws IOException {
		if (!more)
			return false;

		if (prevDoc >= 0 && target == src.doc()) {
			// We're already in the target doc. Just go to the next hit.
			return next();
		}

		// Just skip to the target doc
		more = src.skipTo(target);
		nexted = true;
		return more;
	}

	@Override
	public String toString() {
		return "UniqueSpans(" + src.toString() + ")";
	}

	@Override
	public boolean hitsEndPointSorted() {
		return src.hitsEndPointSorted();
	}

	@Override
	public boolean hitsStartPointSorted() {
		return true;
	}

	@Override
	public boolean hitsAllSameLength() {
		return src.hitsAllSameLength();
	}

	@Override
	public int hitsLength() {
		return src.hitsLength();
	}

	@Override
	public boolean hitsHaveUniqueStart() {
		return src.hitsHaveUniqueStart();
	}

	@Override
	public boolean hitsHaveUniqueEnd() {
		return src.hitsHaveUniqueEnd();
	}

	@Override
	public boolean hitsAreUnique() {
		return true;
	}

	@Override
	public void passHitQueryContextToClauses(HitQueryContext context) {
		src.setHitQueryContext(context);
	}

	@Override
	public void getCapturedGroups(Span[] capturedGroups) {
		if (!childClausesCaptureGroups)
			return;
		src.getCapturedGroups(capturedGroups);
	}

}
