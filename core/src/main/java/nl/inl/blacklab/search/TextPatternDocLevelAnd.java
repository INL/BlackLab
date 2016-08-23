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

/**
 * A TextPattern returning hits from all clauses, but only in documents that match all clauses.
 */
public class TextPatternDocLevelAnd extends TextPatternCombiner {
	public TextPatternDocLevelAnd(TextPattern... clauses) {
		super(clauses);
	}

	@Override
	public <T> T translate(TextPatternTranslator<T> translator, QueryExecutionContext context) {
		List<T> chResults = new ArrayList<>(clauses.size());
		for (TextPattern cl : clauses) {
			chResults.add(cl.translate(translator, context));
		}
		if (chResults.size() == 1)
			return chResults.get(0);
		return translator.docLevelAnd(context, chResults);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof TextPatternDocLevelAnd) {
			return super.equals(obj);
		}
		return false;
	}

	@Override
	public boolean hasConstantLength() {
		int l = clauses.get(0).getMinLength();
		for (TextPattern clause: clauses) {
			if (!clause.hasConstantLength() || clause.getMinLength() != l)
				return false;
		}
		return true;
	}

	@Override
	public int getMinLength() {
		int n = Integer.MAX_VALUE;
		for (TextPattern clause: clauses) {
			n = Math.min(n, clause.getMinLength());
		}
		return n;
	}

	@Override
	public int getMaxLength() {
		int n = 0;
		for (TextPattern clause: clauses) {
			int l = clause.getMaxLength();
			if (l < 0)
				return -1; // infinite
			n = Math.max(n, l);
		}
		return n;
	}

	@Deprecated
	@Override
	public String toString(QueryExecutionContext context) {
		return "DOC-AND(" + clausesToString(clauses, context) + ")";
	}
	@Override
	public String toString() {
		return "DOC-AND(" + clausesToString(clauses) + ")";
	}
}
