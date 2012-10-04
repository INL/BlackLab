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
/*
 * SpanAndQuery.java
 *
 * Created on May 11, 2006, 4:37 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Collection;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.Spans;

/**
 * Een SpanQuery voor een AND-constructie met twee of meer clauses.
 */
public class SpanQueryDocLevelAnd extends SpanQueryBase {
	public SpanQueryDocLevelAnd(SpanQuery first, SpanQuery second) {
		super(first, second);
	}

	public SpanQueryDocLevelAnd(Collection<SpanQuery> clauscol) {
		super(clauscol);
	}

	public SpanQueryDocLevelAnd(SpanQuery[] _clauses) {
		super(_clauses);
	}

	/**
	 * Maakt een Spans object (bestaande uit WrappedTypedSpans en/of AndSpans objecten) dat alle
	 * spans van de gemeenschappelijke documenten van alle clauses bevat. Als je meer dan twee
	 * clauses hebt toegevoegd, wordt dit dus een soort boomstructuur.
	 *
	 * @param reader
	 *            de IndexReader
	 * @return het Spans object, of null in geval van fout
	 * @throws IOException
	 */
	@Override
	public Spans getSpans(IndexReader reader) throws IOException {
		Spans s0 = clauses[0].getSpans(reader);
		Spans combi = s0;
		for (int i = 1; i < clauses.length; i++) {
			Spans si = clauses[i].getSpans(reader);
			combi = new SpansDocLevelAnd(combi, si);
		}
		return combi;
	}

	@Override
	public String toString(String field) {
		return "SpanQueryDocLevelAnd(" + clausesToString(field, " & ") + ")";
	}
}
