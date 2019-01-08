package nl.inl.blacklab.search;


import org.junit.Assert;
import org.junit.Test;

public class TestSingleDocIdFilter {

    @Test
    public void testEquals() {
        SingleDocIdFilter singleDocIdFilter = new SingleDocIdFilter(1);
        SingleDocIdFilter singleDocIdFilter2 = new SingleDocIdFilter(2);
        Assert.assertNotEquals(singleDocIdFilter,singleDocIdFilter2);
    }
}
