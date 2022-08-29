package nl.inl.blacklab.analysis;

import java.io.IOException;
import java.util.List;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
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
        for (int i = 0; i < tokens.size(); i++) {
            Assert.assertTrue(filter.incrementToken());
            Assert.assertEquals(StringUtil.desensitize(tokens.get(i)), termAttr.toString());
        }
        Assert.assertFalse(filter.incrementToken());
    }

    @Test
    public void testAddSecondaryValuePayloadFilter() throws IOException {
        List<String> tokens = List.of("Hé", "he", "JIJ", "jij", "daar!", "daar");
        IntArrayList increments = new IntArrayList();
        increments.addAll(0, 0, 1, 0, 1, 0);
        TokenStream tokenStream = new TokenStreamFromList(tokens, increments, null);

        TokenFilter filter = new AddIsPrimaryValueAttributeFilter(tokenStream);
        CharTermAttribute termAttr = filter.getAttribute(CharTermAttribute.class);
        for (int i = 0; i < tokens.size(); i++) {
            Assert.assertTrue(filter.incrementToken());
            Assert.assertEquals(StringUtil.desensitize(tokens.get(i)), termAttr.toString());
        }
        Assert.assertFalse(filter.incrementToken());
    }

}
