/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.suggest;

/**
 * Base class for a word-based suggester. Suggesters may be chained. The suggest() method produces a
 * Suggestions object that may contain suggestions of several different types (such as spelling
 * variations, inflected forms, related terms, etc.).
 */
public abstract class Suggester {
	/**
	 * Default constructor, without chaining.
	 */
	public Suggester() {
		//
	}

	/**
	 * Called by the client to get suggestions for a word
	 *
	 * @param word
	 *            the word
	 * @return the suggestions
	 */
	public Suggestions suggest(String word) {
		Suggestions sugg = new Suggestions(word);
		addSuggestions(word, sugg);
		return sugg;
	}

	/**
	 * Should be overridden by child classes to add suggestions to the provided Suggestions object.
	 *
	 * @param original
	 *            the original word
	 * @param sugg
	 *            the suggestions object to add to
	 */
	public abstract void addSuggestions(String original, Suggestions sugg);

}
