package nl.inl.blacklab.analysis;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.junit.Assert;
import org.junit.Test;

public class TestBLDutchTokenizer {

    @Test
    public void testBasics() throws IOException {
        Reader r = new StringReader("\"hond, a.u.b.: bél(len); \t [pre]cursor \t\nzo'n 'Hij zij' ex-man -");
        try (Tokenizer ts = new BLDutchTokenizer()) {
            ts.setReader(r);
            ts.reset();
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
        }
    }

}
