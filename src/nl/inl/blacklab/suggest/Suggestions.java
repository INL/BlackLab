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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Result class for word-based suggestions. Stores the original word and a map of suggestions by
 * type. Different types of suggestions might include spelling variations, inflected forms, related
 * terms, etc.
 */
public class Suggestions {
	/** The original word we have suggested alternatives for */
	private String original;

	/** The suggestions by type */
	private Map<String, List<String>> suggestions = new HashMap<String, List<String>>();

	public Suggestions(String original) {
		this.original = original;
	}

	/**
	 * Find all suggestion types
	 *
	 * @return a set of types
	 */
	public List<String> getSuggestionTypes() {
		List<String> sorted = new ArrayList<String>(suggestions.keySet());
		Collections.sort(sorted, new Comparator<String>() {

			@Override
			public int compare(String o1, String o2) {
				// Sort "noun" at the top
				if (o1.equalsIgnoreCase("noun"))
					return -1;
				if (o2.equalsIgnoreCase("noun"))
					return 1;

				// Sort remaining unknown POS codes at the bottom
				if (o1.length() > 0 && Character.isUpperCase(o1.charAt(0)))
					return 1;
				if (o2.length() > 0 && Character.isUpperCase(o2.charAt(0)))
					return -1;

				// Regular compare
				return o1.compareTo(o2);
			}

		});
		return Collections.unmodifiableList(sorted);
	}

	/**
	 * Get all suggestions of a certain type.
	 *
	 * If there are no suggestions of this type, returns an empty list.
	 *
	 * @param type
	 *            the desired suggestion type
	 * @return the list of suggestions
	 */
	public List<String> getSuggestions(String type) {
		List<String> list = suggestions.get(type);
		return list == null ? new ArrayList<String>() : list;
	}

	/**
	 * Get all suggestions in a list.
	 *
	 * @return the list of suggestions
	 */
	public List<String> getAllSuggestionsList() {
		List<String> list = new ArrayList<String>();
		for (List<String> s : suggestions.values()) {
			list.addAll(s);
		}
		return list;
	}

	/**
	 * Sort lists with suggestions.
	 */
	public void sort() {
		for (Map.Entry<String, List<String>> e : suggestions.entrySet()) {
			Collections.sort(e.getValue());
		}
	}

	/**
	 * Sort lists with suggestions.
	 */
	public void sortBySimilarity() {
		for (Map.Entry<String, List<String>> e : suggestions.entrySet()) {
			Collections.sort(e.getValue(), new LevenshteinComparator(original));
		}
	}

	/**
	 * Add a suggestion.
	 *
	 * @param type
	 *            the type of suggestion we're adding
	 * @param suggestion
	 *            the suggestion
	 */
	public void addSuggestion(String type, String suggestion) {
		List<String> l = suggestions.get(type);
		if (l == null) {
			l = new ArrayList<String>();
			suggestions.put(type, l);
		}
		if (!l.contains(suggestion) && !original.equals(suggestion))
			l.add(suggestion);
	}

	public Map<String, List<String>> getAllSuggestions() {
		return suggestions;
	}

	public String getOriginal() {
		return original;
	}

	@Override
	public String toString() {
		StringBuilder b = new StringBuilder();
		boolean none = true;
		for (String type : getSuggestionTypes()) {
			b.append("[" + type + "] ");
			for (String sugg : getSuggestions(type)) {
				b.append("\"" + sugg + "\" ");
			}
			none = false;
		}
		if (none)
			b.append("(none)");
		return b.toString();
	}

}
