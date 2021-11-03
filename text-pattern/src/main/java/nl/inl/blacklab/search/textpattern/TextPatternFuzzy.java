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

import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanFuzzyQuery;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 * A TextPattern matching a word with fuzzy matching.
 */
public class TextPatternFuzzy extends TextPattern {
    protected String value;

    private int maxEdits;

    private int prefixLength;

    public TextPatternFuzzy(String value, int maxEdits) {
        this(value, maxEdits, 0);
    }

    public TextPatternFuzzy(String value, int maxEdits, int prefixLength) {
        this.value = value;
        this.maxEdits = maxEdits;
        this.prefixLength = prefixLength;
    }

    public Term getTerm(String fieldName) {
        return new Term(fieldName, value);
    }

    @Override
    public BLSpanQuery translate(QueryExecutionContext context) {
        int prefixLength1 = prefixLength;
        String valuePrefix = context.subannotPrefix(); // for searching in "subproperties" (e.g. PoS features)
        prefixLength1 += valuePrefix.length();
        return new SpanFuzzyQuery(QueryInfo.create(context.index(), context.field()), new Term(context.luceneField(), valuePrefix + context.optDesensitize(value)),
                maxEdits, prefixLength1);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TextPatternFuzzy) {
            TextPatternFuzzy tp = ((TextPatternFuzzy) obj);
            return value.equals(tp.value) && maxEdits == tp.maxEdits && prefixLength == tp.prefixLength;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return value.hashCode() + 13 * maxEdits + 31 * prefixLength;
    }

    @Override
    public String toString() {
        return "FUZZY(" + value + ", " + maxEdits + ", " + prefixLength + ")";
    }
}
