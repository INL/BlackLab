package nl.inl.blacklab.filter;

import java.io.IOException;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.junit.Assert;
import org.junit.Test;

import nl.inl.blacklab.analysis.RemoveAllAccentsFilter;

public class TestRemoveAllAccentsFilter {

    @Test
    public void testRetrieve() throws IOException {
        TokenStream ts = new StubTokenStream(new String[] { "Hé", "jij" });
        try {
            ts = new RemoveAllAccentsFilter(ts);
            ts.reset();
            CharTermAttribute ta = ts.addAttribute(CharTermAttribute.class);
            Assert.assertTrue(ts.incrementToken());
            Assert.assertEquals("He", new String(ta.buffer(), 0, ta.length()));
            Assert.assertTrue(ts.incrementToken());
            Assert.assertEquals("jij", new String(ta.buffer(), 0, ta.length()));
            Assert.assertFalse(ts.incrementToken());
        } finally {
            ts.close();
        }
    }

}
