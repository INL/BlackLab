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
package nl.inl.blacklab.perdocument;

import org.junit.Assert;
import org.junit.Test;

import nl.inl.blacklab.mocks.MockBlackLabIndex;
import nl.inl.blacklab.search.results.DocResult;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.Results;

public class TestDocResults {

    @Test
    public void testDocResultsIterate() {
        int[] aDoc = { 1, 1, 2, 3, 3 };
        int[] aStart = { 1, 2, 3, 4, 5 };
        int[] aEnd = { 2, 3, 4, 5, 6 };

        try (MockBlackLabIndex index = new MockBlackLabIndex()) {
            Hits hits = Hits.fromArrays(index.createDefaultQueryInfo(), aDoc, aStart, aEnd);
            DocResults drs = hits.perDocResults(Results.NO_LIMIT);
    
            int[] expDoc = { 1, 2, 3 };
            int[] expHits = { 2, 1, 2 };
            int i = 0;
            for (DocResult dr : drs) {
                Assert.assertEquals(expDoc[i], dr.identity().id());
                Assert.assertEquals(expHits[i], dr.size());
                i++;
            }
        }
    }

}
