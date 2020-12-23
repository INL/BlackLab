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

import org.apache.lucene.index.Term;
import org.apache.lucene.search.WildcardQuery;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.exceptions.RegexpTooLarge;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanMultiTermQueryWrapper;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 * A textpattern matching a simple wildcard expression.
 *
 * Internally, the wildcard expression is translated into a regular expression.
 */
public class TextPatternWildcard extends TextPatternTerm {

    public TextPatternWildcard(String value) {
        super(value);
    }

    @Override
    public BLSpanQuery translate(QueryExecutionContext context) throws InvalidQuery {
        TextPattern result = rewrite();
        if (result != this)
            return result.translate(context);
        try {
            return new BLSpanMultiTermQueryWrapper<>(QueryInfo.create(context.index(), context.field()), new WildcardQuery(new Term(context.luceneField(),
                    context.subannotPrefix() + context.optDesensitize(optInsensitive(context, value)))));
        } catch (StackOverflowError e) {
            // If we pass in a really large wildcard expression,
            // stack overflow might occurs inside Lucene's automaton building
            // code and we may end up here.
            throw new RegexpTooLarge();
        }
    }

    /**
     * Rewrite to the "best" TextPattern class for the given wildcard query. Tries
     * to make a TextPatternTerm or TextPatternPrefix because those tend to be
     * faster than TextPatternWildcard in Lucene.
     *
     * @return the TextPattern
     */
    public TextPattern rewrite() {
        // Hey, maybe it doesn't even contain wildcards?
        if (value.indexOf("*") < 0 && value.indexOf("?") < 0) {
            // Woot!
            return new TextPatternTerm(value);
        }

        // Replace multiple consecutive asterisks with a single one
        String newValue = value.replaceAll("\\*+", "*");

        // Is is "any word"?
        if (newValue.equals("*"))
            return new TextPatternAnyToken(1, 1);

        // Is it a prefix query? ("bla*")
        if (newValue.indexOf('*') == newValue.length() - 1 && newValue.indexOf('?') < 0) {
            // Yes!
            String prefix = newValue.substring(0, newValue.length() - 1);
            return new TextPatternPrefix(prefix);
        }

        if (!newValue.equals(value))
            return new TextPatternWildcard(newValue);

        // Can't simplify, just return ourselves
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TextPatternWildcard) {
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
        return "WILDCARD(" + value + ")";
    }
}
