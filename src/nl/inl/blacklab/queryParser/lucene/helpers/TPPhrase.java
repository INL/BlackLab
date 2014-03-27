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
package nl.inl.blacklab.queryParser.lucene.helpers;

import java.util.ArrayList;
import java.util.List;

import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.TextPattern;
import nl.inl.blacklab.search.TextPatternTerm;
import nl.inl.blacklab.search.TextPatternTranslator;

import org.apache.lucene.index.Term;

/**
 * A text pattern matching a phrase, modeled after PhraseQuery. Used with the Lucene Query Language
 * parser.
 */
public class TPPhrase extends TextPattern {

	List<TextPattern> terms = new ArrayList<TextPattern>();

	@Override
	public <T> T translate(TextPatternTranslator<T> translator, QueryExecutionContext context) {
		List<T> clauses = new ArrayList<T>();
		for (TextPattern t : terms) {
			clauses.add(t.translate(translator, context));
		}
		return translator.sequence(context, clauses);
	}

	public void add(Term term, int position) {
		add(term.text(), position);
	}

	public void add(Term term) {
		add(term.text());
	}

	public void add(String term, int position) {
		if (position != terms.size())
			throw new RuntimeException(
					"TextPatternPhrase: adding words at specific positions is not supported");
		add(term);
	}

	public void add(String term) {
		terms.add(new TextPatternTerm(term));
	}

	public void setSlop(int phraseSlop) {
		// throw new RuntimeException("Phrase slop is not supported");

		// the parser calls this even if we don't explicitly set it; silently ignore for now
	}

}
