package nl.inl.blacklab.search.fimatch;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import nl.inl.blacklab.search.lucene.optimize.ClauseCombinerNfa;
import nl.inl.blacklab.testutil.TestIndex;

@RunWith(Parameterized.class)
public class TestSearchesNfa {

    @Parameterized.Parameters(name = "index type {0}")
    public static Collection<TestIndex> typeToUse() {
        return TestIndex.typesForTests();
    }

    @Parameterized.Parameter
    public TestIndex testIndex;

    /** Expected search results; */
    List<String> expected;

    @Before
    public void setUp() {
        ClauseCombinerNfa.setNfaThreshold(ClauseCombinerNfa.MAX_NFA_MATCHING);
    }

    @After
    public void tearDown() {
        ClauseCombinerNfa.setNfaThreshold(ClauseCombinerNfa.defaultForwardIndexMatchingThreshold);
    }

    @Test
    public void testSequence1() {
        expected = List.of("[May the] Force");
        Assert.assertEquals(expected, testIndex.findConc(" 'May' 'the' "));
    }

    @Test
    public void testSequence2a() {
        expected = List.of("[May the Force be with you]");
        Assert.assertEquals(expected, testIndex.findConc(" 'May' 'the' ('force' 'be' 'with') 'you' "));
    }

    @Test
    public void testSequence2b() {
        expected = List.of("[May the Force be with you]");
        Assert.assertEquals(expected, testIndex.findConc(" 'May' 'the' 'force' 'be' 'with' 'you' "));
    }

    @Test
    public void testSequence3() {
        expected = Collections.emptyList();
        Assert.assertEquals(expected, testIndex.findConc(" 'May' 'Force' "));
    }

    @Test
    public void testRepetition0() {
        expected = List.of("[May the] Force");
        Assert.assertEquals(expected, testIndex.findConc(" 'May' 'the'+ "));
    }

    @Test
    public void testRepetition1() {
        expected = List.of("[May the Force be with] you");
        Assert.assertEquals(expected, testIndex.findConc(" 'May' '.*e'+ 'with' "));
    }

    @Test
    public void testRepetition2() {
        expected = List.of("[May the Force be with] you");
        Assert.assertEquals(expected, testIndex.findConc(" 'May' '(?-i).*e'{2,3} 'with' "));
    }

    @Test
    public void testRepetition3() {
        expected = List.of("[May the] Force");
        Assert.assertEquals(expected, testIndex.findConc(" 'May' 'dsgsdg'* 'the' "));
    }

    @Test
    public void testRepetition4() {
        expected = Collections.emptyList();
        Assert.assertEquals(expected, testIndex.findConc(" 'May' 'dsgsdg'+ 'the' "));
    }

    @Test
    public void testRepetitionCaseSensitive() {
        expected = List.of("[May the Force be with] you");
        Assert.assertEquals(expected, testIndex.findConc(" 'May' '(?-i).*e'+ 'with' "));
    }

    @Test
    public void testCaseInsensitive() {
        expected = Arrays.asList("[The quick] brown", "May [the Force] be");
        Assert.assertEquals(expected, testIndex.findConc(" 'the' '.*c.' "));
    }

    @Test
    public void testExpansion1() {
        expected = List.of("[May the Force be with] you");
        Assert.assertEquals(expected, testIndex.findConc(" 'May' 'the' []{2,3} 'with' "));
    }

    @Test
    public void testExpansion2() {
        expected = List.of("[May the Force] be");
        Assert.assertEquals(expected, testIndex.findConc(" 'May' 'the' []{0,2} 'Force' "));
    }

    @Test
    public void testExpansion3() {
        expected = Collections.emptyList();
        Assert.assertEquals(expected, testIndex.findConc(" 'May' 'the' []+ 'Force' "));
    }

    @Test
    public void testExpansion4() {
        expected = List.of("[May the Force] be");
        Assert.assertEquals(expected, testIndex.findConc(" 'May' []+ 'Force' "));
    }

    @Test
    public void testRelativeFreqs2() {
        expected = List.of("[noot mier aap] mier");
        Assert.assertEquals(expected, testIndex.findConc(" 'noot' 'mier' 'aap' "));
    }

    @Test
    public void testOverlappingSequences() {
        expected = Arrays.asList("aap [mier mier mier noot noot] aap", "mier [mier mier noot noot] aap");
        Assert.assertEquals(expected, testIndex.findConc(" 'mier' []{1,2} 'noot' 'noot' "));
    }

    @Test
    public void testSuffix() {
        expected = List.of("[The quick] brown");
        Assert.assertEquals(expected, testIndex.findConc("\".*E\" \"quick\""));
    }

    @Test
    public void testNegation() {
        expected = List.of("mier [mier noot noot aap] aap");
        Assert.assertEquals(expected, testIndex.findConc("'mier' [word != 'aap|mier']+ 'aap'"));
    }

}
