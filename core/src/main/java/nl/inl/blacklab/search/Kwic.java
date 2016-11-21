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

import java.util.AbstractList;
import java.util.Collections;
import java.util.List;

import nl.inl.util.StringUtil;


/**
 * A "keyword in context" for a hit
 * (left context, hit text, right context).
 *
 * The Hits class matches this to the Hit.
 *
 * This object may be converted to a Concordance object (with XML strings)
 * by calling Kwic.toConcordance().
 */
public class Kwic {

	DocContentsFromForwardIndex fragment;

	int hitStart;

	int hitEnd;

	/**
	 * Construct a Kwic object
	 *
	 * @param properties
	 *            What properties are stored in what order for this Kwic (e.g. word, lemma, pos)
	 * @param tokens the contents
	 * @param matchStart where the match starts, in word positions
	 * @param matchEnd where the match ends, in word positions
	 */
	public Kwic(List<String> properties, List<String> tokens, int matchStart, int matchEnd) {
		fragment = new DocContentsFromForwardIndex(properties, tokens);
		this.hitStart = matchStart;
		this.hitEnd = matchEnd;
	}

	/**
	 * Construct a Kwic object
	 *
	 * @param fragment the content fragment to make the Kwic from
	 * @param matchStart where the match starts, in word positions
	 * @param matchEnd where the match ends, in word positions
	 */
	public Kwic(DocContentsFromForwardIndex fragment, int matchStart, int matchEnd) {
		this.fragment = fragment;
		this.hitStart = matchStart;
		this.hitEnd = matchEnd;
	}

	public List<String> getLeft() {
		return Collections.unmodifiableList(fragment.tokens.subList(0, hitStart * fragment.properties.size()));
	}

	public List<String> getMatch() {
		return Collections.unmodifiableList(fragment.tokens.subList(hitStart * fragment.properties.size(), hitEnd * fragment.properties.size()));
	}

	public List<String> getRight() {
		return Collections.unmodifiableList(fragment.tokens.subList(hitEnd * fragment.properties.size(), fragment.tokens.size()));
	}

	/**
	 * Get all the properties of all the tokens in the hit's context fragment.
	 * @return the token properties
	 */
	public List<String> getTokens() {
		return fragment.getTokens();
	}

	/**
	 * Get all values for a single property for all the tokens in the
	 * hit's context fragment.
	 *
	 * @param property the property to get
	 * @return the values of this property for all tokens
	 */
	public List<String> getTokens(String property) {
		return fragment.getTokens(property);
	}

	/**
	 * Get the context of a specific property from the complete
	 * context list.
	 *
	 * @param allContext the complete context list of all properties
	 * @param property the property to get the context for
	 * @param start first word position to get the property context for
	 * @param end word position after the last to get the property context for
	 * @return the context for this property
	 */
	private List<String> getSinglePropertyContext(String property, int start, int end) {
		final int nProp = fragment.properties.size();
		final int size = end - start;
		final int propIndex = fragment.properties.indexOf(property);
		final int startIndex = start * nProp + propIndex;
		if (propIndex == -1)
			return null;
		return new AbstractList<String>() {
			@Override
			public String get(int index) {
				if (index >= size)
					throw new IndexOutOfBoundsException();
				return fragment.tokens.get(startIndex + nProp * index);
			}

			@Override
			public int size() {
				return size;
			}
		};
	}

	/**
	 * Get the left context of a specific property
	 * @param property the property to get the context for
	 * @return the context
	 */
	public List<String> getLeft(String property) {
		return getSinglePropertyContext(property, 0, hitStart);
	}

	/**
	 * Get the match context of a specific property
	 * @param property the property to get the context for
	 * @return the context
	 */
	public List<String> getMatch(String property) {
		return getSinglePropertyContext(property, hitStart, hitEnd);
	}


	/**
	 * Get the right context of a specific property
	 * @param property the property to get the context for
	 * @return the context
	 */
	public List<String> getRight(String property) {
		return getSinglePropertyContext(property, hitEnd, fragment.tokens.size() / fragment.properties.size());
	}

	/**
	 * Convert this Kwic object to a Concordance object.
	 *
	 * This produces XML consisting of &lt;w&gt; tags. The words
	 * are the text content of the tags. The punctuation is between the tags.
	 * The other properties are attributes of the tags.
	 *
	 * @return the Concordance object
	 */
	public Concordance toConcordance() {
		return toConcordance(true);
	}

	/**
	 * Convert this Kwic object to a Concordance object.
	 *
	 * This may either consist of only words and punctuation, or include the XML
	 * tags containing the other properties as well, depending on the parameter.
	 *
	 * @param produceXml if true, produces XML. If false, produces human-readable text.
	 * @return the Concordance object
	 */
	public Concordance toConcordance(boolean produceXml) {
		String[] conc = new String[3];
		List<String> match = getMatch();
		String addPunctAfter = !match.isEmpty() ? match.get(0) : "";
		conc[0] = xmlString(getLeft(), addPunctAfter, true, produceXml);
		conc[1] = xmlString(match, null, true, produceXml);
		conc[2] = xmlString(getRight(), null, false, produceXml);
		return new Concordance(conc);
	}

	/**
	 * Convert a context List to an XML string (used for converting to a Concordance object)
	 *
	 * @param context the context List to convert
	 * @param addPunctAfter if not null, this is appended at the end of the string.
	 * @param leavePunctBefore if true, no punctuation is added before the first word.
	 * @param produceXml if true, produces XML with word tags. If false, produces human-readable text.
	 * @return the XML string
	 */
	private String xmlString(List<String> context, String addPunctAfter, boolean leavePunctBefore, boolean produceXml) {
		int valuesPerWord = fragment.properties.size();
		int numberOfWords = context.size() / valuesPerWord;
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < numberOfWords; i++) {
			int vIndex = i * valuesPerWord;
			int j = 0;
			if (i > 0 || !leavePunctBefore) {
				if (produceXml)
					b.append(StringUtil.escapeXmlChars(context.get(vIndex)));
				else
					b.append(context.get(vIndex));
			}
			if (produceXml) {
				b.append("<w");
				for (int k = 1; k < valuesPerWord - 1; k++) {
					String name = fragment.properties.get(k);
					String value = context.get(vIndex + 1 + j);
					b.append(" ").append(name).append("=\"").append(StringUtil.escapeXmlChars(value)).append("\"");
					j++;
				}
				b.append(">");
			} else {
				// We're skipping the other properties besides word and punct. Advance j.
				if (valuesPerWord > 2)
					j += valuesPerWord - 2;
			}
			if (produceXml)
				b.append(StringUtil.escapeXmlChars(context.get(vIndex + 1 + j))).append("</w>");
			else
				b.append(context.get(vIndex + 1 + j));
		}
		if (addPunctAfter != null)
			b.append(addPunctAfter);
		return b.toString();
	}

	/**
	 * Get the names of the properties in the order they occur in the context array.
	 * @return the property names
	 */
	public List<String> getProperties() {
		return fragment.getProperties();
	}

	/**
	 * Return the index of the token after the last hit token
	 * in the context fragment.
	 * @return the hit end index
	 */
	public int getHitEnd() {
		return hitEnd;
	}

	/**
	 * Return the index of the first hit token in the context fragment.
	 * @return the hit start index
	 */
	public int getHitStart() {
		return hitStart;
	}

	public String getFullXml() {
		return fragment.getXml();
	}

	public DocContentsFromForwardIndex getDocContents() {
		return fragment;
	}

	@Override
	public String toString() {
		return toConcordance().toString();
	}

}
