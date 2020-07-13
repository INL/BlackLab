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

import java.text.Normalizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

/**
 * A collection of String-related utility methods and regular expression
 * patterns.
 */
public final class StringUtil {
    /** nbsp character (decimal 160 = hex A0) */
    public static final char CHAR_NON_BREAKING_SPACE = '\u00A0';

    /** Matches whitespace. */
    private static final Pattern PATT_WHITESPACE = Pattern.compile("\\s+");

    /** Whitespace and/or punctuation at end */
    private static final Pattern PATT_WS_PUNCT_AT_END = Pattern.compile("[\\p{P}\\s]+$");

    /** Whitespace and/or punctuation at start */
    private static final Pattern PATT_WS_PUNCT_AT_START = Pattern.compile("^[\\p{P}\\s]+");

    private static final Pattern PATT_REGEX_CHARACTERS = Pattern.compile("([\\|\\\\\\?\\*\\+\\(\\)\\[\\]\\-\\^\\$\\{\\}\\.])");

    private static final Pattern PATT_DIACRITICAL_MARKS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    private StringUtil() {
    }

    /**
     * Escape regex special characters
     * 
     * (Pattern.quote() also does this, but this method is needed if you use a different regex
     *  engine from Java's, such as with Lucene)
     *
     * @param termStr the string to escape characters in
     * @return the escaped string
     */
    public static String escapeRegexCharacters(String termStr) {
        Matcher m = PATT_REGEX_CHARACTERS.matcher(termStr);
        termStr = m.replaceAll("\\\\$1");
        return termStr;
    }

    /**
     * Replace adjacent whitespace characters with a single space
     *
     * @param s source string
     * @return the result
     */
    public static String normalizeWhitespace(String s) {
        Matcher m = PATT_WHITESPACE.matcher(s);
        return m.replaceAll(" ");
    }

    /**
     * <p>
     * Removes diacritics (~= accents) from a string. The case will not be altered.
     * </p>
     * <p>
     * For instance, '&agrave;' will be replaced by 'a'.
     * </p>
     * <p>
     * Note that ligatures will be left as is.
     * </p>
     *
     * <pre>
     * StringUtils.stripAccents(null)                = null
     * StringUtils.stripAccents("")                  = ""
     * StringUtils.stripAccents("control")           = "control"
     * StringUtils.stripAccents("&eacute;clair")     = "eclair"
     * </pre>
     *
     * NOTE: this method was copied from Apache StringUtils. The only change is
     * precompiling the regular expression for efficiency.
     *
     * @param input String to be stripped
     * @return input text with diacritics removed
     *
     * @since 3.0
     */
    // See also Lucene's ASCIIFoldingFilter (Lucene 2.9) that replaces accented characters by their unaccented equivalent (and uncommitted bug fix: https://issues.apache.org/jira/browse/LUCENE-1343?focusedCommentId=12858907&page=com.atlassian.jira.plugin.system.issuetabpanels%3Acomment-tabpanel#action_12858907).
    public static String stripAccents(final String input) {
        if (input == null) {
            return null;
        }
        final StringBuilder decomposed = new StringBuilder(Normalizer.normalize(input, Normalizer.Form.NFD));
        convertRemainingAccentCharacters(decomposed);
        // Note that this doesn't correctly remove ligatures...
        return PATT_DIACRITICAL_MARKS.matcher(decomposed).replaceAll(StringUtils.EMPTY);
    }

    private static void convertRemainingAccentCharacters(StringBuilder decomposed) {
        for (int i = 0; i < decomposed.length(); i++) {
            if (decomposed.charAt(i) == '\u0141') {
                decomposed.deleteCharAt(i);
                decomposed.insert(i, 'L');
            } else if (decomposed.charAt(i) == '\u0142') {
                decomposed.deleteCharAt(i);
                decomposed.insert(i, 'l');
            }
        }
    }

    /**
     * Remove any punctuation and whitespace at the start and end of input.
     *
     * @param input the input string
     * @return the string without punctuation or whitespace at the edges.
     */
    public static String trimWhitespaceAndPunctuation(String input) {
        input = PATT_WS_PUNCT_AT_END.matcher(input).replaceAll("");
        input = PATT_WS_PUNCT_AT_START.matcher(input).replaceAll("");
        return input;
    }

    /**
     * A lowercase letter followed by an uppercase one, both matched in groups.
     */
    static Pattern lcaseUcase = Pattern.compile("(\\p{Ll})(\\p{Lu})");


    /**
     * Convert a string from a camel-case "identifier" style to a human-readable
     * version, by putting spaces between words, uppercasing the first letter and
     * lowercasing the rest.
     *
     * E.g. myCamelCaseString becomes "My camel case string".
     *
     * @param camelCaseString a string in camel case, i.e. multiple capitalized
     *            words glued together.
     * @param dashesToSpaces if true, also converts dashes and underscores to spaces
     * @return a human-readable version of the input string
     */
    public static String camelCaseToDisplayable(String camelCaseString, boolean dashesToSpaces) {
        String spaceified = camelCaseString;
        spaceified = lcaseUcase.matcher(spaceified).replaceAll("$1 $2");
        if (dashesToSpaces)
            spaceified = spaceified.replaceAll("[\\-_]", " ");
        return StringUtils.capitalize(spaceified.toLowerCase());
    }

    /**
     * For a number n, return a string like "nth".
     *
     * So 1 returns "1st", 2 returns "2nd", and so on.
     *
     * @param docNumber number
     * @return ordinal for that number
     */
    public static String ordinal(int docNumber) {
        final String[] ordSuffix = { "th", "st", "nd", "rd" };
        int i = docNumber;
        if (i < 1 || i > 3)
            i = 0;
        return docNumber + ordSuffix[i];
    }

    /**
     * Convert wildcard string to regex string.
     *
     * Adapted from: http://stackoverflow.com/a/28758377
     *
     * @param wildcard wildcard pattern
     * @return equivalent regex pattern
     */
    public static String wildcardToRegex(String wildcard) {
        StringBuffer s = new StringBuffer(wildcard.length());
        s.append('^');
        for (int i = 0, is = wildcard.length(); i < is; i++) {
            char c = wildcard.charAt(i);
            switch (c) {
            case '*':
                s.append(".*");
                break;
            case '?':
                s.append(".");
                break;
            // escape special regexp-characters
            case '^': // escape character in cmd.exe
            case '(':
            case ')':
            case '[':
            case ']':
            case '$':
            case '.':
            case '{':
            case '}':
            case '|':
            case '\\':
                s.append("\\");
                s.append(c);
                break;
            default:
                s.append(c);
                break;
            }
        }
        s.append('$');
        return s.toString();
    }

}
