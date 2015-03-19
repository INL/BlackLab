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
package nl.inl.blacklab.search;

import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.search.BooleanClause.Occur;

/**
 * A TextPattern that performs document-level combining of boolean clauses. Modeled after Lucene's
 * BooleanQuery, each clause can get the MUST, SHOULD or MUST NOT label attached.
 *
 * MUST-clauses must all occur in the document. At least one of all SHOULD clauses must occur in the
 * document. None of the MUST NOT clauses must occur in the document.
 *
 * For documents that satisfy these criteria, all the MUST and SHOULD hits are reported.
 */
public class TextPatternBoolean extends TextPattern {

	private List<TextPattern> must = new ArrayList<TextPattern>();

	private List<TextPattern> should = new ArrayList<TextPattern>();

	private List<TextPattern> mustNot = new ArrayList<TextPattern>();

	public TextPatternBoolean(boolean disableCoord) {
		// do nothing (disableCoord is ignored as we don't use scoring)
	}

	@Override
	public <T> T translate(TextPatternTranslator<T> translator, QueryExecutionContext context) {

		// First, translate clauses into complete TextPattern. Then translate that.
		TextPattern translated = null;

		// MUST and SHOULD
		TextPattern tpMust = null, tpShould = null;
		if (must.size() > 0) {
			// Build a TextPattern that combines all MUST queries with AND
			tpMust = new TextPatternDocLevelAnd(must.toArray(new TextPattern[0]));
		}
		if (should.size() > 0) {
			// Build a TextPattern that combines all SHOULD queries with OR
			tpShould = new TextPatternOr(should.toArray(new TextPattern[0]));
		}
		if (tpMust == null && tpShould == null)
			throw new RuntimeException("Query must contain included terms (cannot just exclude)");
		else if (tpMust != null && tpShould != null) {
			// Require all MUST queries and at least one of the SHOULD queries
			translated = new TextPatternDocLevelAnd(tpMust, tpShould);
		} else {
			translated = tpMust == null ? tpShould : tpMust;
		}

		// MUST NOT
		if (mustNot.size() > 0) {
			// Build a TextPattern that combines all MUST NOT queries with AND
			translated = new TextPatternDocLevelAndNot(translated, new TextPatternDocLevelAnd(
					mustNot.toArray(new TextPattern[0])));
		}

		return translated.translate(translator, context);
	}

	public void add(TextPattern query, Occur occur) {
		add(new TPBooleanClause(query, occur));
	}

	public void add(TPBooleanClause clause) {
		switch (clause.getOccur()) {
		case MUST:
			must.add(clause.getQuery());
			break;
		case SHOULD:
			should.add(clause.getQuery());
			break;
		case MUST_NOT:
			mustNot.add(clause.getQuery());
			break;
		}
	}

	@Override
	public Object clone() {
		try {
			TextPatternBoolean clone = (TextPatternBoolean) super.clone();

			// copy list of children so we can modify it independently
			clone.must = new ArrayList<TextPattern>(must);
			clone.should = new ArrayList<TextPattern>(should);
			clone.mustNot = new ArrayList<TextPattern>(mustNot);

			return clone;
		} catch (CloneNotSupportedException e) {
			throw new RuntimeException("Clone not supported: " + e.getMessage());
		}
	}

	// TODO: implement rewrite!
}
