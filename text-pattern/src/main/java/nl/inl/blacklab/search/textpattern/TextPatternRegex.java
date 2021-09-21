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
package nl.inl.blacklab.search.textpattern;

import java.util.regex.Pattern;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.RegexpQuery;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.exceptions.RegexpTooLarge;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.BLSpanMultiTermQueryWrapper;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 * A TextPattern matching a regular expression.
 */
public class TextPatternRegex extends TextPatternTerm {
    /**
     * Instantiate a regex TextPattern.
     *
     * @param value
     */
    public TextPatternRegex(String value) {
        super(value);
    }

    @Override
    public BLSpanQuery translate(QueryExecutionContext context) throws InvalidQuery {
        TextPattern result = rewrite();
        if (result != this)
            return result.translate(context);
        String valueNoStartEndMatch = optInsensitive(context, value).replaceAll("^\\^|\\$$", "");
        try {
            return new BLSpanMultiTermQueryWrapper<>(QueryInfo.create(context.index(), context.field()), new RegexpQuery(
                    new Term(context.luceneField(),
                            context.subannotPrefix() + context.optDesensitize(valueNoStartEndMatch))));
        } catch (IllegalArgumentException e) {
            throw new InvalidQuery("Invalid query: " + e.getMessage() + " (while parsing regex)");
        } catch (StackOverflowError e) {
            // If we pass in a really large regular expression, like a huge
            // list of words combined with OR, stack overflow occurs inside
            // Lucene's automaton building code and we may end up here.
            throw new RegexpTooLarge();
        }
    }

    static final Pattern onlyLettersAndDigits = Pattern.compile("[\\w\\d]+", Pattern.UNICODE_CHARACTER_CLASS);

    /**
     * Rewrite to TextPatternTerm if value only contains letters and numbers.
     *
     * Also looks at (?i), (?-i), (?c) at the start of the pattern and converts that
     * into an appropriate TextPatternSensitive() wrapper.
     *
     * In all other cases, we keep TextPatternRegex because Lucene's regex, wildcard
     * and prefix queries all work in the same basic way (are converted into
     * AutomatonQuery's), so they are equally fast.
     *
     * @return the TextPattern
     */
    public TextPattern rewrite() {
        // Is it "any token"?
        if (value.equals("^.*$")) {
            return new TextPatternAnyToken(1, 1);
        }

        // If there's a case-sensitivity toggle flag after a
        // start-of-string match, put the flag first so we can
        // easily detect it below.
        String newValue = value.replaceAll("^\\^(\\(\\?\\-?\\w+\\))", "$1^");

        // Do we want to force an (in)sensitive search?
        boolean forceSensitive = false;
        boolean forceInsensitive = false;
        if (newValue.startsWith("(?-i)")) {
            forceSensitive = true;
            newValue = newValue.substring(5);
        } else if (newValue.startsWith("(?c)")) {
            forceSensitive = true;
            newValue = newValue.substring(4);
        } else if (newValue.startsWith("(?i)")) {
            forceInsensitive = true;
            newValue = newValue.substring(4);
        }

        // If this contains no funny characters, only (Unicode) letters and digits,
        // surrounded by ^ and $, turn it into a TermQuery, which might be a little
        // faster than doing it via RegexpQuery (which has to build an Automaton).
        TextPattern result = null;
        String term = newValue;
        if (term.length() >= 2 && term.charAt(0) == '^' && term.charAt(term.length() - 1) == '$') {
            term = term.substring(1, term.length() - 1);
            if (onlyLettersAndDigits.matcher(term).matches())
                result = new TextPatternTerm(term);
        }
        if (result == null) {
            // Not a term query. Did we strip off a sensitivity flag above?
            if (!forceSensitive && !forceInsensitive) {
                // Nope. Nothing to rewrite.
                return this;
            }
            // Yes. Create new TP from remaining regex, and add TextPatternSensitive below.
            result = new TextPatternRegex(newValue);
        }

        if (forceSensitive) {
            // Pattern started with (?-i) or (?c) to force it to be sensitive
            result = new TextPatternSensitive(MatchSensitivity.SENSITIVE, result);
        } else if (forceInsensitive) {
            // Pattern started with (?i) to force it to be insensitive
            result = new TextPatternSensitive(MatchSensitivity.INSENSITIVE, result);
        }

        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TextPatternRegex) {
            return super.equals(obj);
        }
        return false;
    }

    // appease PMD
    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public String toString() {
        return "REGEX(" + value + ")";
    }

}
