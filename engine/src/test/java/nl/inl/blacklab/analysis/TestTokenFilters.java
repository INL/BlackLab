package nl.inl.blacklab.analysis;

import java.io.IOException;
import java.util.List;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PayloadAttribute;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;
import org.junit.Assert;
import org.junit.Test;

import nl.inl.blacklab.index.annotated.TokenStreamFromList;
import nl.inl.util.StringUtil;

public class TestTokenFilters {

    @Test
    public void testDesensitizeFilter() throws IOException {
        List<String> tokens = List.of("Hé", "JIJ", "daar!");
        TokenStream tokenStream = new TokenStreamFromList(tokens, null, null);
        TokenFilter filter = new DesensitizeFilter(tokenStream, true, true);
        CharTermAttribute termAttr = filter.getAttribute(CharTermAttribute.class);
        for (String token: tokens) {
            Assert.assertTrue(filter.incrementToken());
            Assert.assertEquals(StringUtil.desensitize(token), termAttr.toString());
        }
        Assert.assertFalse(filter.incrementToken());
    }

    @Test
    public void testAddSecondaryValuePayloadFilter() throws IOException {
        // Even tokens are primary, odd tokens are secondary (posIncr == 0)
        List<String> tokens = List.of("Hé", "he", "JIJ", "jij", "daar!", "daar");
        MutableIntList increments = new IntArrayList();
        increments.addAll(0, 0, 1, 0, 1, 0);

        // Create token stream with s-primary indicators in payload
        TokenStream tokenStream = new TokenStreamFromList(tokens, increments, null);
        TokenFilter filter = new AddIsPrimaryValueToPayloadFilter(tokenStream);
        CharTermAttribute termAttr = filter.getAttribute(CharTermAttribute.class);
        PayloadAttribute payloadAttr = filter.getAttribute(PayloadAttribute.class);

        // Check the tokenstream and is-primary values.
        for (int i = 0; i < tokens.size(); i++) {
            Assert.assertTrue("incrementToken " + i, filter.incrementToken());
            Assert.assertEquals("equal " + i, tokens.get(i), termAttr.toString());
            boolean isPrimary = i == 0 || increments.get(i) > 0;
            Assert.assertEquals("isPrimary " + i, isPrimary, PayloadUtils.isPrimaryValue(payloadAttr.getPayload()));
        }
        Assert.assertFalse(filter.incrementToken());
    }
}
