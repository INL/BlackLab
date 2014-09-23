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

import java.util.List;
import java.util.Map;

import nl.inl.util.StringUtil;

/**
 * Translates a TextPattern to a String, for debugging and testing purposes.
 */
public class TextPatternTranslatorString extends TextPatternTranslator<String> {

	@Override
	public String and(QueryExecutionContext context, List<String> clauses) {
		return "AND(" + StringUtil.join(clauses, ", ") + ")";
	}

	@Override
	public String or(QueryExecutionContext context, List<String> clauses) {
		return "OR(" + StringUtil.join(clauses, ", ") + ")";
	}

	@Override
	public String regex(QueryExecutionContext context, String value) {
		return "REGEX(" + context.luceneField() + ", " + context.optDesensitize(value) + ")";
	}

	@Override
	public String sequence(QueryExecutionContext context, List<String> clauses) {
		return "SEQ(" + StringUtil.join(clauses, ", ") + ")";
	}

	@Override
	public String docLevelAnd(QueryExecutionContext context, List<String> clauses) {
		return "DOC-AND(" + StringUtil.join(clauses, ", ") + ")";
	}

	@Override
	public String fuzzy(QueryExecutionContext context, String value, int maxEdits, int prefixLength) {
		return "FUZZY(" + context.luceneField() + ", " + context.optDesensitize(value) + ", " + maxEdits + ", " + prefixLength + ")";
	}

	@Override
	public String tags(QueryExecutionContext context, String elementName, Map<String, String> attr) {
		return "TAGS(" + elementName + (attr == null ? "-" : ", " + StringUtil.join(attr.values(), ";")) + ")";
	}

	@Override
	public String containing(QueryExecutionContext context, String containers, String search) {
		return "CONTAINING(" + containers + ", " + search + ")";
	}

	@Override
	public String within(QueryExecutionContext context, String search, String containers) {
		return "WITHIN(" + search + ", " + containers + ")";
	}

	@Override
	public String startsAt(QueryExecutionContext context, String producer, String filter) {
		return "STARTSAT(" + producer + ", " + filter + ")";
	}

	@Override
	public String endsAt(QueryExecutionContext context, String producer, String filter) {
		return "ENDSAT(" + producer + ", " + filter + ")";
	}

	@Override
	public String term(QueryExecutionContext context, String value) {
		return "TERM(" + context.luceneField() + ", " + context.optDesensitize(value) + ")";
	}

	@Override
	public String expand(QueryExecutionContext context, String clause, boolean expandToLeft, int min, int max) {
		return "EXPAND(" + clause + ", " + expandToLeft + ", " + min + ", " + max + ")";
	}

	@Override
	public String repetition(String clause, int min, int max) {
		return "REP(" + clause + ", " + min + ", " + max + ")";
	}

	@Override
	public String docLevelAndNot(String include, String exclude) {
		return "ANDNOT(" + include + ", " + exclude + ")";
	}

	@Override
	public String wildcard(QueryExecutionContext context, String value) {
		return "WILDCARD(" + context.luceneField() + ", " + context.optDesensitize(value) + ")";
	}

	@Override
	public String prefix(QueryExecutionContext context, String value) {
		return "PREFIX(" + context.luceneField() + ", " + context.optDesensitize(value) + ")";
	}

	@Override
	public String not(QueryExecutionContext context, String clause) {
		return "NOT(" + clause + ")";
	}

	@Override
	public String any(QueryExecutionContext context) {
		return "ANYTOKEN";
	}

	@Override
	public String edge(String clause, boolean rightEdge) {
		return "EDGE(" + clause + ", " + rightEdge + ")";
	}

}
