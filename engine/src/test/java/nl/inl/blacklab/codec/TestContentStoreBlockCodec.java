package nl.inl.blacklab.codec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

    @Parameters(name = "codec #{0}")
    public static Collection<Integer> codecToUse() {
        return List.of(0, 1);
    }

    /** Code of the codec to use */
    @Parameterized.Parameter
    public int blockCodecCode;

    /** Codec to use */
    ContentStoreBlockCodec blockCodec;

    private ContentStoreBlockCodec.Encoder encoder;

    private ContentStoreBlockCodec.Decoder decoder;

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
            testEncodeDecode(test);
        }
    }

    private String getTestTitle(Triple<String, Integer, Integer> test) {
        return "\"" + test.getLeft() + "\" " + test.getMiddle() + "-" + test.getRight();
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
            testEncodeDecode(test);
        }
    }

    private void testEncodeDecode(Triple<String, Integer, Integer> test) throws IOException {
        String testTitle = getTestTitle(test);
        String inputBuffer = test.getLeft();
        int offset = test.getMiddle();
        int length = test.getRight();

        String expected = inputBuffer.substring(offset, offset + length);

        // Basic encode/decode
        byte[] encoded = encoder.encode(inputBuffer, offset, length);
        String decoded = decoder.decode(encoded, 0, encoded.length);
        Assert.assertEquals(testTitle + " method 1", expected, decoded);

        // Encode/decode into existing buffer
        byte[] encodeBuffer = new byte[1024];
        int encodedLength = encoder.encode(inputBuffer, offset, length, encodeBuffer, 0, encodeBuffer.length);
        byte[] decodeBuffer = new byte[1024];
        int decodedLength = decoder.decode(encodeBuffer, 0, encodedLength, decodeBuffer, 0, decodeBuffer.length);
        Assert.assertTrue(testTitle + " method 2 success", decodedLength >= 0);
        String decoded2 = new String(decodeBuffer, 0, decodedLength, StandardCharsets.UTF_8);
        Assert.assertEquals(testTitle + " method 2 result", expected, decoded2);
    }
}
