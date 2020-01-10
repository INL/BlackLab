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

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQuerySequence;

/**
 * A sequence of patterns. The patterns specified may be any pattern, and may
 * themselves be sequences if desired.
 */
public class TextPatternSequence extends TextPatternAndNot {
    public TextPatternSequence(TextPattern... clauses) {
        super(clauses);
    }

    @Override
    public BLSpanQuery translate(QueryExecutionContext context) throws InvalidQuery {
        if (!exclude.isEmpty())
            throw new BlackLabRuntimeException("clausesNot not empty!");
        List<BLSpanQuery> chResults = new ArrayList<>();
        for (TextPattern cl : include) {
            chResults.add(cl.translate(context));
        }
        if (chResults.size() == 1)
            return chResults.get(0); // just one part, return that
        return new SpanQuerySequence(chResults);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TextPatternSequence) {
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
        return "SEQ(" + clausesToString(include) + ")";
    }
}
