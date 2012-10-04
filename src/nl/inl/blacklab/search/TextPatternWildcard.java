/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.search;

/**
 * A textpattern matching a simple wildcard expression.
 *
 * Internally, the wildcard expression is translated into a regular expression.
 */
public class TextPatternWildcard extends TextPatternTerm {

	public TextPatternWildcard(String value) {
		super(value);
	}

	@Override
	public <T> T translate(TextPatternTranslator<T> translator, String fieldName) {
		return translator.wildcard(fieldName, value);
	}

	/**
	 * Instantiate the "best" TextPattern class for the given regex. Tries to make a TextPatternTerm
	 * or TextPatternPrefix because those tend to be faster than TextPatternWildcard in Lucene.
	 *
	 * @param expression
	 *            the wildcard expression we want a TextPattern for
	 * @return the TextPattern
	 */
	public static TextPattern getAppropriatePattern(String expression) {
		// Hey, maybe it doesn't even contain wildcards?
		if (expression.indexOf("*") < 0 && expression.indexOf("?") < 0) {
			// Woot!
			return new TextPatternTerm(expression);
		}

		// Is it a prefix query? ("bla*")
		if (expression.indexOf('*') == expression.length() - 1 && expression.indexOf('?') < 0) {
			// Yes!
			String prefix = expression.substring(0, expression.length() - 1);
			return new TextPatternPrefix(prefix);
		}

		return new TextPatternWildcard(expression);
	}

}
