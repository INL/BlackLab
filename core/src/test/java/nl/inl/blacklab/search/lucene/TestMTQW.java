package nl.inl.blacklab.search.lucene;

import org.junit.Assert;
import org.junit.Test;

public class TestMTQW {

    @Test
    public void testFindRegexPrefixSuffix() {
        Assert.assertEquals("test", BLSpanMultiTermQueryWrapper.findRegexPrefixSuffix("^(?-i)test$"));
        Assert.assertEquals("bana", BLSpanMultiTermQueryWrapper.findRegexPrefixSuffix("ba(na)+na"));
    }
}
