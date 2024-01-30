package nl.inl.blacklab.forwardindex;

import java.text.Collator;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import nl.inl.util.UtilsForTesting;

public class TestForwardIndexPosIncr {
    private AnnotationForwardIndex fi;

    // Words and position increments to store in forward index.
    // When multiple words occur at the same position, only the first one will be stored.
    // When a gap occurs, empty words will be added in between.
    final List<Pair<String, Integer>> tokens = List.of(
        Pair.of("How",  1), // (posincr at start is ignored) (pos 0)
        Pair.of("much", 1), // directly follows previous token (pos 1)
        Pair.of("many", 0), // same position as previous token (pos 1)
        Pair.of("lots", 0), // same position as previous token (pos 1)
        Pair.of("wood", 3)  // three positions after previous token (pos 4)
    );

    private UtilsForTesting.TestDir testDir;

    @Before
    public void setUpForwardIndex() {

        // Create new test dir
        testDir = UtilsForTesting.createBlackLabTestDir("ForwardIndexPosIncr");

        AnnotationForwardIndexExternalWriter fiw = (AnnotationForwardIndexExternalWriter)
                AnnotationForwardIndexExternalAbstract.open(null, testDir.file(), true, Collator.getInstance(),
                        true, null);
        try {
            // Store strings
            String[] words = tokens.stream().map(Pair::getLeft).toArray(String[]::new);
            Integer[] posIncrements = tokens.stream().map(Pair::getRight).toArray(Integer[]::new);
            Assert.assertEquals(0, fiw.addDocument(Arrays.asList(words), Arrays.asList(posIncrements)));
        } finally {
            fiw.close(); // close so everything is guaranteed to be written
        }
        fi = AnnotationForwardIndexExternalAbstract.open(null, testDir.file(), false, Collator.getInstance(),
                false, null);
    }

    @After
    public void tearDown() {
        fi = null;
        testDir.close();
    }

    @Test
    public void testRetrieve() {
        // Retrieve strings
        String[] expected = { "How", "much", "", "", "wood" };

        int[] retrieved = fi.retrievePart(0, -1, -1);
        for (int j = 0; j < retrieved.length; j++) {
            Assert.assertEquals(expected[j], fi.terms().get(retrieved[j]));
        }
    }
}
