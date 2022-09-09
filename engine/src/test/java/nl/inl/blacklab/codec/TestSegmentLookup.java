package nl.inl.blacklab.codec;

import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestSegmentLookup {

    static class TestSegment {
        String name;
        int id;
        public TestSegment(String name, int id) {
            this.name = name;
            this.id = id;
        }
    }

    SegmentLookup<TestSegment> lookup;

    @Before
    public void setUp() {
        List<TestSegment> segments = List.of(
            new TestSegment("aap", 0),
            new TestSegment("noot", 50),
            new TestSegment("mies", 51),
            new TestSegment("vis", 100)
        );
        lookup = new SegmentLookup<>(segments, s -> s.id);
    }

    @Test
    public void testLookup() {
        Assert.assertEquals("aap", lookup.forId(0).name);
        Assert.assertEquals("aap", lookup.forId(1).name);
        Assert.assertEquals("aap", lookup.forId(49).name);
        Assert.assertEquals("noot", lookup.forId(50).name);
        Assert.assertEquals("mies", lookup.forId(51).name);
        Assert.assertEquals("mies", lookup.forId(99).name);
        Assert.assertEquals("vis", lookup.forId(100).name);
        Assert.assertEquals("vis", lookup.forId(1000000).name);
    }

}
