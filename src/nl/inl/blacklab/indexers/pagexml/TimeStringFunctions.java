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

import java.util.regex.Pattern;

import nl.inl.util.Utilities;

/**
 * Simple test program to demonstrate corpus search functionality.
 */
public class TimeStringFunctions {
	/** Matches XML open and close tags. Used for tag removal before displaying blurb. */
	private static final Pattern xmlTags = Pattern.compile("</?(\\w+)\\b[^>]*>");

	/**
	 * Matches newlines (possibly surrounded by whitespace. Used for newline removal before
	 * displaying blurb.
	 */
	private static final Pattern newlines = Pattern.compile("\\s*\n\\s*");

	/** Matches XML less-than entity. Used for replacing with the character before displaying blurb. */
	private static final Pattern lt = Pattern.compile("&lt;");

	/**
	 * Matches XML greater-than entity. Used for replacing with the character before displaying
	 * blurb.
	 */
	private static final Pattern gt = Pattern.compile("&gt;");

	/**
	 * Matches XML double quote entity. Used for replacing with the character before displaying
	 * blurb.
	 */
	private static final Pattern quot = Pattern.compile("&quot;");

	/** Matches XML ampersand entity. Used for replacing with the character before displaying blurb. */
	private static final Pattern amp = Pattern.compile("&amp;");

	public static String xmlToPlainTextOld(String xmlString) {
		xmlString = xmlTags.matcher(xmlString).replaceAll("");
		xmlString = newlines.matcher(xmlString).replaceAll(" ");
		xmlString = lt.matcher(xmlString).replaceAll("<");
		xmlString = gt.matcher(xmlString).replaceAll(">");
		xmlString = quot.matcher(xmlString).replaceAll("\"");
		xmlString = amp.matcher(xmlString).replaceAll("&");
		return xmlString;
	}

	@SuppressWarnings("deprecation")
	public static void main(String[] args) {
		long t, x;

		/*
		 * // Compare xmlToPlainText versions
		 *
		 * t = System.currentTimeMillis(); x = 0; // used to prevent optimizing the call away for
		 * (int i = 0; i < 1000000; i++) { x +=
		 * Utilities.xmlToPlainText("test &gt;&#65;&#x42;   <bla &quot; >test</bla> test").length();
		 * } System.out.println("xmlToPlainText: " + (System.currentTimeMillis() - t) +
		 * "ms            [[" + x + "]]");
		 *
		 * t = System.currentTimeMillis(); x = 0; // used to prevent optimizing the call away for
		 * (int i = 0; i < 1000000; i++) { x +=
		 * xmlToPlainTextOld("test &gt;&#65;&#x42;   <bla &quot; >test</bla> test").length(); }
		 * System.out.println("xmlToPlainTextOld: " + (System.currentTimeMillis() - t) +
		 * "ms            [[" + x + "]]");
		 */
		/*
		 * // Compare sanitizeForSorting versions
		 *
		 * t = System.currentTimeMillis(); x = 0; // used to prevent optimizing the call away for
		 * (int i = 0; i < 1000000; i++) { x +=
		 * Utilities.sanitizeForSorting("  Hë,  :  bla    Hë,  :  bla    Hë,  :  bla  ").length(); }
		 * System.out.println("sanitizeForSorting: " + (System.currentTimeMillis() - t) +
		 * "ms            [[" + x + "]]");
		 *
		 * t = System.currentTimeMillis(); x = 0; // used to prevent optimizing the call away for
		 * (int i = 0; i < 1000000; i++) { x +=
		 * Utilities.sanitizeForSortingOld("  Hë,  :  bla    Hë,  :  bla    Hë,  :  bla  "
		 * ).length(); } System.out.println("sanitizeForSortingOld: " + (System.currentTimeMillis()
		 * - t) + "ms            [[" + x + "]]");
		 */

		// Compare xmlToSortable versions

		t = System.currentTimeMillis();
		x = 0; // used to prevent optimizing the call away
		for (int i = 0; i < 1000000; i++) {
			x += Utilities.xmlToSortableOld("test &gt;&#65;&#x42;   <bla &quot; >test</bla> test",
					true).length();
		}
		System.out.println("xmlToSortable: " + (System.currentTimeMillis() - t)
				+ "ms            [[" + x + "]]");

		t = System.currentTimeMillis();
		x = 0; // used to prevent optimizing the call away
		for (int i = 0; i < 1000000; i++) {
			x += Utilities.xmlToSortable("test &gt;&#65;&#x42;   <bla &quot; >test</bla> test",
					true).length();
		}
		System.out.println("xmlToSortableOpt: " + (System.currentTimeMillis() - t)
				+ "ms            [[" + x + "]]");

		t = System.currentTimeMillis();
		x = 0; // used to prevent optimizing the call away
		for (int i = 0; i < 1000000; i++) {
			x += Utilities.xmlToSortable("tëst &gt;&#65;&#x42;   <bla &quot; >test</bla> test",
					true).length();
		}
		System.out.println("xmlToSortableOpt met non-ASCII: " + (System.currentTimeMillis() - t)
				+ "ms            [[" + x + "]]");

	}

}
