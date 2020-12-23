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
package nl.inl.blacklab.mocks;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.search.IndexSearcher;

import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.BLSpanWeight;
import nl.inl.blacklab.search.lucene.BLSpans;

/**
 * Stub SpanQuery class for testing. Takes arrays and iterates through 'hits'
 * from these arrays.
 */
public class MockSpanQuery extends BLSpanQuery {
    int[] doc;

    int[] start;

    int[] end;

    boolean isSimple;

    private boolean singleTokenSpans;

    private boolean sortedSpans;

    private boolean uniqueSpans;

    public MockSpanQuery(int[] doc, int[] start, int[] end, boolean isSimple) {
        super(null);
        this.doc = doc;
        this.start = start;
        this.end = end;
        this.isSimple = isSimple;

        sortedSpans = singleTokenSpans = uniqueSpans = true;
        int prevDoc = -1, prevStart = -1, prevEnd = -1;
        for (int i = 0; i < doc.length; i++) {
            if (end[i] - start[i] > 1) {
                // Some hits are longer than 1 token
                singleTokenSpans = false;
            }
            if (doc[i] == prevDoc) {
                if (prevStart > start[i] || prevStart == start[i] && prevEnd > end[i]) {
                    // Violates sorted rule (sorted by start point, then endpoint)
                    sortedSpans = false;
                }
                if (prevStart == start[i] && prevEnd == end[i]) {
                    // Duplicate, so not unique
                    // (this check only works if the spans is sorted but we take that into account below)
                    uniqueSpans = false;
                }
            }
            prevDoc = doc[i];
            prevStart = start[i];
            prevEnd = end[i];
        }
    }

    public MockSpanQuery(int[] doc, int[] start, int[] end) {
        this(doc, start, end, false);
    }

    @Override
    public BLSpanWeight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
        return new BLSpanWeight(this, searcher, null) {

            @Override
            public void extractTerms(Set<Term> terms) {
                // NOP
            }

            @Override
            public void extractTermContexts(Map<Term, TermContext> contexts) {
                // NOP
            }

            @Override
            public BLSpans getSpans(final LeafReaderContext context, Postings requiredPostings) throws IOException {
                return new MockSpans(doc, start, end);
            }
        };
    }

    @Override
    public String toString(String field) {
        return "MockSpanQuery()";
    }

    @Override
    public String getField() {
        return "dummy";
    }

    @Override
    public String getRealField() {
        return "dummy";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o instanceof MockSpanQuery) {
            final MockSpanQuery that = (MockSpanQuery) o;
            return doc == that.doc && start == that.start && end == that.end && isSimple == that.isSimple;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(doc) ^ Arrays.hashCode(start) ^ Arrays.hashCode(end) ^ (isSimple ? 0x23357649 : 0);
    }

    @Override
    public boolean hitsAllSameLength() {
        return singleTokenSpans;
    }

    @Override
    public int hitsLengthMin() {
        return singleTokenSpans ? 1 : 0;
    }

    @Override
    public int hitsLengthMax() {
        return singleTokenSpans ? 1 : Integer.MAX_VALUE;
    }

    @Override
    public boolean hitsEndPointSorted() {
        return singleTokenSpans && sortedSpans;
    }

    @Override
    public boolean hitsStartPointSorted() {
        return sortedSpans;
    }

    @Override
    public boolean hitsHaveUniqueStart() {
        return singleTokenSpans && sortedSpans && uniqueSpans;
    }

    @Override
    public boolean hitsHaveUniqueEnd() {
        return singleTokenSpans && sortedSpans && uniqueSpans;
    }

    @Override
    public boolean hitsAreUnique() {
        return sortedSpans && uniqueSpans;
    }

    @Override
    public long reverseMatchingCost(IndexReader reader) {
        return 0;
    }

    @Override
    public int forwardMatchingCost() {
        return 0;
    }

    @Override
    public BLSpanQuery rewrite(IndexReader reader) throws IOException {
        return this;
    }

}
