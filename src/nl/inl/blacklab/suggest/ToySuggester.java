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
