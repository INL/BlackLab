/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.filter;

import java.io.IOException;

import junit.framework.Assert;
import nl.inl.util.Utilities;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.junit.Test;

public class TestTranscribeGermanAccentsFilter {

	@Test
	public void testRetrieve() throws IOException {
		TokenStream ts = new StubTokenStream(new String[] { "Köln", "Berlin" });
		try {
			ts = new TranscribeGermanAccentsFilter(ts);
			CharTermAttribute ta = ts.addAttribute(CharTermAttribute.class);
			Assert.assertTrue(ts.incrementToken());
			Assert.assertEquals("Koeln", Utilities.getTerm(ta));
			Assert.assertTrue(ts.incrementToken());
			Assert.assertEquals("Berlin", Utilities.getTerm(ta));
			Assert.assertFalse(ts.incrementToken());
		} finally {
			ts.close();
		}
	}

}
