package nl.inl.blacklab.search;

import java.util.Arrays;
import java.util.List;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.TermQuery;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.HitPropertyHitText;
import nl.inl.blacklab.resultproperty.HitPropertyLeftContext;
import nl.inl.blacklab.resultproperty.HitPropertyMultiple;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.resultproperty.PropertyValueContextWords;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.BLSpanTermQuery;
import nl.inl.blacklab.search.lucene.SpanQueryFiltered;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.testutil.TestIndex;

public class TestSearches {

    static TestIndex testIndex;

    /**
     * Expected search results;
     */
    List<String> expected;

    @BeforeClass
    public static void setUp() {
        testIndex = new TestIndex();
    }

    @AfterClass
    public static void tearDown() {
        if (testIndex != null)
            testIndex.close();
    }

    @Test
    public void testSimple() {
        expected = Arrays.asList(
                "[The] quick",
                "over [the] lazy",
                "May [the] Force",
                "is [the] question");
        Assert.assertEquals(expected, testIndex.findConc(" 'the' "));

        expected = Arrays.asList(
                "over [the] lazy",
                "May [the] Force",
                "is [the] question");
        Assert.assertEquals(expected, testIndex.findConc(" '(?-i)the' "));

        expected = Arrays.asList(
                "brown [fox] jumps",
                "lazy [dog]",
                "the [Force] be",
                "the [question]");
        Assert.assertEquals(expected, testIndex.findConc(" [pos='nou'] "));
    }

    @Test
    public void testSimpleDocFilter() {
        expected = Arrays.asList("May [the] Force");
        Assert.assertEquals(expected, testIndex.findConc(" 'the' ", new SingleDocIdFilter(1)));
    }

    @Test
    public void testFilteredQuery() {
        expected = Arrays.asList("[The] quick", "over [the] lazy");
        BLSpanTermQuery patternQuery = new BLSpanTermQuery(null, new Term("contents%word@i", "the"));
        TermQuery filterQuery = new TermQuery(new Term("contents%word@i", "fox"));
        Assert.assertEquals(expected, testIndex.findConc(new SpanQueryFiltered(patternQuery, filterQuery)));
    }

    @Test
    public void testSequences() {
        expected = Arrays.asList(
                "quick [brown fox] jumps",
                "the [lazy dog]");
        Assert.assertEquals(expected, testIndex.findConc(" [pos='adj'] [pos='nou'] "));
    }

    @Test
    public void testMatchAll() {
        expected = Arrays.asList(
                "brown [fox jumps] over",
                "the [Force be] with");
        Assert.assertEquals(expected, testIndex.findConc(" [pos='nou'] [] "));

        expected = Arrays.asList(
                "quick [brown fox] jumps",
                "the [lazy dog]",
                "May [the Force] be",
                "is [the question]");
        Assert.assertEquals(expected, testIndex.findConc(" [] [pos='nou'] "));
    }

    @Test
    public void testOptional1() {
        expected = Arrays.asList(
                "be [with you]",
                "with [you]",
                "to [find That] is",
                "find [That] is");
        Assert.assertEquals(expected, testIndex.findConc(" []? [pos='pro'] "));
    }

    @Test
    public void testOptional2() {
        expected = Arrays.asList(
                "with [you]",
                "find [That] is",
                "find [That is] the");
        Assert.assertEquals(expected, testIndex.findConc(" [pos='pro'] []? "));

    }

    @Test
    public void testOptional3() {
        expected = Arrays.asList(
                "be [with] you",
                "be [with you]",
                "with [you]",
                "To [find] or",
                "to [find] That",
                "to [find That] is",
                "find [That] is");
        Assert.assertEquals(expected, testIndex.findConc(" 'with|find'? [pos='pro']? "));
    }

    @Test
    public void testRepetition() {
        expected = Arrays.asList(
                "The [quick brown] fox");
        Assert.assertEquals(expected, testIndex.findConc(" [pos='adj']{2} "));

        expected = Arrays.asList(
                "The [quick] brown",
                "The [quick brown] fox",
                "quick [brown] fox",
                "the [lazy] dog");
        Assert.assertEquals(expected, testIndex.findConc(" [pos='adj']{1,} "));
    }

