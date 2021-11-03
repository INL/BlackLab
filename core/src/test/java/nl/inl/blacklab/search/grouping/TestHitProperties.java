package nl.inl.blacklab.search.grouping;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.mocks.MockTerms;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.HitPropertyContextWords;
import nl.inl.blacklab.resultproperty.HitPropertyHitText;
import nl.inl.blacklab.resultproperty.PropertyValueContextWords;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.results.HitGroup;
import nl.inl.blacklab.search.results.HitGroups;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.Results;
import nl.inl.blacklab.testutil.TestIndex;

public class TestHitProperties {

    private final static int NO_TERM = Terms.NO_TERM;

    private static TestIndex testIndex;
    
    private static BlackLabIndex index;

    private static Terms terms;

    private static Annotation wordAnnotation;

    @BeforeClass
    public static void setUp() {
        testIndex = new TestIndex();
        index = testIndex.index();
        wordAnnotation = index.mainAnnotatedField().annotation("word");
        terms = index.annotationForwardIndex(wordAnnotation).terms();
    }

    private static int term(String word) {
        return terms.indexOf(word);
    }

    @AfterClass
    public static void tearDown() {
        if (testIndex != null)
            testIndex.close();
    }

    @Test
    public void testHitPropHitText() {
        Hits hits = testIndex.find(" 'the' ");
        HitProperty p = new HitPropertyHitText(index, MatchSensitivity.SENSITIVE);
        HitGroups g = hits.group(p, Results.NO_LIMIT);
        HitGroup group = g.get(new PropertyValueContextWords(index, wordAnnotation, MatchSensitivity.SENSITIVE, new int[] { term("the") }, false));
        Assert.assertEquals(3, group.size());
        group = g.get(new PropertyValueContextWords(index, wordAnnotation, MatchSensitivity.SENSITIVE, new int[] { term("The") }, false));
        Assert.assertEquals(1, group.size());
    }

    @Test
    public void testHitPropContextWords() {
        Hits hits = testIndex.find(" 'the' ");
        HitProperty p = new HitPropertyContextWords(index, wordAnnotation, MatchSensitivity.SENSITIVE, "L1-1;H1-2");
        HitGroups g = hits.group(p, Results.NO_LIMIT);
        Assert.assertEquals(4, g.size());
        HitGroup group;
        group = g.get(
                new PropertyValueContextWords(index, wordAnnotation, MatchSensitivity.SENSITIVE, new int[] { NO_TERM, term("The"), NO_TERM }, false));
        Assert.assertEquals(1, group.size());
        group = g.get(
                new PropertyValueContextWords(index, wordAnnotation, MatchSensitivity.SENSITIVE, new int[] { term("over"), term("the"), NO_TERM }, false));
        Assert.assertEquals(1, group.size());
        group = g.get(
                new PropertyValueContextWords(index, wordAnnotation, MatchSensitivity.SENSITIVE, new int[] { term("May"), term("the"), NO_TERM }, false));
        Assert.assertEquals(1, group.size());
        group = g.get(
                new PropertyValueContextWords(index, wordAnnotation, MatchSensitivity.SENSITIVE, new int[] { term("is"), term("the"), NO_TERM }, false));
        Assert.assertEquals(1, group.size());
    }

    @Test
    public void testHitPropContextWordsReverse() {
        Hits hits = testIndex.find(" 'the' 'lazy' ");
        HitProperty p = new HitPropertyContextWords(index, wordAnnotation, MatchSensitivity.SENSITIVE, "L1;H2-1;R1");
        HitGroups g = hits.group(p, Results.NO_LIMIT);
        Assert.assertEquals(1, g.size());
        HitGroup group;
        group = g.get(new PropertyValueContextWords(index, wordAnnotation,
                MatchSensitivity.SENSITIVE, new int[] { term("over"), term("lazy"), term("the"), term("dog") }, false));
        Assert.assertEquals(1, group.size());
    }

    @Test
    public void testTermSerialization() {
        String[] words = { "aap", "~", "~~", "" };
        String[] expected = { "aap", "~~", "~~~", "" };
        MockTerms mockTerms = new MockTerms(words);
        for (int i = 0; i < mockTerms.numberOfTerms(); i++) {
            Assert.assertEquals(expected[i], mockTerms.serializeTerm(i));
            Assert.assertEquals(expected[i], mockTerms.serializeTerm(mockTerms.deserializeToken(expected[i])));
        }
        Assert.assertEquals("~", mockTerms.serializeTerm(Terms.NO_TERM));
        Assert.assertEquals("~", mockTerms.serializeTerm(mockTerms.deserializeToken("~")));
    }

}
