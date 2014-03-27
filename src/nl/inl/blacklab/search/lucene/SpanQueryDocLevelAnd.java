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
import java.util.Map;

import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.Spans;
import org.apache.lucene.util.Bits;

/**
 * A SpanQuery for and AND-construction at the document level.
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
	 * Constructs a Spans object that contains all spans in all the documents that contain
	 * both clauses.
	 *
	 * @param context the index reader context
	 * @param acceptDocs document filter
	 * @param termContexts the term contexts (?)
	 * @return the Spans object, or null on error
	 * @throws IOException
	 */
	@Override
	public Spans getSpans(AtomicReaderContext context, Bits acceptDocs, Map<Term,TermContext> termContexts)  throws IOException {
		Spans s0 = clauses[0].getSpans(context, acceptDocs, termContexts);
		Spans combi = s0;
		for (int i = 1; i < clauses.length; i++) {
			Spans si = clauses[i].getSpans(context, acceptDocs, termContexts);
			combi = new SpansDocLevelAnd(combi, si);
		}

		return combi;
	}

	@Override
	public String toString(String field) {
		return "SpanQueryDocLevelAnd(" + clausesToString(field, " & ") + ")";
	}
}
