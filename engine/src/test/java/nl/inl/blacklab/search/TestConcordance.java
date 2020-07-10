package nl.inl.blacklab.search;

import org.junit.Assert;
import org.junit.Test;

public class TestConcordance {

    @Test
    public void testConcordanceOldConstructor() {
        Concordance c = new Concordance(new String[] { "the quick ", "brown", " fox" });
        Assert.assertEquals("the quick ", c.left());
        Assert.assertEquals("brown", c.match());
        Assert.assertEquals(" fox", c.right());
    }

    @Test
    public void testConcordanceNewConstructor() {
        Concordance c = new Concordance("the quick brown fox", 10, 15);
        Assert.assertEquals("the quick ", c.left());
        Assert.assertEquals("brown", c.match());
        Assert.assertEquals(" fox", c.right());
    }
}
