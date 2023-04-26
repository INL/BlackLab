package nl.inl.blacklab.search;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.HitPropertyHitText;
import nl.inl.blacklab.resultproperty.HitPropertyLeftContext;
import nl.inl.blacklab.resultproperty.HitPropertyMultiple;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.resultproperty.PropertyValueContextWords;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.BLSpanTermQuery;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.lucene.SpanQueryFiltered;
import nl.inl.blacklab.search.results.DocResult;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.testutil.TestIndex;

@RunWith(Parameterized.class)
public class TestSearches {

    @Parameterized.Parameters(name = "index type {0}")
    public static Collection<TestIndex> typeToUse() {
        return TestIndex.typesForTests();
    }

    @Parameterized.Parameter
    public TestIndex testIndex;

    @Test
    public void testSimple() {
        List<String> expected = Arrays.asList(
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
        List<String> expected = List.of("May [the] Force");
        int docId = testIndex.getDocIdForDocNumber(2);
        Assert.assertEquals(expected, testIndex.findConc(" 'the' ", new SingleDocIdFilter(docId)));
    }

    @Test
    public void testSimpleTitleFilter() {
        List<String> expected = List.of("May [the] Force");
        // metadata is tokenized and lowercased by default
        Query filter = new TermQuery(new Term("title", "star"));
        Assert.assertEquals(expected, testIndex.findConc(" 'the' ", filter));
    }

    @Test
    public void testFilteredQuery() {
        List<String> expected = Arrays.asList("[The] quick", "over [the] lazy");
        BLSpanTermQuery patternQuery = new BLSpanTermQuery(null, new Term("contents%word@i", "the"));
        TermQuery filterQuery = new TermQuery(new Term("contents%word@i", "fox"));
        Assert.assertEquals(expected, testIndex.findConc(new SpanQueryFiltered(patternQuery, filterQuery)));
    }

    @Test
    public void testSequences() {
        List<String> expected = Arrays.asList(
                "quick [brown fox] jumps",
                "the [lazy dog]");
        Assert.assertEquals(expected, testIndex.findConc(" [pos='adj'] [pos='nou'] "));
        // Also test that forward index matching either the first or the second clause produces the same results
        Assert.assertEquals(expected, testIndex.findConc(" _FI1([pos='adj'], [pos='nou']) "));
        Assert.assertEquals(expected, testIndex.findConc(" _FI2([pos='adj'], [pos='nou']) "));
    }

    @Test
    public void testMoreSequencesFiMatch() {
        int expected = 33;
        //DEBUG Assert.assertEquals(expected, testIndex.findConc(" [] [] ").size());
        // Also test that forward index matching either the first or the second clause produces the same results
        Assert.assertEquals(expected, testIndex.findConc(" _FI1([], []) ").size());
        Assert.assertEquals(expected, testIndex.findConc(" _FI2([], []) ").size());
    }

    @Test
    public void testMatchAll() {
        List<String> expected = Arrays.asList(
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
        List<String> expected = Arrays.asList(
                "be [with you]",
                "with [you]",
                "to [find That] is",
                "find [That] is");
        Assert.assertEquals(expected, testIndex.findConc(" []? [pos='pro'] "));
    }

    @Test
    public void testOptional2() {
        List<String> expected = Arrays.asList(
                "with [you]",
                "find [That] is",
                "find [That is] the");
        Assert.assertEquals(expected, testIndex.findConc(" [pos='pro'] []? "));

    }

    @Test
    public void testOptional3() {
        List<String> expected = Arrays.asList(
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
        List<String> expected = List.of(
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
        List<String> expected = Collections.emptyList();
        Assert.assertEquals(expected, testIndex.findConc("[pos='PD.*']+ '(?i)getal'"));

    }

    @Test
    public void testStringRegexes() {
        List<String> expected = Arrays.asList(
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
        List<String> expected = List.of(
                "fox [jumps] over");
        Assert.assertEquals(expected, testIndex.findConc("[word = 'jumps' | lemma = 'jump']"));
    }

    @Test
    public void testOr() {
        List<String> expected = Arrays.asList(
                "fox [jumps] over",
                "jumps [over] the");
        Assert.assertEquals(expected, testIndex.findConc("[word = 'jumps' | lemma = 'over']"));
    }

    @Test
    public void testAnd() {
        List<String> expected = List.of(
                "The [quick] brown");
        Assert.assertEquals(expected, testIndex.findConc("[pos = 'adj' & lemma = '.*u.*']"));
    }

    @Test
    public void testTags() {
        List<String> expected = List.of(
                "[The quick brown fox] jumps", "over [the lazy dog]", "May [the Force] be");
        Assert.assertEquals(expected, testIndex.findConc("<entity/>"));

        expected = List.of(
                "quick [brown] fox");
        Assert.assertEquals(expected, testIndex.findConc(" 'b.*' within <entity/> "));

        expected = List.of(
                "[The quick brown fox] jumps");
        Assert.assertEquals(expected, testIndex.findConc(" <entity/> containing 'b.*' "));

        expected = List.of(
                "[The] quick");
        Assert.assertEquals(expected, testIndex.findConc(" <s> 'the' "));

        expected = List.of(
                "lazy [dog]");
        Assert.assertEquals(expected, testIndex.findConc(" 'dog' </s> "));
    }

    @Test
    public void testRelRspan() {
        if (testIndex.getIndexType() == BlackLabIndex.IndexType.INTEGRATED) {
            List<String> allRelations = List.of(
                    "[The quick brown fox] jumps", "over [the lazy dog]", "May [the Force] be");
            List<String> targets = List.of("fox [] jumps", "dog []", "Force [] be");
            List<String> sources = List.of("[] The", "over [] the", "May [] the");
            List<String> none = Collections.emptyList();
            String type = AnnotatedFieldNameUtil.tagFullRelationType("entity");

            Assert.assertEquals(allRelations, testIndex.findConc("rel('" + type + "', _, 'full')"));
            Assert.assertEquals(allRelations, testIndex.findConc("rel('" + type + "', 'forward', 'full')"));
            Assert.assertEquals(allRelations, testIndex.findConc("rspan(rel('" + type + "'), 'full')"));
            Assert.assertEquals(allRelations, testIndex.findConc("rspan(rel('" + type + "', _, 'source'), 'full')"));

            Assert.assertEquals(targets, testIndex.findConc("rel('" + type + "')"));
            Assert.assertEquals(targets, testIndex.findConc("rel('" + type + "', 'forward', 'target')"));
            Assert.assertEquals(targets, testIndex.findConc("rspan(rel('" + type + "', _, 'full'), 'target')"));
            Assert.assertEquals(targets, testIndex.findConc("rspan(rel('" + type + "', _, 'source'), 'target')"));

            Assert.assertEquals(sources, testIndex.findConc("rel('" + type + "', _, 'source')"));
            Assert.assertEquals(sources, testIndex.findConc("rspan(rel('" + type + "', _, 'full'), 'source')"));
            Assert.assertEquals(sources, testIndex.findConc("rspan(rel('" + type + "', _, 'target'), 'source')"));

            Assert.assertEquals(none, testIndex.findConc("rel('" + type + "', 'backward')"));
            Assert.assertEquals(none, testIndex.findConc("rel('" + type + "', 'root')"));
        }
    }

    @Test
    public void testNfa4() {
        List<String> expected = List.of("[May the Force be with] you");
        Assert.assertEquals(expected, testIndex.findConc(" 'May' '.*e'+ 'with' "));
    }

    @Test
    public void testOnlyRepetition() {
        List<String> expected = Arrays.asList("[The] quick", "over [the] lazy", "May [the] Force", "is [the] question");
        Assert.assertEquals(expected, testIndex.findConc("[lemma='.*he']{0,10}"));
    }

    @Test
    public void testConstraintSimple0() {
        List<String> expected = List.of("the [Force] be");
        Assert.assertEquals(expected, testIndex.findConc("a:'Force' :: a.word = 'Force'"));
    }

    @Test
    public void testConstraintSimple1() {
        List<String> expected = Arrays.asList("noot [mier aap mier] mier", "noot [aap aap aap] aap", "aap [aap aap aap]");
        Assert.assertEquals(expected, testIndex.findConc("a:[] 'aap' b:[] :: a.word = b.word"));
    }

    @Test
    public void testConstraintSimple2() {
        List<String> expected = Arrays.asList("noot [mier aap mier] mier", "noot [aap aap aap] aap", "aap [aap aap aap]");
        Assert.assertEquals(expected, testIndex.findConc("a:[] 'aap' b:[] :: a.word = b.lemma"));
    }

    @Test
    public void testConstraintSimple3() {
        List<String> expected = List.of("noot [mier aap mier mier] mier");
        Assert.assertEquals(expected, testIndex.findConc("a:[] 'aap' 'mier' b:[] :: a.word = b.word"));
    }

    @Test
    public void testConstraintSimple4() {
        List<String> expected = List.of("[The quick brown fox jumps over the] lazy");
        Assert.assertEquals(expected,
                testIndex.findConc("a:[] ([]{1,5} containing 'brown') b:[] :: a.lemma = b.lemma"));
    }

    @Test
    public void testConstraintSimple4a() {
        List<String> expected = Arrays.asList(
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
    public void testLocalConstraint() {
        List<String> expected = List.of("[The quick brown fox jumps over the lazy] dog");
        Assert.assertEquals(expected,
                testIndex.findConc("(a:[] ([]{1,5} containing 'brown') b:[] :: a.lemma = b.lemma) 'lazy'"));
    }

    @Test
    public void testLocalConstraintAnyTokens() {
        List<String> expected = List.of("noot [mier aap mier mier] mier", "aap [mier mier mier noot] noot", "noot [aap aap aap aap]");
        Assert.assertEquals(expected,
                testIndex.findConc("(a:[] [] b:[] :: a.lemma = b.lemma) []"));
    }

    @Test
    public void testNGramContainingWithAdjustment() {
        List<String> expected = Arrays.asList(
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
        List<String> expected = List.of(
                "[The quick brown fox jumps over] the"
        );
        Assert.assertEquals(expected,
                testIndex.findConc("'The' []{1,2} 'fox' []{1, 2} 'over' "));
    }

    @Test
    public void testConstraintOr1() {
        List<String> expected = Arrays.asList("noot [mier aap mier] mier", "noot [aap aap aap] aap", "aap [aap aap aap]");
        Assert.assertEquals(expected, testIndex.findConc("a:[] 'aap' b:[] :: a.word = b.lemma | a.word = b.pos"));
    }

    @Test
    public void testConstraintOr2() {
        List<String> expected = Arrays.asList("noot [mier aap mier] mier", "noot [aap aap aap] aap", "aap [aap aap aap]");
        Assert.assertEquals(expected, testIndex.findConc("a:[] 'aap' b:[] :: a.word = b.lemma | a.lemma = b.word"));
    }

    @Test
    public void testConstraintAnd1() {
        List<String> expected = List.of();
        Assert.assertEquals(expected, testIndex.findConc("a:[] 'aap' b:[] :: a.word = b.lemma & a.word = b.pos"));
    }

    @Test
    public void testConstraintAnd2() {
        List<String> expected = Arrays.asList("noot [mier aap mier] mier", "noot [aap aap aap] aap", "aap [aap aap aap]");
        Assert.assertEquals(expected, testIndex.findConc("a:[] 'aap' b:[] :: a.word = b.lemma & a.word != b.pos"));
    }

    @Test
    public void testConstraintAnd3() {
        List<String> expected = Arrays.asList("noot [mier aap mier] mier", "noot [aap aap aap] aap", "aap [aap aap aap]");
        Assert.assertEquals(expected, testIndex.findConc("a:[] 'aap' b:[] :: a.word = b.lemma & a.pos = b.pos"));
    }

    @Test
    public void testWeirdTwoPhaseIterFail() {
        testIndex.findConc("'noot'     [] 'aap'");

        //testIndex.findConc(  "[]");                        // SUCCEEDS
        //testIndex.findConc("a:[]");                        // SUCCEEDS
        //testIndex.findConc(   "'noot'     []");            // SUCCEEDS
        //testIndex.findConc(   "'noot'   a:[]");            // SUCCEEDS
        //testIndex.findConc(   "'noot'?  a:[]");            // SUCCEEDS
        //testIndex.findConc(   "'noot'        'aap'");      // SUCCEEDS
        //testIndex.findConc(   "'noot'?       'aap'");      // SUCCEEDS
        //testIndex.findConc(   "'noot'?    [] 'aap'");      // SUCCEEDS
        //testIndex.findConc("(c:'noot')?   [] 'aap'");      // SUCCEEDS
        //testIndex.findConc(            "a:[] 'aap' b:[]"); // SUCCEEDS
        //testIndex.findConc("            a:[] 'aap' b:[] :: c -> (a.word = b.word)"); // SUCCEEDS
        //testIndex.findConc(   "'noot'     [] 'aap'");      // FAILS
        //testIndex.findConc(   "'noot'   a:[] 'aap'");      // FAILS
        //testIndex.findConc(   "'noot'?  a:[] 'aap'");      // FAILS
        //testIndex.findConc("(c:'noot')? a:[] 'aap'");      // FAILS    (removed capture b and token)
        //testIndex.findConc("(c:'noot')? a:[] 'aap' b:[]"); // FAILS
        //testIndex.findConc("(c:'noot')? a:[] 'aap' b:[] :: c -> (a.word = b.word)"); // FAILS
    }

    @Test
    public void testConstraintImplication1() {
        List<String> expected = Arrays.asList(
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
        List<String> expected = Arrays.asList(
                "noot [mier aap mier] mier",
                "noot [noot aap aap] aap",
                "noot [aap aap aap] aap",
                "aap [aap aap aap]");
        // If left side of implication is always false, right side is ignored
        Assert.assertEquals(expected, testIndex.findConc("(c:'NOTININDEX')? a:[] 'aap' b:[] :: c -> a.word = b.word"));
    }

    @Test
    public void testSort() {
        List<String> expected = Arrays.asList(
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
        List<String> expected = Arrays.asList(
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
        List<String> expected = List.of(
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
        List<String> expected = List.of(
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

    @Test
    public void testCaptureGroups() {
        Hits hits = testIndex.find("A:'aap'");
        Assert.assertEquals(5, hits.size());
        Assert.assertTrue(hits.hasMatchInfo());
        MatchInfo[] group = hits.get(0).matchInfo();
        Assert.assertNotNull(group);
        Assert.assertEquals(1, group.length);
        Assert.assertEquals(2, group[0].getFullSpanStart());
        Assert.assertEquals(3, group[0].getFullSpanEnd());
    }

    @Test
    public void testDocResults() {
        DocResults allDocs = testIndex.index().queryDocuments(new MatchAllDocsQuery());

        // Check that the number is correct (e.g. metadata document is not matched)
        Assert.assertEquals(4, allDocs.size());

        // Check that all pids and titles are there
        Set<String> pids = new HashSet<>();
        Set<String> titles = new HashSet<>();
        for (DocResult d: allDocs) {
            Document doc = testIndex.index().luceneDoc(d.docId());
            pids.add(doc.get("pid"));
            titles.add(doc.get("title"));
        }
        Assert.assertEquals(Set.of("0", "1", "2", "3"), pids);
        Assert.assertEquals(Set.of("Pangram", "Learning words", "Star Wars", "Bastardized Shakespeare"), titles);
    }

}
