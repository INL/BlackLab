package nl.inl.blacklab.codec;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.junit.Assert;
import org.junit.Test;

public class TestThreeByteInt {

    @Test
    public void testAllValues() throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(3);
        for (int i = ThreeByteInt.MIN_VALUE; i <= ThreeByteInt.MAX_VALUE; i++) {
            buf.rewind();
            ThreeByteInt.write(b -> buf.put(b), i);
            buf.rewind();
            int j = ThreeByteInt.read(() -> buf.get());
            Assert.assertEquals(j, i);
        }
    }
}
