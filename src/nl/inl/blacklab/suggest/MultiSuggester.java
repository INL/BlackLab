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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Base class for a word-based suggester. Suggesters may be chained. The suggest() method produces a
 * Suggestions object that may contain suggestions of several different types (such as spelling
 * variations, inflected forms, related terms, etc.).
 */
public class MultiSuggester extends Suggester {
	private List<Suggester> suggesters = new ArrayList<Suggester>();

	/**
	 * Default constructor, without chaining.
	 */
	public MultiSuggester() {
		//
	}

	public MultiSuggester(Suggester... suggesters) {
		this.suggesters.addAll(Arrays.asList(suggesters));
	}

	public void add(Suggester suggester) {
		suggesters.add(suggester);
	}

	/**
	 * Should be overridden by child classes to add suggestions to the provided Suggestions object.
	 *
	 * @param original
	 *            the original word
	 * @param suggestions
	 *            the suggestions object to add to
	 */
	@Override
	public void addSuggestions(String original, Suggestions suggestions) {
		for (Suggester suggester : suggesters) {
			suggester.addSuggestions(original, suggestions);
		}
	}

}
