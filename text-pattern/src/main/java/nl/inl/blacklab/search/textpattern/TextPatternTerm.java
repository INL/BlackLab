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

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.BLSpanTermQuery;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 * A TextPattern matching a word.
 */
public class TextPatternTerm extends TextPattern {
    protected String value;

    public String getValue() {
        return value;
    }

    public TextPatternTerm(String value) {
        this.value = value;
    }

    public Term getTerm(String fieldName) {
        return new Term(fieldName, value);
    }

    @Override
    public BLSpanQuery translate(QueryExecutionContext context) throws InvalidQuery {
        return new BLSpanTermQuery(QueryInfo.create(context.index(), context.field()), new Term(context.luceneField(),
                context.subannotPrefix() + context.optDesensitize(optInsensitive(context, value))));
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TextPatternTerm) {
            return value.equals(((TextPatternTerm) obj).value);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return "TERM(" + value + ")";
    }
}
