package nl.inl.blacklab.search.lucene;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;

import org.apache.lucene.store.ByteArrayDataInput;
import org.apache.lucene.store.OutputStreamDataOutput;
import org.apache.lucene.util.BytesRef;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestMatchInfo {

    /** Bound for our random numbers, chosen safely to avoid over/underflow */
    public static final int RND_BOUND = Integer.MAX_VALUE / 3;

    public static final int NUMBER_OF_TESTS = 10_000;

    private Random random;

    @Before
    public void setUp() {
        random = new Random(1928374);
    }

    @Test
    public void testMatchInfoSerialization() throws IOException {
        for (int i = 0; i < NUMBER_OF_TESTS; i++) {

            // Create a random MatchInfo structure
            boolean onlyHasTarget = random.nextBoolean();
            int sourceStart = random.nextInt(RND_BOUND);
            int sourceEnd = sourceStart + random.nextInt(RND_BOUND);
            int targetStart = random.nextInt(RND_BOUND);
            int targetEnd = targetStart + random.nextInt(RND_BOUND);
            if (onlyHasTarget) {
                // We'll index the same values for source and target in this case,
                // even though source shouldn't be used.
                sourceStart = targetStart;
                sourceEnd = targetEnd;
            }
            RelationInfo matchInfo = new RelationInfo(null, onlyHasTarget, sourceStart, sourceEnd, targetStart, targetEnd);

            // Randomly index at either source or target
            int currentPos = random.nextBoolean() ? sourceStart : targetStart;

            // Encode the payload
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            matchInfo.serialize(currentPos, new OutputStreamDataOutput(os));
            byte[] payload = new BytesRef(os.toByteArray()).bytes;

            // Decode it again
            RelationInfo decoded = new RelationInfo();
            decoded.deserialize(currentPos, new ByteArrayDataInput(payload));

            Assert.assertEquals(matchInfo, decoded);
        }
    }
}
