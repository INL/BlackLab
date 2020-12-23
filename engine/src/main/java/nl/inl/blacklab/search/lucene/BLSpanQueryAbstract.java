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
package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.lucene.index.IndexReader;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 * A base class for a SpanQuery with an array of clauses. Provides default
 * implementations of some abstract methods in SpanQuery.
 */
abstract class BLSpanQueryAbstract extends BLSpanQuery {
    /**
     * The field name for this query. The "base" part is only applicable when
     * dealing with annotated fields: the base field name of "contents" and
     * "contents%pos" would both be "contents".
     */
    protected String baseFieldName = "";

    protected String luceneFieldName = "";

    protected List<BLSpanQuery> clauses;
    
    public BLSpanQueryAbstract(QueryInfo queryInfo) {
        super(queryInfo);
    }

    public BLSpanQueryAbstract(BLSpanQuery first, BLSpanQuery second) {
        super(first != null ? first.queryInfo : second != null ? second.queryInfo : null);
        clauses = Arrays.asList(first, second);
        determineBaseFieldName();
    }

    public BLSpanQueryAbstract(BLSpanQuery clause) {
        super(clause != null ? clause.queryInfo : null);
        clauses = Arrays.asList(clause);
        determineBaseFieldName();
    }

    public BLSpanQueryAbstract(Collection<BLSpanQuery> clauscol) {
        super(clauscol.isEmpty() ? null : clauscol.iterator().next().queryInfo);
        clauses = new ArrayList<>(clauscol);
        determineBaseFieldName();
    }

    public BLSpanQueryAbstract(BLSpanQuery[] clauses) {
        super(clauses.length > 0 && clauses[0] != null ? clauses[0].queryInfo : null);
        this.clauses = Arrays.asList(clauses);
        determineBaseFieldName();
    }

    private void determineBaseFieldName() {
        if (!clauses.isEmpty()) {
            luceneFieldName = clauses.get(0).getRealField();
            baseFieldName = AnnotatedFieldNameUtil.getBaseName(clauses.get(0).getField());
            for (int i = 1; i < clauses.size(); i++) {
                String f = AnnotatedFieldNameUtil.getBaseName(clauses.get(i).getField());
                if (!baseFieldName.equals(f))
                    throw new BlackLabRuntimeException("Mix of incompatible fields in query ("
                            + baseFieldName + " and " + f + ")");
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || this.getClass() != o.getClass())
            return false;

        final BLSpanQueryAbstract that = (BLSpanQueryAbstract) o;

        return clauses.equals(that.clauses);
    }

    @Override
    public int hashCode() {
        int h = clauses.hashCode();
        h ^= (h << 10) | (h >>> 23);
        return h;
    }

    /**
     * Returns the name of the search field. In the case of a annotated field, the
     * clauses may actually query different properties of the same annotated field
     * (e.g. "description" and "description__pos"). That's why only the prefix is
     * returned.
     *
     * @return name of the search field
     */
    @Override
    public String getField() {
        return baseFieldName;
    }

    @Override
    public String getRealField() {
        return luceneFieldName;
    }

    List<BLSpanQuery> getClauses() {
        return clauses;
    }

    protected List<BLSpanQuery> rewriteClauses(IndexReader reader) throws IOException {
        List<BLSpanQuery> rewritten = new ArrayList<>(clauses.size());
        boolean someRewritten = false;
        for (BLSpanQuery c : clauses) {
            BLSpanQuery query = c == null ? null : (BLSpanQuery) c.rewrite(reader);
            rewritten.add(query);
            if (query != c)
                someRewritten = true;
        }
        return someRewritten ? rewritten : null;
    }

    public String clausesToString(String field) {
        StringBuilder buffer = new StringBuilder();
        for (BLSpanQuery clause : clauses) {
            if (buffer.length() > 0) {
                buffer.append(", ");
            }
            buffer.append(clause.toString(field));
        }
        return buffer.toString();
    }
    
    @Override
    public void setQueryInfo(QueryInfo queryInfo) {
        super.setQueryInfo(queryInfo);
        for (BLSpanQuery clause: clauses) {
            clause.setQueryInfo(queryInfo);
        }
    }
}