    @Test
    public void testRepetitionNoResults() {
        expected = Arrays.asList();
        Assert.assertEquals(expected, testIndex.findConc("[pos='PD.*']+ '(?i)getal'"));

    }

    @Test
    public void testStringRegexes() {
        expected = Arrays.asList(
                "quick [brown] fox",
                "Force [be] with");
        Assert.assertEquals(expected, testIndex.findConc(" 'b.*' "));

        expected = Arrays.asList(
                "brown [fox] jumps",
                "the [Force] be");
        Assert.assertEquals(expected, testIndex.findConc(" 'fo[xr].*' "));
    }

    @Test
    public void testUniq() {
        expected = Arrays.asList(
                "fox [jumps] over");
        Assert.assertEquals(expected, testIndex.findConc("[word = 'jumps' | lemma = 'jump']"));
    }

    @Test
    public void testOr() {
        expected = Arrays.asList(
                "fox [jumps] over",
                "jumps [over] the");
        Assert.assertEquals(expected, testIndex.findConc("[word = 'jumps' | lemma = 'over']"));
    }

    @Test
    @Ignore
    public void testAnd() {
        expected = Arrays.asList(
                "The [quick] brown");
        Assert.assertEquals(expected, testIndex.findConc("[pos = 'adj' & lemma = '.*u.*']"));
    }

    @Test
    public void testTags() {
        expected = Arrays.asList(
                "quick [brown] fox");
        Assert.assertEquals(expected, testIndex.findConc(" 'b.*' within <entity/> "));

        expected = Arrays.asList(
                "[The quick brown fox] jumps");
        Assert.assertEquals(expected, testIndex.findConc(" <entity/> containing 'b.*' "));

        expected = Arrays.asList(
                "[The] quick");
        Assert.assertEquals(expected, testIndex.findConc(" <s> 'the' "));

        expected = Arrays.asList(
                "lazy [dog]");
        Assert.assertEquals(expected, testIndex.findConc(" 'dog' </s> "));
    }

    @Test
    public void testNfa4() {
        expected = Arrays.asList("[May the Force be with] you");
        Assert.assertEquals(expected, testIndex.findConc(" 'May' '.*e'+ 'with' "));
    }

    @Test
    public void testOnlyRepetition() {
        expected = Arrays.asList("[The] quick", "over [the] lazy", "May [the] Force", "is [the] question");
        Assert.assertEquals(expected, testIndex.findConc("[lemma='.*he']{0,10}"));
    }

    @Test
    public void testConstraintSimple0() {
        expected = Arrays.asList("the [Force] be");
        Assert.assertEquals(expected, testIndex.findConc("a:'Force' :: a.word = 'Force'"));
    }

    @Test
    public void testConstraintSimple1() {
        expected = Arrays.asList("noot [mier aap mier] mier", "noot [aap aap aap] aap", "aap [aap aap aap]");
        Assert.assertEquals(expected, testIndex.findConc("a:[] 'aap' b:[] :: a.word = b.word"));
    }

    @Test
    public void testConstraintSimple2() {
        expected = Arrays.asList("noot [mier aap mier] mier", "noot [aap aap aap] aap", "aap [aap aap aap]");
        Assert.assertEquals(expected, testIndex.findConc("a:[] 'aap' b:[] :: a.word = b.lemma"));
    }

    @Test
    public void testConstraintSimple3() {
        expected = Arrays.asList("noot [mier aap mier mier] mier");
        Assert.assertEquals(expected, testIndex.findConc("a:[] 'aap' 'mier' b:[] :: a.word = b.word"));
    }

    @Test
    public void testConstraintSimple4() {
        expected = Arrays.asList("[The quick brown fox jumps over the] lazy");
        Assert.assertEquals(expected,
                testIndex.findConc("a:[] ([]{1,5} containing 'brown') b:[] :: a.lemma = b.lemma"));
    }

