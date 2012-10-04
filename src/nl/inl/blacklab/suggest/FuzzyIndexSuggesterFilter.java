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
package nl.inl.blacklab.suggest;

import java.util.List;
import java.util.Map;

import nl.inl.blacklab.search.Searcher;

import org.apache.log4j.Logger;

public class FuzzyIndexSuggesterFilter extends Suggester {
	protected static final Logger logger = Logger.getLogger(FuzzyIndexSuggesterFilter.class);

	private Searcher searcher;

	private String field;

	private float similarity;

	private Suggester suggester;

	public FuzzyIndexSuggesterFilter(Suggester suggester, Searcher searcher, String field,
			float similarity) {
		this.searcher = searcher;
		this.field = field;
		this.similarity = similarity;
		this.suggester = suggester;
	}

	@Override
	public void addSuggestions(String original, Suggestions sugg) {
		Suggestions s = new Suggestions(original);
		suggester.addSuggestions(original, s);
		for (Map.Entry<String, List<String>> e : s.getAllSuggestions().entrySet()) {
			String suggestionType = e.getKey();
			List<String> suggestions = e.getValue();

			for (String t : searcher.getMatchingTermsFromIndex(field, suggestions, similarity)) {
				sugg.addSuggestion(suggestionType, t);
			}
		}
	}
}
