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
package nl.inl.util;

import java.io.File;
import java.io.FilenameFilter;
import java.text.Collator;
import java.text.Normalizer;
import java.text.ParseException;
import java.text.RuleBasedCollator;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nl.inl.util.FileUtil.FileTask;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.LogMergePolicy;
import org.apache.lucene.index.MergePolicy;
import org.apache.lucene.util.Version;

/**
 * Misc. utilities that haven't yet found a place in InlJavaLib.
 *
 * TODO: move to InlJavaLib (deprecate/remove here)
 */
public class Utilities {

	/**
	 * Removes temporary test directories that may be left over from previous test
	 * runs because of memory mapping file locking on Windows.
	 *
	 * It is good practice to start and end a test run by calling
	 * removeBlackLabTestDirs().
	 */
	public static void removeBlackLabTestDirs() {
		File tempDir = new File(System.getProperty("java.io.tmpdir"));

		// Remove old ContentStore test dirs from temp dir, if possible
		// (may not be possible because of memory mapping lock on Windows;
		//  in this case we just leave the files and continue)
		for (File testDir: tempDir.listFiles(new FilenameFilter() {
				@Override
				public boolean accept(File parentDir, String name) {
					return name.startsWith("BlackLabTest_");
				}
			})) {

			// Recursively delete this temp dir
			FileUtil.processTree(testDir, new FileTask() {
				@Override
				public void process(File f) {
					f.delete();
				}
			});
			testDir.delete();
		}
	}

	/**
	 * Create a temporary directory for BlackLab testing. A GUID
	 * is used to avoid collisions. Note that because of memory mapping and
	 * file locking issues, temp dirs may hang around. It is good practice
	 * to start and end a test run by calling removeBlackLabTestDirs().
	 *
	 * @param name descriptive name to be used in the temporary dir (useful while debugging)
	 * @return the newly created temp dir.
	 */
	public static File createBlackLabTestDir(String name) {
		File tempDir = new File(System.getProperty("java.io.tmpdir"));
		File file = new File(tempDir, "BlackLabTest_" + name + "_" + UUID.randomUUID().toString());
		file.mkdir();
		return file;
	}

	/** Matches whitespace. */
	private static final Pattern ws = Pattern.compile("\\s+");

	/**
	 * Here, we define punctuation as anything that is not an ASCII character, digit, whitespace or
	 * diacritical mark. This is used to remove punctuation from a normalized String.
	 *
	 * FIXME: Latin character assumption! Use POSIX character class \\p{P} ?
	 */
	private static Pattern punctuation = Pattern
			.compile("[^\\sA-Za-z0-9\\p{InCombiningDiacriticalMarks}]");

	/**
	 * Matches Unicode diacritics composition characters, which are separated out by the Normalizer
	 * and then discarded using this regex.
	 */
	private static Pattern diacritics = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

	/**
	 * Convert accented letters to their unaccented counterparts.
	 *
	 * @param input
	 *            the string possibly containing accented letters.
	 * @return the ASCCIfied version
	 */
	public static String removeAccents(String input) {
		// Separate characters into base character and diacritics characters
		String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);