    @Test
    public void testConstraintSimple4a() {
        expected = Arrays.asList(
                "[The quick brown fox] jumps",
                "[The quick brown fox jumps] over",
                "[The quick brown fox jumps over] the",
                "[The quick brown fox jumps over the] lazy",
                "The [quick brown fox] jumps",
                "The [quick brown fox jumps] over",
                "The [quick brown fox jumps over] the",
                "The [quick brown fox jumps over the] lazy",
                "The [quick brown fox jumps over the lazy] dog"
                );
        Assert.assertEquals(expected,
                testIndex.findConc("a:[] ([]{1,5} containing 'brown') b:[]"));
    }

    @Test
    public void testNGramContainingWithAdjustment() {
        expected = Arrays.asList(
            "[The quick brown] fox",
            "[The quick brown fox] jumps",
            "[The quick brown fox jumps] over",
            "[The quick brown fox jumps over] the",
            "The [quick brown] fox",
            "The [quick brown fox] jumps",
            "The [quick brown fox jumps] over",
            "The [quick brown fox jumps over] the",
            "The [quick brown fox jumps over the] lazy"
            );
        Assert.assertEquals(expected,
                testIndex.findConc("[] ([]{1,5} containing 'brown')"));
    }

    @Test
    public void testExpandTwice() {
        expected = Arrays.asList(
            "[The quick brown fox jumps over] the"
            );
        Assert.assertEquals(expected,
                testIndex.findConc("'The' []{1,2} 'fox' []{1, 2} 'over' "));
    }

    @Test
    public void testConstraintOr1() {
        expected = Arrays.asList("noot [mier aap mier] mier", "noot [aap aap aap] aap", "aap [aap aap aap]");
        Assert.assertEquals(expected, testIndex.findConc("a:[] 'aap' b:[] :: a.word = b.lemma | a.word = b.pos"));
    }

    @Test
    public void testConstraintOr2() {
        expected = Arrays.asList("noot [mier aap mier] mier", "noot [aap aap aap] aap", "aap [aap aap aap]");
        Assert.assertEquals(expected, testIndex.findConc("a:[] 'aap' b:[] :: a.word = b.lemma | a.lemma = b.word"));
    }

    @Test
    public void testConstraintAnd1() {
        expected = Arrays.asList();
        Assert.assertEquals(expected, testIndex.findConc("a:[] 'aap' b:[] :: a.word = b.lemma & a.word = b.pos"));
    }

    @Test
    public void testConstraintAnd2() {
        expected = Arrays.asList("noot [mier aap mier] mier", "noot [aap aap aap] aap", "aap [aap aap aap]");
        Assert.assertEquals(expected, testIndex.findConc("a:[] 'aap' b:[] :: a.word = b.lemma & a.word != b.pos"));
    }

    @Test
    public void testConstraintAnd3() {
        expected = Arrays.asList("noot [mier aap mier] mier", "noot [aap aap aap] aap", "aap [aap aap aap]");
        Assert.assertEquals(expected, testIndex.findConc("a:[] 'aap' b:[] :: a.word = b.lemma & a.pos = b.pos"));
    }

    @Test
    public void testConstraintImplication1() {
        expected = Arrays.asList(
                "[noot mier aap mier] mier", // left side matches, right side holds
                "noot [mier aap mier] mier", // left side doesn't match
                "noot [noot aap aap] aap", // left side doesn't match
                "noot [noot aap aap aap] aap", // left side matches, right side holds
                "noot [aap aap aap] aap", // left side doesn't match
                "aap [aap aap aap]" // left side doesn't match
        );
        // If left side of implication is true, right side must also be true
        Assert.assertEquals(expected, testIndex.findConc("(c:'noot')? a:[] 'aap' b:[] :: c -> (a.word = b.word)"));
    }

    @Test
    public void testConstraintImplication2() {
        expected = Arrays.asList(
                "noot [mier aap mier] mier",
                "noot [noot aap aap] aap",
                "noot [aap aap aap] aap",
                "aap [aap aap aap]");
        // If left side of implication is always false, right side is ignored
        Assert.assertEquals(expected, testIndex.findConc("(c:'NOTININDEX')? a:[] 'aap' b:[] :: c -> a.word = b.word"));
    }

