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