		// Remove diacritics
		return diacritics.matcher(normalized).replaceAll("");
	}

	/**
	 * Keep all [accented] characters, numbers and spaces. Replace everything else with a space.
	 *
	 * @param input
	 *            the input string
	 * @return the string without punctuation
	 */
	public static String removePunctuation(String input) {
		// Decompose (separate) characters into base character and diacritics characters
		input = Normalizer.normalize(input, Normalizer.Form.NFD);

		// Remove punctuation
		input = punctuation.matcher(input).replaceAll(" ");

		// Recompose accented characters
		input = Normalizer.normalize(input, Normalizer.Form.NFC);

		return input;
	}

	/**
	 * Reverse the words in a string, by splitting on whitespace and gluing the resulting pieces
	 * together back-to-front. Useful for sorting on the left context of concordances.
	 *
	 * @param input
	 *            the input string
	 * @return the string with words reversed
	 */
	public static String reverseWordsInString(String input) {
		String[] parts = ws.split(input);
		StringBuilder b = new StringBuilder();
		for (int i = parts.length - 1; i >= 0; i--) {
			b.append(parts[i]);
			if (i > 0)
				b.append(' ');
		}
		return b.toString();
	}

	/**
	 * Sanitize a string for sorting.
	 *
	 * This means removing any accents and replacing any non-letter or digit (e.g. punctuation) with
	 * a space. Then whitespace is normalized and leading and trailing whitespace is removed.
	 *
	 * So, this method behaves as if implemented as follows:
	 *
<code>public static String sanitizeForSorting(String input)
{
	input = removeAccents(input);
	input = input.replaceAll("[^ 0-9a-zA-Z]", " ");
	input = input.replaceAll("\\s+", " ").trim();
	return input;
}</code>
     *
     * FIXME: Latin char assumptions!
	 *
	 * @param conc
	 *            the input string to be sanitized
	 * @return the sanitized string
	 */
	public static String sanitizeForSorting(String conc) {
		// First, remove accents
		conc = removeAccents(conc);

		// Allocate buffer.
		int inputLength = conc.length();
		int bufferLength = inputLength;
		char[] src = new char[bufferLength];

		// Get character array
		conc.getChars(0, inputLength, src, 0);

		// Loop through character array
		// char[] dst = new char[src.length];
		int dstIndex = 0;
		boolean previousCharWasWhitespace = false;

		// Skip leading nonalphanumerics
		int srcIndex = 0;
		for (; srcIndex < inputLength; srcIndex++) {
			char c = src[srcIndex];
			if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9') {
				// Alphanumeric; start copying
				break;
			}
		}

		// Copy alphanumerics and normalize invalid characters and whitespace to single space
		for (; srcIndex < inputLength; srcIndex++) {
			char c = src[srcIndex];
			if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9') {
				// Valid character, copy
				src[dstIndex] = c;
				dstIndex++;
				previousCharWasWhitespace = false;
			} else {
				// Invalid character or whitespace; normalize
				if (!previousCharWasWhitespace) {
					// First character: put space in buffer and set flag
					previousCharWasWhitespace = true;
					src[dstIndex] = ' ';
					dstIndex++;
				}
			}
		}

		if (previousCharWasWhitespace) {
			// Remove single space already placed at end to trim trailing whitespace
			dstIndex--;
		}

		return new String(src, 0, dstIndex);
	}

	/**
	 * Returns a new collator that takes spaces into account (unlike the default Java collators,
	 * which ignore spaces), so we can sort "per word".
	 *
	 * Example: with the default collator, "cat dog" would be sorted after "catapult" (a after d).
	 * With the per-word collator, "cat dog" would be sorted before "catapult" (cat before
	 * catapult).
	 *
	 * NOTE: the base collator must be a RuleBasedCollator, but the argument has type Collator for
	 * convenience (not having to explicitly cast when calling)
	 *
	 * @param base
	 *            the collator to base the per-word collator on.
	 * @return the per-word collator
	 */
	public static RuleBasedCollator getPerWordCollator(Collator base) {
		if (!(base instanceof RuleBasedCollator))
			throw new RuntimeException("Base collator must be rule-based!");

		try {
			// Insert a collation rule to sort the space character before the underscore
			RuleBasedCollator ruleBasedCollator = (RuleBasedCollator) base;
			String rules = ruleBasedCollator.getRules();
			return new RuleBasedCollator(rules.replaceAll("<'_'", "<' '<'_'"));
		} catch (ParseException e) {
			throw new RuntimeException(e);
		}
	}

	public static IndexWriterConfig getIndexWriterConfig(Analyzer analyzer, boolean create) {
		IndexWriterConfig config = new IndexWriterConfig(Version.LUCENE_30, analyzer);
		config.setOpenMode(create ? OpenMode.CREATE : OpenMode.CREATE_OR_APPEND);
		config.setRAMBufferSizeMB(150); // faster indexing

		// Set merge factor (if using LogMergePolicy, which is the default up to version LUCENE_32,
		// so yes)
		MergePolicy mp = config.getMergePolicy();
		if (mp instanceof LogMergePolicy) {
			((LogMergePolicy) mp).setMergeFactor(40); // faster indexing
		} else
			throw new RuntimeException("Not using LogMergePolicy??");
		return config;
	}

	public static String getTerm(CharTermAttribute a) {
		return new String(a.buffer(), 0, a.length());
	}

	private static Pattern regexCharacters = Pattern
			.compile("([\\+\\(\\)\\[\\]\\-\\^\\$\\{\\}\\.])");

	public static String getRegex(String wildcardExpr) {
		// Escape special regex characters behalve ? en *
		Matcher m = regexCharacters.matcher(wildcardExpr);
		String regex = m.replaceAll("\\\\$1");

		// Vertaal wildcardtekens ? en * naar regular expressies
		regex = regex.replaceAll("\\*", ".*"); // Wildcardteken '*' betekent
												// "0 of meer willekeurige tekens"
		regex = regex.replaceAll("\\?", "."); // Wildcardteken '?' betekent "1 willekeurig teken"
		regex = "^" + regex + "$";
		return regex;
	}

	/**
	 * Compare two arrays of ints by comparing each element in succession.
	 *
	 * The first difference encountered determines the result. If the
	 * arrays are of different length but otherwise equal, the longest
	 * array will be ordered after the shorter.
	 *
	 * @param a first array
	 * @param b second array
	 * @return 0 if equal, negative if a &lt; b, positive if a &gt; b
	 */
	public static int compareArrays(int[] a, int[] b) {
		int n = a.length;
		if (b.length < n)
			n = b.length;
		for (int i = 0; i < n; i++) {
			if (a[i] != b[i]) {
				return a[i] - b[i];
			}
		}
		if (a.length == b.length) {
			// Arrays are exactly equal
			return 0;
		}
		if (n == a.length) {
			// Array b is longer than a; sort it after a
			return -1;
		}
		// a longer than b
		return 1;
	}

	/**
	 * Compare two arrays by comparing each element in succession.
	 *
	 * The first difference encountered determines the result. If the
	 * arrays are of different length but otherwise equal, the longest
	 * array will be ordered after the shorter.
	 *
	 * If the elements of the array are themselves arrays, this function
	 * is called recursively.
	 *
	 * @param a first array
	 * @param b second array
	 * @return 0 if equal, negative if a &lt; b, positive if a &gt; b
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static int compareArrays(Object[] a, Object[] b) {
		int n = a.length;
		if (b.length < n)
			n = b.length;
		for (int i = 0; i < n; i++) {
			int cmp;

			// Figure out how to compare these two elements
			if (a[i] instanceof int[]) {
				// Use int array compare
				cmp = compareArrays((int[])a[i], (int[])b[i]);
			} else if (a[i] instanceof Object[]) {
				// Use Object array compare
				cmp = compareArrays((Object[])a[i], (Object[])b[i]);
			} else if (a[i] instanceof Comparable) {
				// Assume Comparable and use that
				cmp = ((Comparable)a[i]).compareTo(b[i]);
			} else {
				throw new RuntimeException("Cannot compare objects of type " + a[i].getClass());
			}

			// Did that decide the comparison?
			if (cmp != 0) {
				return cmp; // yep, done
			}
		}
		if (a.length == b.length) {
			// Arrays are exactly equal
			return 0;
		}
		if (n == a.length) {
			// Array b is longer than a; sort it after a
			return -1;
		}
		// a longer than b
		return 1;
	}

}
