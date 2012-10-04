/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.search.sequences;

import nl.inl.blacklab.search.TextPattern;
import nl.inl.blacklab.search.TextPatternTranslator;

/**
 * Repetition of a pattern.
 */
public class TextPatternRepetition extends TextPattern {
	private TextPattern base;

	private int min;

	private int max;

	public TextPatternRepetition(TextPattern base, int min, int max) {
		this.base = base;
		this.min = min;
		this.max = max;
		if (max != -1 && min > max)
			throw new RuntimeException("min > max");
		if (min < 0)
			throw new RuntimeException("min < 0");
	}

	/**
	 * Repetition query matches the empty sequence iff min == 0.
	 */
	@Override
	public boolean matchesEmptySequence() {
		return min == 0;
	}

	@Override
	public <T> T translate(TextPatternTranslator<T> translator, String fieldName) {
		T baseTranslated = base.translate(translator, fieldName);

		// NOTE: the case min == 0 is handled higher up the TextPattern hierarchy
		// (by checking matchesEmptySequence()). When translating, we just pretend this
		// case is equal to min == 1.
		// A repetition with min == 0 in isolation would not make sense anyway, only
		// in terms of surrounding patterns.
		return translator.repetition(baseTranslated, min == 0 ? 1 : min, max);
	}

	@Override
	public String toString() {
		return "TextPatternRepetition(" + base + ", " + min + ", " + max + ")";
	}

}
