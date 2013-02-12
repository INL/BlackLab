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
	 * @param fieldName
	 *            the field to search
	 * @param value
	 *            the value to search for
	 * @return result of the translation
	 */
	public abstract T term(String fieldName, String value);

	/**
	 * A regular expression query
	 *
	 * @param fieldName
	 *            the field to search
	 * @param value
	 *            the value to search for
	 * @return result of the translation
	 */
	public abstract T regex(String fieldName, String value);

	/**
	 * Token-level AND.
	 *
	 * @param fieldName
	 *            the field to search
	 * @param clauses
	 *            the clauses to combine using AND
	 * @return result of the translation
	 */
	public abstract T and(String fieldName, List<T> clauses);

	/**
	 * Token-level OR.
	 *
	 * @param fieldName
	 *            the field to search
	 * @param clauses
	 *            the clauses to combine using OR
	 * @return result of the translation
	 */
	public abstract T or(String fieldName, List<T> clauses);

	/**
	 * Token-level NOT.
	 *
	 * @param fieldName
	 *            the field to search
	 * @param clause
	 *            the clause to invert
	 * @return result of the translation
	 */
	public abstract T not(String fieldName, T clause);

	/**
	 * Sequence query.
	 *
	 * @param fieldName
	 *            the field to search
	 * @param clauses
	 *            the clauses to find in sequence
	 * @return result of the translation
	 */
	public abstract T sequence(String fieldName, List<T> clauses);

	// TODO: This is the same for all implementations? Convert to public abstract class and implement here?
	/**
	 * Property query: find the specified TextPattern in the specified property of the field.
	 *
	 * @param fieldName
	 *            the field to search
	 * @param propertyName
	 *            the property to search
	 * @param altName
	 *            human-readable name for the property
	 * @param input
	 *            the source query we want to find in the specified property
	 * @return result of the translation
	 */
	public abstract T property(String fieldName, String propertyName, String altName, TextPattern input);

	public abstract T docLevelAnd(String fieldName, List<T> clauses);

	public abstract T fuzzy(String fieldName, String value, float similarity, int prefixLength);

	public abstract T tags(String fieldName, String elementName, Map<String, String> attr);

	public abstract T edge(T clause, boolean rightEdge);

	public abstract T containing(String fieldName, T containers, T search);

	public abstract T within(String fieldName, T search, T containers);

	public abstract T startsAt(String fieldName, T producer, T filter);

	public abstract T endsAt(String fieldName, T producer, T filter);

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

	public abstract T wildcard(String fieldName, String value);

	public abstract T prefix(String fieldName, String value);

	/**
	 * Any token in field.
	 * @param fieldName the field to search
	 * @return the resulting any-token clause
	 */
	public abstract T any(String fieldName);
}
