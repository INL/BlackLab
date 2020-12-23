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
import org.apache.lucene.search.PrefixQuery;

import nl.inl.blacklab.exceptions.RegexpTooLarge;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanMultiTermQueryWrapper;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 * A TextPattern matching words that start with the specified prefix.
 */
public class TextPatternPrefix extends TextPatternTerm {
    public TextPatternPrefix(String value) {
        super(value);
    }

    @Override
    public BLSpanQuery translate(QueryExecutionContext context) throws RegexpTooLarge {
        try {
            return new BLSpanMultiTermQueryWrapper<>(QueryInfo.create(context.index(), context.field()), new PrefixQuery(new Term(context.luceneField(),
                    context.subannotPrefix() + context.optDesensitize(optInsensitive(context, value)))));
        } catch (StackOverflowError e) {
            // If we pass in a prefix expression matching a lot of words,
            // stack overflow may occur inside Lucene's automaton building
            // code and we may end up here.
            throw new RegexpTooLarge();
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TextPatternPrefix) {
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
        return "PREFIX(" + value + ")";
    }
}
