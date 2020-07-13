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
package nl.inl.blacklab.analysis;

import java.io.IOException;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.junit.Assert;
import org.junit.Test;

import nl.inl.blacklab.filter.StubTokenStream;

public class TestBLDutchTokenFilter {

    @Test
    public void testBasics() throws IOException {
        TokenStream ts = new StubTokenStream(
                new String[] { "hond", "a.u.b.", "bel(len)", "[pre]cursor", "zo'n", "'Hij", "zij'", "ex-man", "-" });
        try {
            ts = new BLDutchTokenFilter(ts);
            ts.reset();
            CharTermAttribute ta = ts.addAttribute(CharTermAttribute.class);
            Assert.assertTrue(ts.incrementToken());
            Assert.assertEquals("hond", new String(ta.buffer(), 0, ta.length()));
            Assert.assertTrue(ts.incrementToken());
            Assert.assertEquals("aub", new String(ta.buffer(), 0, ta.length()));
            Assert.assertTrue(ts.incrementToken());
            Assert.assertEquals("bellen", new String(ta.buffer(), 0, ta.length()));
            Assert.assertTrue(ts.incrementToken());
            Assert.assertEquals("precursor", new String(ta.buffer(), 0, ta.length()));
            Assert.assertTrue(ts.incrementToken());
            Assert.assertEquals("zo'n", new String(ta.buffer(), 0, ta.length()));
            Assert.assertTrue(ts.incrementToken());
            Assert.assertEquals("Hij", new String(ta.buffer(), 0, ta.length()));
            Assert.assertTrue(ts.incrementToken());
            Assert.assertEquals("zij", new String(ta.buffer(), 0, ta.length()));
            Assert.assertTrue(ts.incrementToken());
            Assert.assertEquals("ex-man", new String(ta.buffer(), 0, ta.length()));
            Assert.assertFalse(ts.incrementToken());
        } finally {
            ts.close();
        }
    }

}
