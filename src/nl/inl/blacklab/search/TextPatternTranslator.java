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
 * Interface for translating a TextPattern into a different representation.
 *
 * This uses the Visitor design pattern to recursively translate the whole TextPattern tree.
 *
 * @param <T>
 *            the destination type
 */
public abstract class TextPatternTranslator<T> {

	/**
	 * A simple field/value query
	 *
	 * @param context
	 *            the current query execution context
	 * @param value
	 *            the value to search for
	 * @return result of the query execution
	 */
	public abstract T term(QueryExecutionContext context, String value);

	/**
	 * A regular expression query
	 *
	 * @param context
	 *            the current query execution context
	 * @param value
	 *            the value to search for
	 * @return result of the query execution
	 */
	public abstract T regex(QueryExecutionContext context, String value);

	/**
	 * Token-level AND.
	 *
	 * @param context
	 *            the current query execution context
	 * @param clauses
	 *            the clauses to combine using AND
	 * @return result of the query execution
	 */
	public abstract T and(QueryExecutionContext context, List<T> clauses);

	/**
	 * Token-level OR.
	 *
	 * @param context
	 *            the current query execution context
	 * @param clauses
	 *            the clauses to combine using OR
	 * @return result of the query execution
	 */
	public abstract T or(QueryExecutionContext context, List<T> clauses);

	/**
	 * Token-level NOT.
	 *
	 * @param context
	 *            the current query execution context
	 * @param clause
	 *            the clause to invert
	 * @return result of the query execution
	 */
	public abstract T not(QueryExecutionContext context, T clause);

	/**
	 * Sequence query.
	 *
	 * @param context
	 *            the current query execution context
	 * @param clauses
	 *            the clauses to find in sequence
	 * @return result of the query execution
	 */
	public abstract T sequence(QueryExecutionContext context, List<T> clauses);

	public abstract T docLevelAnd(QueryExecutionContext context, List<T> clauses);

	public abstract T fuzzy(QueryExecutionContext context, String value, int maxEdits, int prefixLength);

	public abstract T tags(QueryExecutionContext context, String elementName, Map<String, String> attr);

	public abstract T edge(T clause, boolean rightEdge);

	public abstract T containing(QueryExecutionContext context, T containers, T search);

	public abstract T within(QueryExecutionContext context, T search, T containers);

	public abstract T startsAt(QueryExecutionContext context, T producer, T filter);

	public abstract T endsAt(QueryExecutionContext context, T producer, T filter);

	/**
	 * Expand the given clause by a number of tokens, either to the left or to the right.
	 *
	 * This is used to implement wilcard tokens.
	 *
	 * @param clause
	 *            the clause to expand
	 * @param expandToLeft
	 *            if true, expand to the left. If false, expand to the right.
	 * @param min
	 *            minimum number of tokens to expand the clause
	 * @param max
	 *            maximum number of tokens to expand the clause
	 * @return the resulting clause
	 */
	public abstract T expand(T clause, boolean expandToLeft, int min, int max);

	/**
	 * Repetition of a clause.
	 *
	 * @param clause
	 *            the repeated clause
	 * @param min
	 *            the minimum number of times it may be repeated (min 0)
	 * @param max
	 *            the maximum number of times it may be repeated (-1 for no limit)
	 * @return the resulting clause
	 */
	public abstract T repetition(T clause, int min, int max);

	/**
	 * Inclusion/exclusion.
	 *
	 * @param include
	 *            clause that must occur
	 * @param exclude
	 *            clause that must not occur
	 * @return the resulting clause
	 */
	public abstract T docLevelAndNot(T include, T exclude);

	public abstract T wildcard(QueryExecutionContext context, String value);

	public abstract T prefix(QueryExecutionContext context, String value);

	/**
	 * Any token in field.
	 * @param context
	 *            the current query execution context
	 * @return the resulting any-token clause
	 */
	public abstract T any(QueryExecutionContext context);

	public String optInsensitive(QueryExecutionContext context, String value) {
		if (!context.diacriticsSensitive)
			value = StringUtil.removeAccents(value);
		if (!context.caseSensitive)
			value = value.toLowerCase();
		return value;
	}
}
