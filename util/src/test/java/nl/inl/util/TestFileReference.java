package nl.inl.util;

import org.junit.Assert;
import org.junit.Test;

public class TestFileReference {

    @Test
    public void testCharArray() {
        FileReference ref = FileReference.fromCharArray("path", new char[] {'a', 'b', 'c'}, null);
        byte[] b = ref.getBytes();
        Assert.assertEquals(3, b.length);
        Assert.assertEquals('a', b[0]);
        Assert.assertEquals('b', b[1]);
        Assert.assertEquals('c', b[2]);
    }

}
