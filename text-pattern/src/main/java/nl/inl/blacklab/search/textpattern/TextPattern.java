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

import java.util.List;

import org.apache.lucene.search.Query;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.exceptions.RegexpTooLarge;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryFiltered;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 * Describes some pattern of words in a content field. The point of this
 * interface is to provide an abstract layer to describe the pattern we're
 * interested in, which can then be translated into, for example, a SpanQuery
 * object or a String, depending on our needs.
 */
public abstract class TextPattern {

    public static final int MAX_UNLIMITED = BLSpanQuery.MAX_UNLIMITED;

    static String inf(int max) {
        return BLSpanQuery.inf(max);
    }

    /**
     * Translate this TextPattern into a BLSpanQuery.
     *
     * @param context query execution context to use
     * @return result of the translation
     * @throws RegexpTooLarge if a regular expression was too large
     * @throws InvalidQuery if something else was wrong about the query (e.g. error in regex expression)
     */
    public abstract BLSpanQuery translate(QueryExecutionContext context) throws InvalidQuery;

    @Override
    public abstract String toString();

    protected String clausesToString(List<TextPattern> clauses) {
        StringBuilder b = new StringBuilder();
        for (TextPattern clause : clauses) {
            if (b.length() > 0)
                b.append(", ");
            b.append(clause.toString());
        }
        return b.toString();
    }

    @Override
    public abstract boolean equals(Object obj);

    @Override
    public abstract int hashCode();

    String optInsensitive(QueryExecutionContext context, String value) {
        return context.optDesensitize(value);
    }

    public BLSpanQuery toQuery(QueryInfo queryInfo) throws InvalidQuery {
        return toQuery(queryInfo, null);
    }

    public BLSpanQuery toQuery(QueryInfo queryInfo, Query filter) throws InvalidQuery {
        QueryExecutionContext context = queryInfo.index().defaultExecutionContext(queryInfo.field());
        BLSpanQuery spanQuery = translate(context);
        if (filter != null)
            spanQuery = new SpanQueryFiltered(spanQuery, filter);
        return spanQuery;
    }

}
