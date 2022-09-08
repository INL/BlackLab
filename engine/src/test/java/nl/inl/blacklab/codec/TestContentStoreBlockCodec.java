package nl.inl.blacklab.codec;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

import org.apache.commons.lang3.tuple.Triple;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TestContentStoreBlockCodec {

    @Parameters
    public static Collection<Integer> data() {
        return List.of(0, 1);
    }

    int blockCodecCode;

    ContentStoreBlockCodec blockCodec;

    private ContentStoreBlockCodec.Encoder encoder;

    private ContentStoreBlockCodec.Decoder decoder;

    public TestContentStoreBlockCodec(Integer blockCodecCode) {
        this.blockCodecCode = blockCodecCode;
    }

    @Before
    public void setUp() {
        blockCodec = ContentStoreBlockCodec.fromCode((byte)blockCodecCode);
        encoder = blockCodec.getEncoder();
        decoder = blockCodec.getDecoder();
    }

    @Test(expected = Exception.class)
    public void testException() throws IOException {
        Collection<Triple<String, Integer, Integer>> tests = List.of(
                Triple.of("", 0, 1), // length out of bounds
                Triple.of("blablab", 0, -1), // length out of bounds
                Triple.of("blablab", -1, 1), // offset out of bounds
                Triple.of("blablab", 100, 1) ,// offset out of bounds
                Triple.of(null, 0, 0) // cannot be null
        );
        for (Triple<String, Integer, Integer> test: tests) {
            testEncodeDecode(test.getLeft(), test.getMiddle(), test.getRight());
        }
    }

    @Test
    public void testSimple() throws IOException {
        Collection<Triple<String, Integer, Integer>> tests = List.of(
                Triple.of("", 0, 0),
                Triple.of("testing", 1, 5),
                Triple.of("testing", 0, 7),
                Triple.of("a", 0, 1)
        );
        for (Triple<String, Integer, Integer> test: tests) {
            testEncodeDecode(test.getLeft(), test.getMiddle(), test.getRight());
        }
    }

    private void testEncodeDecode(String inputBuffer, int offset, int length) throws IOException {
        byte[] encoded = encoder.encode(inputBuffer, offset, length);
        String expected = inputBuffer.substring(offset, offset + length);
        String decoded = decoder.decode(encoded, 0, encoded.length);
        Assert.assertEquals(expected, decoded);
    }
}
