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

import java.util.Arrays;
import java.util.Set;

import nl.inl.blacklab.search.Searcher;

public class FuzzyIndexSuggester extends Suggester {
	private Searcher searcher;
	private String field;
	private float similarity;

	public FuzzyIndexSuggester(Searcher searcher, String field, float similarity) {
		this.searcher = searcher;
		this.field = field;
		this.similarity = similarity;
	}

	@Override
	public void addSuggestions(String original, Suggestions sugg) {
		Set<String> terms = searcher.getMatchingTermsFromIndex(field, Arrays.asList(original),
				similarity);
		for (String t : terms) {
			if (!t.equals(original))
				sugg.addSuggestion("fuzzy", t);
		}
	}
}
