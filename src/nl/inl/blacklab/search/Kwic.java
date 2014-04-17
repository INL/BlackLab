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

import java.util.Collections;
import java.util.List;

import nl.inl.util.StringUtil;


/**
 * A "keyword in context" for a hit
 * (left context, hit text, right context).
 *
 * The context lists in this class store all the properties for each word,
 * in this order:
 * - punctuation before this word ("punct")
 * - all other properties except punctuation and word (e.g. "lemma", "pos")
 * - the word itself ("word")
 *
 * So if you had "lemma" and "pos" as extra properties in addition to "punct"
 * and "word", and you had 10 words of context, the List size would be 40.
 *
 * (The reason for the specific ordering is ease of converting it to XML, with
 * the extra properties being attributes and the word itself being the element
 * content of the word tags)
 *
 * The Hits class matches this to the Hit.
 *
 * This object may be converted to the old-style Concordance object (with XML strings)
 * by calling Kwic.toConcordance().
 */
public class Kwic {

	/** What properties are stored in what order for this Kwic (e.g. word, lemma, pos) */
	private List<String> properties;

	/** Word properties for context left of match (properties.size() values per word;
	 *  e.g. punct 1, lemma 1, pos 1, word 1, punct 2, lemma 2, pos 2, word 2, etc.) */
	private List<String> left;

	/** Word properties for matched text (properties.size() values per word).
        (see left for the order) */
	private List<String> match;

	/** Word properties for context right of match (properties.size() values per word).
        (see left for the order) */
	private List<String> right;

	/**
	 * Construct a hit object
	 *
	 * @param properties
	 *            What properties are stored in what order for this Kwic (e.g. word, lemma, pos)
	 * @param left
	 * @param match
	 * @param right
	 */
	public Kwic(List<String> properties, List<String> left, List<String> match, List<String> right) {
		this.properties = properties;
		this.left = left;
		this.match = match;
		this.right = right;
	}

	public List<String> getProperties() {
		return Collections.unmodifiableList(properties);
	}

	public List<String> getLeft() {
		return Collections.unmodifiableList(left);
	}

	public List<String> getMatch() {
		return Collections.unmodifiableList(match);
	}

	public List<String> getRight() {
		return Collections.unmodifiableList(right);
	}

	/**
	 * Convert this Kwic object to a Concordance object (the same information in XML format).
	 * @return the Concordance object
	 */
	public Concordance toConcordance() {
		String[] conc = new String[3];
		conc[0] = xmlString(left, match.get(0), true);
		conc[1] = xmlString(match, null, true);
		conc[2] = xmlString(right, null, false);
		return new Concordance(conc);
	}

	/**
	 * Convert a context List to an XML string (used for converting to a Concordance object)
	 *
	 * @param context the context List to convert
	 * @param addPunctAfter if not null, this is appended at the end of the string.
	 * @param leavePunctBefore if true, no punctuation is added before the first word.
	 * @return the XML string
	 */
	private String xmlString(List<String> context, String addPunctAfter, boolean leavePunctBefore) {
		int valuesPerWord = properties.size();
		int numberOfWords = context.size() / valuesPerWord;
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < numberOfWords; i++) {
			int vIndex = i * valuesPerWord;
			int j = 0;
			if (i > 0 || !leavePunctBefore)
				b.append(StringUtil.escapeXmlChars(context.get(vIndex)));
			b.append("<w");
			for (int k = 1; k < properties.size() - 1; k++) {
				String name = properties.get(k);
				String value = context.get(vIndex + 1 + j);
				b.append(" ").append(name).append("=\"").append(StringUtil.escapeXmlChars(value)).append("\"");
				j++;
			}
			b.append(">");
			b.append(StringUtil.escapeXmlChars(context.get(vIndex + 1 + j)));
			b.append("</w>");
		}
		if (addPunctAfter != null)
			b.append(addPunctAfter);
		return b.toString();
	}

}
