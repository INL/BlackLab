/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.indexers.pagexml;

import java.text.Collator;
import java.util.Arrays;
import java.util.Locale;

import nl.inl.util.Utilities;

/**
 * Simple test program to demonstrate corpus search functionality.
 */
public class TestCollatorAndTab {

	public static void main(String[] args) {
		Collator base = Collator.getInstance(new Locale("nl"));

		Collator correct = Utilities.getPerWordCollator(base);

		String[] testStr = { "Aap Mies;Vis", "Aap Noot;Vuur", "Aap;Mies", "Aap;Noot" };

		String[] test = testStr.clone();
		Arrays.sort(test, base);
		System.out.println("Default: " + Arrays.toString(test));

		test = testStr.clone();
		Arrays.sort(test, correct);
		System.out.println("Correct: " + Arrays.toString(test));
	}

}
