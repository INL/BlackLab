/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.search;

/**
 * Describes some pattern of words in a content field. The point of this interface is to provide an
 * abstract layer to describe the pattern we're interested in, which can then be translated into
 * either a Query or SpanQuery object, depending on our needs. Note that the returned Query object
 * may in some cases produce false positives; see the implementing classes (such as
 * TextPatternSequence) for details. It should be used as a first filtering tool.
 */
public abstract class TextPattern {
	/**
	 * Default constructor; does nothing.
	 */
	public TextPattern() {
		//
	}

	/**
	 * Translate this TextPattern into some other representation.
	 *
	 * For example, TextPatternTranslatorSpanQuery translates it into Lucene SpanQuerys.
	 *
	 * @param translator
	 *            the translator to use
	 * @param fieldName
	 *            name of the field to search
	 *
	 * @param <T>
	 *            type of object to translate to
	 * @return result of the translation
	 */
	public abstract <T> T translate(TextPatternTranslator<T> translator, String fieldName);

	/**
	 * Does this TextPattern match the empty sequence?
	 *
	 * For example, the query [word="cow"]* matches the empty sequence. We need to know this so we
	 * can generate the appropriate queries. A query of the form "AB*" would be translated into
	 * "A|AB+", so each component of the query actually generates non-empty matches.
	 *
	 * When translating, the translator will usually generate a query that doesn't match the empty
	 * sequence, because this is not a practical query to execute. It is up to the translator to
	 * make sure the empty-sequence-matching property of the pattern is correctly dealt with at a
	 * higher level (usually by executing two alternative queries, one with and one without the part
	 * that would match the empty sequence). It uses this method to do so.
	 *
	 * We default to no because most queries don't match the empty sequence.
	 *
	 * @return true if this pattern matches the empty sequence, false otherwise
	 */
	public boolean matchesEmptySequence() {
		return false;
	}

	@Override
	public String toString() {
		return toString("fieldName");
	}

	public String toString(String fieldName) {
		return translate(new TextPatternTranslatorString(), fieldName);
	}
}
