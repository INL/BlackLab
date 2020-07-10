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

import org.apache.lucene.search.spans.Spans;
import org.junit.Test;

import nl.inl.blacklab.TestUtil;
import nl.inl.blacklab.mocks.MockSpans;

public class TestSpansNGrams {

    private static SpansNGrams getSpans() {
        // NOTE: in doc 1, all tokens except 0-1 match; in doc 3, all tokens match
        SpansNGrams spans = new SpansNGrams(null, "test", 2, 3);
        spans.setTest(true, 1); // no IndexReader available
        return spans;
    }

    @Test
    public void testSpansNGrams() throws IOException {
        Spans exp = MockSpans.fromLists(
                new int[] { 0, 0, 0, 0, 0, 0, 0 },
                new int[] { 0, 0, 1, 1, 2, 2, 3 },
                new int[] { 2, 3, 3, 4, 4, 5, 5 });
        TestUtil.assertEquals(exp, getSpans());
    }

}
