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

import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import nl.inl.blacklab.mocks.MockBlackLabIndex;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.HitPropertyDocumentId;
import nl.inl.blacklab.resultproperty.PropertyValueInt;
import nl.inl.blacklab.search.results.HitGroup;
import nl.inl.blacklab.search.results.HitGroups;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.Results;

public class TestResultsGrouper {
    int[] doc   = { 1, 2, 1, 3, 2, 1 };
    int[] start = { 1, 2, 3, 4, 5, 6 };
    int[] end   = { 7, 8, 9, 10, 11, 12 };

    @Test
    public void testGrouper() {
        MockBlackLabIndex index = new MockBlackLabIndex();
        
        IndexSearcher indexSearcher = Mockito.mock(IndexSearcher.class);
        Mockito.when(indexSearcher.getSimilarity(ArgumentMatchers.anyBoolean())).thenReturn(new BM25Similarity());

        index.setIndexSearcher(indexSearcher);
        Hits hits = Hits.fromArrays(QueryInfo.create(index), doc, start, end);
        HitProperty crit = new HitPropertyDocumentId();
        HitGroups grouper = hits.group(crit, Results.NO_LIMIT);

        Assert.assertEquals(3, grouper.size());
        PropertyValueInt one = new PropertyValueInt(1);
        HitGroup group1 = grouper.get(one);
        Assert.assertEquals(one, group1.identity());
        Assert.assertEquals(3, group1.size());
        Assert.assertEquals(1, group1.storedResults().get(0).doc());
        Assert.assertEquals(1, group1.storedResults().get(1).doc());
        Assert.assertEquals(1, group1.storedResults().get(2).doc());
        PropertyValueInt two = new PropertyValueInt(2);
        Assert.assertEquals(2, grouper.get(two).size());
        PropertyValueInt three = new PropertyValueInt(3);
        Assert.assertEquals(1, grouper.get(three).size());
    }
}
