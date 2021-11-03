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

import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryAnyToken;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 * A 'gap' of a number of tokens we don't care about, with minimum and maximum
 * length.
 *
 * This may be used to implement a 'wildcard' token in a pattern language.
 */
public class TextPatternAnyToken extends TextPattern {
    /*
     * The minimum number of tokens in this stretch.
     */
    protected int min;

    /*
     * The maximum number of tokens in this stretch.
     */
    protected int max;

    public TextPatternAnyToken(int min, int max) {
        this.min = min;
        this.max = max;
    }

    public TextPattern repeat(int nmin, int nmax) {
        if (nmin == 1 && nmax == 1)
            return this;
        if (min == 1 && max == 1) {
            return new TextPatternAnyToken(nmin, nmax);
        }
        return new TextPatternRepetition(this, nmin, nmax);
    }

    @Override
    public BLSpanQuery translate(QueryExecutionContext context) {
        return new SpanQueryAnyToken(QueryInfo.create(context.index(), context.field()), min, max, context.luceneField());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TextPatternAnyToken) {
            TextPatternAnyToken tp = ((TextPatternAnyToken) obj);
            return min == tp.min && max == tp.max;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return min + 31 * max;
    }

    @Override
    public String toString() {
        return "ANYTOKEN(" + min + ", " + max + ")";
    }
}
