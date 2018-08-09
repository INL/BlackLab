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
package nl.inl.blacklab.search.grouping;

import java.util.Map;

import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import nl.inl.blacklab.mocks.MockSearcher;
import nl.inl.blacklab.mocks.MockSpanQuery;
import nl.inl.blacklab.resultproperty.HitPropValue;
import nl.inl.blacklab.resultproperty.HitPropValueInt;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.HitPropertyDocumentId;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.results.HitGroup;
import nl.inl.blacklab.search.results.HitGroups;
import nl.inl.blacklab.search.results.HitsImpl;

public class TestResultsGrouper {
    int[] doc = { 1, 2, 1, 3, 2, 1 };
    int[] start = { 1, 2, 3, 4, 5, 6 };
    int[] end = { 7, 8, 9, 10, 11, 12 };

    @Test
    public void testGrouper() {
        BLSpanQuery query = new MockSpanQuery(doc, start, end);
        MockSearcher searcher = new MockSearcher();
        
        IndexSearcher indexSearcher = Mockito.mock(IndexSearcher.class);
        Mockito.when(indexSearcher.getSimilarity(ArgumentMatchers.anyBoolean())).thenReturn(new BM25Similarity());

        searcher.setIndexSearcher(indexSearcher);
        HitsImpl hits = HitsImpl.fromSpanQuery(searcher, query, null);
        HitProperty crit = new HitPropertyDocumentId(hits);
        HitGroups grouper = hits.groupedBy(crit);
        Map<HitPropValue, HitGroup> groups = grouper.getGroupMap();

        Assert.assertEquals(3, groups.size());
        HitPropValueInt one = new HitPropValueInt(1);
        HitGroup group1 = groups.get(one);
        Assert.assertEquals(one, group1.getIdentity());
        Assert.assertEquals(3, group1.size());
        Assert.assertEquals(1, group1.getHits().get(0).doc());
        Assert.assertEquals(1, group1.getHits().get(1).doc());
        Assert.assertEquals(1, group1.getHits().get(2).doc());
        HitPropValueInt two = new HitPropValueInt(2);
        Assert.assertEquals(2, groups.get(two).size());
        HitPropValueInt three = new HitPropValueInt(3);
        Assert.assertEquals(1, groups.get(three).size());
    }
}
