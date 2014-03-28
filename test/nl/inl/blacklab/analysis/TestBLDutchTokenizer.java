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
import java.io.Reader;
import java.io.StringReader;

import junit.framework.Assert;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.junit.Test;

public class TestBLDutchTokenizer {

	@Test
	public void testBasics() throws IOException {
		Reader r = new StringReader("\"hond, a.u.b.: bél(len); \t [pre]cursor \t\nzo'n 'Hij zij' ex-man -");
		TokenStream ts = new BLDutchTokenizer(r);
		ts.reset();
		try {
			CharTermAttribute ta = ts.addAttribute(CharTermAttribute.class);
			Assert.assertTrue(ts.incrementToken());
			Assert.assertEquals("hond", new String(ta.buffer(), 0, ta.length()));
			Assert.assertTrue(ts.incrementToken());
			Assert.assertEquals("a.u.b.", new String(ta.buffer(), 0, ta.length()));
			Assert.assertTrue(ts.incrementToken());
			Assert.assertEquals("bél(len)", new String(ta.buffer(), 0, ta.length()));
			Assert.assertTrue(ts.incrementToken());
			Assert.assertEquals("[pre]cursor", new String(ta.buffer(), 0, ta.length()));
			Assert.assertTrue(ts.incrementToken());
			Assert.assertEquals("zo'n", new String(ta.buffer(), 0, ta.length()));
			Assert.assertTrue(ts.incrementToken());
			Assert.assertEquals("'Hij", new String(ta.buffer(), 0, ta.length()));
			Assert.assertTrue(ts.incrementToken());
			Assert.assertEquals("zij'", new String(ta.buffer(), 0, ta.length()));
			Assert.assertTrue(ts.incrementToken());
			Assert.assertEquals("ex-man", new String(ta.buffer(), 0, ta.length()));
			Assert.assertTrue(ts.incrementToken());
			Assert.assertEquals("-", new String(ta.buffer(), 0, ta.length()));
			Assert.assertFalse(ts.incrementToken());
		} finally {
			ts.close();
		}
	}

}