/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.search;

import java.util.List;

/**
 * Interface for translating a TextPattern into a different representation.
 *
 * This uses the Visitor design pattern to recursively translate the whole TextPattern tree.
 *
 * @param <T>
 *            the destination type
 */
public interface TextPatternTranslator<T> {
	/**
	 * A simple field/value query
	 *
	 * @param fieldName
	 *            the field to search
	 * @param value
	 *            the value to search for
	 * @return result of the translation
	 */
	T term(String fieldName, String value);

	/**
	 * A regular expression query
	 *
	 * @param fieldName
	 *            the field to search
	 * @param value
	 *            the value to search for
	 * @return result of the translation
	 */
	T regex(String fieldName, String value);

	/**
	 * Token-level AND.
	 *
	 * @param fieldName
	 *            the field to search
	 * @param clauses
	 *            the clauses to combine using AND
	 * @return result of the translation
	 */
	T and(String fieldName, List<T> clauses);

	/**
	 * Token-level OR.
	 *
	 * @param fieldName
	 *            the field to search
	 * @param clauses
	 *            the clauses to combine using OR
	 * @return result of the translation
	 */
	T or(String fieldName, List<T> clauses);

	/**
	 * Sequence query.
	 *
	 * @param fieldName
	 *            the field to search
	 * @param clauses
	 *            the clauses to find in sequence
	 * @return result of the translation
	 */
	T sequence(String fieldName, List<T> clauses);

	// TODO: This is the same for all implementations? Convert to abstract class and implement here?
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
	T property(String fieldName, String propertyName, String altName, TextPattern input);

	T docLevelAnd(String fieldName, List<T> clauses);

	T fuzzy(String fieldName, String value, float similarity, int prefixLength);

	T tags(String fieldName, String elementName);

	T containing(String fieldName, T containers, T search);

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
	T expand(T clause, boolean expandToLeft, int min, int max);

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
	T repetition(T clause, int min, int max);

	/**
	 * Inclusion/exclusion.
	 *
	 * @param include
	 *            clause that must occur
	 * @param exclude
	 *            clause that must not occur
	 * @return the resulting clause
	 */
	T docLevelAndNot(T include, T exclude);

	T wildcard(String fieldName, String value);

	T prefix(String fieldName, String value);
}
