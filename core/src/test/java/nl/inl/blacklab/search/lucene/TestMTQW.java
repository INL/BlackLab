package nl.inl.blacklab.search.lucene;

import org.junit.Assert;
import org.junit.Test;

public class TestMTQW {

    @Test
    public void testCountRegexWordChars() {
        Assert.assertEquals(4, BLSpanMultiTermQueryWrapper.countRegexWordCharacters("^(?-i)test$"));
        Assert.assertEquals(6, BLSpanMultiTermQueryWrapper.countRegexWordCharacters("ba(na)+na"));
    }
}
