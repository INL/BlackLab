/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.suggest;

/**
 * A quick toy example of a Suggester.
 */
public class ToySuggester extends Suggester {

	@Override
	public void addSuggestions(String original, Suggestions sugg) {
		if (original.equals("the"))
			sugg.addSuggestion("toy", "ye");
		else if (original.equals("old")) {
			sugg.addSuggestion("toy", "olde");
		} else if (original.equals("shop"))
			sugg.addSuggestion("toy", "shoppe");
	}

	public static void main(String[] args) {
		String s = "the nice old toy shop";

		String[] w = s.split("\\s");

		Suggester sg = new ToySuggester();

		for (String word : w) {
			System.out.print(word + ": ");
			Suggestions suggs = sg.suggest(word);
			boolean any = false;
			for (String sugg : suggs.getAllSuggestionsList()) {
				System.out.print(sugg + " ");
			}
			any = true;
			if (!any) {
				System.out.print("(no suggestions)");
			}
			System.out.print("\n");
		}
	}

}
