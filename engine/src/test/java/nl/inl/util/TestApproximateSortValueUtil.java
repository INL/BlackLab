package nl.inl.util;

import java.text.Collator;
import java.util.Random;

import org.junit.Assert;
import org.junit.Test;

import nl.inl.blacklab.search.BlackLabIndexImpl;

public class TestApproximateSortValueUtil {

    /**
     * Test that some numbers encode to the expected value.
     * This method is implementation-dependent.
     */
    @Test
    public void intEncode() {
        Assert.assertEquals("0000000000", ASVUtil.encode(0));
        Assert.assertEquals("0000000001", ASVUtil.encode(1));
        Assert.assertEquals("0000000002", ASVUtil.encode(2));
        Assert.assertEquals("0123456789", ASVUtil.encode(123456789));
        Assert.assertEquals("2147483647", ASVUtil.encode(Integer.MAX_VALUE));
        Assert.assertThrows(IllegalArgumentException.class, () -> ASVUtil.encode(Integer.MIN_VALUE));
    }

    /**
     * Test that some encoded numbers are decoded to the expected value.
     * This method is implementation-dependent.
     */
    @Test
    public void intDecode() {
        Assert.assertEquals(0, ASVUtil.decodeInt("0000000000"));
        Assert.assertEquals(1, ASVUtil.decodeInt("0000000001"));
        Assert.assertEquals(2, ASVUtil.decodeInt("0000000002"));
        Assert.assertEquals(123456789, ASVUtil.decodeInt("0123456789"));
        Assert.assertEquals(Integer.MAX_VALUE, ASVUtil.decodeInt("2147483647"));
    }

    /**
     * Test that some numbers encode to the expected value.
     * This method is implementation-dependent.
     */
    @Test
    public void longEncode() {
        Assert.assertEquals("0000000000000000000", ASVUtil.encode(0L));
        Assert.assertEquals("0000000000000000001", ASVUtil.encode(1L));
        Assert.assertEquals("0000000000000000002", ASVUtil.encode(2L));
        Assert.assertEquals(Long.toString(Long.MAX_VALUE), ASVUtil.encode(Long.MAX_VALUE));
        Assert.assertThrows(IllegalArgumentException.class, () -> ASVUtil.encode(Long.MIN_VALUE));
    }

    /**
     * Test that some encoded numbers are decoded to the expected value.
     * This method is implementation-dependent.
     */
    @Test
    public void longDecode() {
        Assert.assertEquals(0L, ASVUtil.decodeLong("0000000000000000000"));
        Assert.assertEquals(1L, ASVUtil.decodeLong("0000000000000000001"));
        Assert.assertEquals(2L, ASVUtil.decodeLong("0000000000000000002"));
        Assert.assertEquals(Long.MAX_VALUE, ASVUtil.decodeLong(Long.toString(Long.MAX_VALUE)));
    }

    /**
     * Test that numbers encode+decode back to the correct value.
     *
     * Also tests that ASVs don't exceed max. length
     */
    @Test
    public void testNumbers() {
        Random r = new Random(23456);
        for (int i = 0; i < 1000; i++) {
            int x = Math.abs(r.nextInt());
            String encoded = ASVUtil.encode(x);
            Assert.assertTrue(encoded.length() < ASVUtil.MAX_VALUE_LENGTH);
            Assert.assertEquals("int", x, ASVUtil.decodeInt(encoded));
        }
        for (int i = 0; i < 1000; i++) {
            long x = Math.abs(r.nextLong());
            String encoded = ASVUtil.encode(x);
            Assert.assertTrue(encoded.length() < ASVUtil.MAX_VALUE_LENGTH);
            Assert.assertEquals("long", x, ASVUtil.decodeLong(ASVUtil.encode(x)));
        }
    }

    /**
     * Test that encoding doesn't change comparison outcome
     */
    @Test
    public void testComparisonInt() {
        Random r = new Random(45678);
        Collator coll = BlackLabIndexImpl.defaultCollator();
        for (int i = 0; i < 1000; i++) {
            int a = Math.abs(r.nextInt());
            int b = Math.abs(r.nextInt());
            String ae = ASVUtil.encode(a);
            String be = ASVUtil.encode(b);
            Assert.assertEquals("int " + a + ", " + b + " => " + ae + ", " + be, Integer.compare(a, b), coll.compare(ae, be));
        }
        for (int i = 0; i < 1000; i++) {
            long a = Math.abs(r.nextLong());
            long b = Math.abs(r.nextLong());
            String ae = ASVUtil.encode(a);
            String be = ASVUtil.encode(b);
            Assert.assertEquals("long " + a + ", " + b + " => " + ae + ", " + be, Long.compare(a, b), coll.compare(ae, be));
        }
    }
}