    @Test
    public void testSort() {
        expected = Arrays.asList(
                "aap [aap aap aap]",
                "noot [aap aap aap] aap",
                "noot [mier aap mier] mier",
                "noot [noot aap aap] aap"
                );
        // If left side of implication is always false, right side is ignored
        HitProperty hit = new HitPropertyHitText(testIndex.index(), MatchSensitivity.INSENSITIVE);
        HitProperty left = new HitPropertyLeftContext(testIndex.index(), MatchSensitivity.INSENSITIVE);
        HitProperty sortBy = new HitPropertyMultiple(hit, left);
        Assert.assertEquals(expected, testIndex.findConc("(c:'NOTININDEX')? a:[] 'aap' b:[] :: c -> a.word = b.word", sortBy));
    }

    @Test
    public void testSortReverse() {
        expected = Arrays.asList(
                "noot [noot aap aap] aap",
                "noot [mier aap mier] mier",
                "noot [aap aap aap] aap",
                "aap [aap aap aap]"
                );
        // If left side of implication is always false, right side is ignored
        HitProperty hit = new HitPropertyHitText(testIndex.index(), MatchSensitivity.INSENSITIVE);
        HitProperty left = new HitPropertyLeftContext(testIndex.index(), MatchSensitivity.INSENSITIVE);
        HitProperty sortBy = new HitPropertyMultiple(hit, left).reverse();
        Assert.assertEquals(expected, testIndex.findConc("(c:'NOTININDEX')? a:[] 'aap' b:[] :: c -> a.word = b.word", sortBy));
    }

    @Test
    public void testFilter() {
        expected = Arrays.asList(
                "noot [noot aap aap] aap"
                );
        // If left side of implication is always false, right side is ignored
        BlackLabIndex index = testIndex.index();
        HitProperty prop = new HitPropertyHitText(index, MatchSensitivity.INSENSITIVE);
        Annotation annotation = index.mainAnnotatedField().mainAnnotation();
        Terms terms = index.annotationForwardIndex(annotation).terms();
        int[] words = { terms.indexOf("noot"), terms.indexOf("aap"), terms.indexOf("aap") };
        PropertyValue value = new PropertyValueContextWords(index, annotation, MatchSensitivity.INSENSITIVE, words, false);
        Assert.assertEquals(expected, testIndex.findConc("(c:'NOTININDEX')? a:[] 'aap' b:[] :: c -> a.word = b.word", prop, value));
    }

    @Test
    public void testNGramsNotContaining() {
        expected = Arrays.asList(
                "noot [noot aap aap] aap"
                );
        BlackLabIndex index = testIndex.index();
        HitProperty prop = new HitPropertyHitText(index, MatchSensitivity.INSENSITIVE);
        Annotation annotation = index.mainAnnotatedField().mainAnnotation();
        Terms terms = index.annotationForwardIndex(annotation).terms();
        int[] words = { terms.indexOf("noot"), terms.indexOf("aap"), terms.indexOf("aap") };
        PropertyValue value = new PropertyValueContextWords(index, annotation, MatchSensitivity.INSENSITIVE, words, false);
        // Query below will be rewritten using POSFILTER(ANYTOKEN(1,INF), NOTCONTAINING, 'noot');
        // there used to be an issue with determining doc length that messed this up
        Assert.assertEquals(expected, testIndex.findConc("'noot'+ [word != 'noot']+ group:('aap')+", prop, value));
    }

    // Backreferences not implemented yet
    @Ignore
    @Test
    public void testBackref() {
        expected = Arrays.asList("noot [mier aap mier] mier", "noot [aap aap aap] aap", "aap [aap aap aap]");
        Assert.assertEquals(expected, testIndex.findConc("a:[] 'aap' b:[word = a.word]"));
    }

    @Test
    public void testCaptureGroups() {
        Hits hits = testIndex.find("A:'aap'");
        Assert.assertEquals(5, hits.size());
        Assert.assertTrue(hits.hasCapturedGroups());
        Span[] group = hits.capturedGroups().get(hits.get(0));
        Assert.assertNotNull(group);
        Assert.assertEquals(1, group.length);
        Assert.assertEquals(2, group[0].start());
        Assert.assertEquals(3, group[0].end());
    }

}
