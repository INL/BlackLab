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
package nl.inl.blacklab.search.sequences;

import java.io.IOException;
import java.util.Collection;

import nl.inl.blacklab.search.lucene.BLSpans;
import nl.inl.blacklab.search.lucene.BLSpansWrapper;
import nl.inl.blacklab.search.lucene.SpanQueryBase;
import nl.inl.blacklab.search.lucene.SpansUnique;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.Spans;

/**
 * Combines spans, keeping only combinations of hits that occur one after the other. The order is
 * significant: a hit from the first span must be followed by a hit from the second.
 *
 * Note that this class is different from org.apache.lucene.search.spans.SpanNearQuery: it tries to
 * make sure it generates *all* possible sequence matches. SpanNearQuery doesn't do this; once a hit
 * is used in a SpanNearQuery match, it advances to the next hit.
 *
 * In the future, this class could be expanded to make the exact behaviour configurable: find all
 * matches / find longest matches / find shortest matches / ...
 *
 * See SpanSequenceRaw for details on the matching process.
 */
public class SpanQuerySequence extends SpanQueryBase {
	public SpanQuerySequence(SpanQuery first, SpanQuery second) {
		super(first, second);
	}

	public SpanQuerySequence(Collection<SpanQuery> clauscol) {
		super(clauscol);
	}

	public SpanQuerySequence(SpanQuery[] _clauses) {
		super(_clauses);
	}

	@Override
	public Spans getSpans(IndexReader reader) throws IOException {
		BLSpans combi = BLSpansWrapper.optWrap(clauses[0].getSpans(reader));
		for (int i = 1; i < clauses.length; i++) {
			BLSpans si = BLSpansWrapper.optWrap(clauses[i].getSpans(reader));

			// Note: the spans coming from SequenceSpansRaw are not sorted by end point.
			// This is okay in this loop because combi is used as the left part of the next
			// sequence (so it is explicitly sorted by end point when we put it back in
			// SequenceSpansRaw for the next part of the sequence), but before returning the
			// final spans, we wrap it in a per-document (start-point) sorter.

			if (si.hitsStartPointSorted() && si.hitsHaveUniqueStart() &&
					combi.hitsEndPointSorted() && combi.hitsHaveUniqueEnd()) {
				// We can take a shortcut because of what we know about the Spans we're combining.
				combi = new SpansSequenceSimple(combi, si);
			}
			else {
				combi = new SpansSequenceRaw(combi, si);
			}
		}

		// Sort the resulting spans by start point.
		// Note that duplicates may have formed by combining spans from left and right. Eliminate
		// these duplicates now (hence the 'true').
		boolean sorted = combi.hitsStartPointSorted();
		boolean unique = combi.hitsAreUnique();
		if (!sorted) {
			combi = new PerDocumentSortedSpans(combi, false, !unique);
		} else if (!unique) {
			combi = new SpansUnique(combi);
		}
		return combi;
	}

	@Override
	public String toString(String field) {
		return "SpanQuerySequence(" + clausesToString(field, " >> ") + ")";
	}
}
