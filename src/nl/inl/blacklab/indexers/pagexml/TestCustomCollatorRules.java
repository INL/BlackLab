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
package nl.inl.blacklab.indexers.pagexml;

import java.text.Collator;
import java.util.Arrays;
import java.util.Locale;

import nl.inl.util.Utilities;

/**
 * Simple test program to demonstrate corpus search functionality.
 */
public class TestCustomCollatorRules {

	public static void main(String[] args) {
		Collator base = Collator.getInstance(new Locale("nl"));

		Collator correct = Utilities.getPerWordCollator(base);

		String[] testStr = { "de noot", "den aap" };

		String[] test = testStr.clone();
		Arrays.sort(test, base);
		System.out.println("Default: " + Arrays.toString(test));

		test = testStr.clone();
		Arrays.sort(test, correct);
		System.out.println("Correct: " + Arrays.toString(test));
	}

}
