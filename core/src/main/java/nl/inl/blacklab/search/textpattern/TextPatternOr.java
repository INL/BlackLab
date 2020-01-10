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

import java.util.ArrayList;
import java.util.List;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanOrQuery;
import nl.inl.blacklab.search.lucene.BLSpanQuery;

/**
 * A TextPattern matching at least one of its child clauses.
 */
public class TextPatternOr extends TextPatternCombiner {

    public TextPatternOr(TextPattern... clauses) {
        super(clauses);
    }

    @Override
    public BLSpanQuery translate(QueryExecutionContext context) throws InvalidQuery {
        List<BLSpanQuery> chResults = new ArrayList<>(clauses.size());
        for (TextPattern cl : clauses) {
            chResults.add(cl.translate(context));
        }
        if (chResults.size() == 1)
            return chResults.get(0);
        return new BLSpanOrQuery(chResults.toArray(new BLSpanQuery[] {}));
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TextPatternOr) {
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
        return "OR(" + clausesToString(clauses) + ")";
    }
}
