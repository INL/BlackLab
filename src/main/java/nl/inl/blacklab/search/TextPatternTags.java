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

import java.util.Map;

import nl.inl.util.StringUtil;

import org.apache.lucene.index.Term;

/**
 * A TextPattern matching a word.
 */
public class TextPatternTags extends TextPattern {

	protected String elementName;

	Map<String, String> attr;

	public TextPatternTags(String elementName, Map<String, String> attr) {
		this.elementName = elementName;
		this.attr = attr;
	}

	public TextPatternTags(String elementName) {
		this(elementName, null);
	}

	public Term getTerm(String fieldName) {
		return new Term(fieldName, elementName);
	}

	@Override
	public <T> T translate(TextPatternTranslator<T> translator, QueryExecutionContext context) {
		return translator.tags(context, translator.optInsensitive(context, elementName), attr);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof TextPatternTags) {
			TextPatternTags tp = ((TextPatternTags) obj);
			return elementName.equals(tp.elementName) && attr.equals(tp.attr);
		}
		return false;
	}

	@Override
	public boolean hasConstantLength() {
		return false;
	}

	@Override
	public int getMinLength() {
		return 0;
	}

	@Override
	public int getMaxLength() {
		return -1;
	}

	public String getElementName() {
		return elementName;
	}

	@Override
	public int hashCode() {
		return elementName.hashCode() + attr.hashCode();
	}

	@Override
	public String toString(QueryExecutionContext context) {
		if (attr != null && !attr.isEmpty())
			return "TAGS(" + elementName + ", " + StringUtil.join(attr) + ")";
		return "TAGS(" + elementName + ")";
	}

}
