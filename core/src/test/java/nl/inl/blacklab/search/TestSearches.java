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

import nl.inl.blacklab.TestIndex;
import nl.inl.blacklab.queryParser.corpusql.ParseException;
import nl.inl.blacklab.search.lucene.BLSpanTermQuery;
import nl.inl.blacklab.search.lucene.SpanQueryFiltered;

public class TestSearches {

	static TestIndex testIndex;

	/**
	 * Expected search results;
	 */
	List<String> expected;

	@BeforeClass
	public static void setUp() throws Exception {
		testIndex = new TestIndex();
	}

	@AfterClass
	public static void tearDown() {
	    if (testIndex != null)
	        testIndex.close();
	}

	@Test
	public void testSimple() throws ParseException {
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
	public void testSimpleDocFilter() throws ParseException {
		expected = Arrays.asList("May [the] Force");
		Assert.assertEquals(expected, testIndex.findConc(" 'the' ", new SingleDocIdFilter(1)));
	}

	@Test
	public void testFilteredQuery() throws ParseException {
		expected = Arrays.asList("[The] quick", "over [the] lazy");
		BLSpanTermQuery patternQuery = new BLSpanTermQuery(new Term("contents%word@i", "the"));
		TermQuery filterQuery = new TermQuery(new Term("contents%word@i", "fox"));
		Assert.assertEquals(expected, testIndex.findConc(new SpanQueryFiltered(patternQuery, filterQuery)));
	}

	@Test
	public void testSequences() throws ParseException {
		expected = Arrays.asList(
				"quick [brown fox] jumps",
				"the [lazy dog]");
		Assert.assertEquals(expected, testIndex.findConc(" [pos='adj'] [pos='nou'] "));
	}

	@Test
	public void testMatchAll() throws ParseException {
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
	public void testOptional1() throws ParseException {
		expected = Arrays.asList(
				"be [with you]",
				"with [you]",
				"to [find That] is",
				"find [That] is"
				);
		Assert.assertEquals(expected, testIndex.findConc(" []? [pos='pro'] "));
	}

	@Test
	public void testOptional2() throws ParseException {
		expected = Arrays.asList(
				"with [you]",
				"find [That] is",
				"find [That is] the"
				);
		Assert.assertEquals(expected, testIndex.findConc(" [pos='pro'] []? "));

	}

	@Test
	public void testOptional3() throws ParseException {
		expected = Arrays.asList(
				"be [with] you",
				"be [with you]",
				"with [you]",
				"To [find] or",
				"to [find] That",
				"to [find That] is",
				"find [That] is"
				);
		Assert.assertEquals(expected, testIndex.findConc(" 'with|find'? [pos='pro']? "));
	}

	@Test
	public void testRepetition() throws ParseException {
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
	public void testRepetitionNoResults() throws ParseException {
		expected = Arrays.asList();
		Assert.assertEquals(expected, testIndex.findConc("[pos='PD.*']+ '(?i)getal'"));

	}

	@Test
	public void testStringRegexes() throws ParseException {
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
	public void testUniq() throws ParseException {
		expected = Arrays.asList(
				"fox [jumps] over");
		Assert.assertEquals(expected, testIndex.findConc("[word = 'jumps' | lemma = 'jump']"));
	}

	@Test
	public void testOr() throws ParseException {
		expected = Arrays.asList(
				"fox [jumps] over",
				"jumps [over] the");
		Assert.assertEquals(expected, testIndex.findConc("[word = 'jumps' | lemma = 'over']"));
	}

	@Test
	@Ignore
	public void testAnd() throws ParseException {
		expected = Arrays.asList(
				"The [quick] brown");
		Assert.assertEquals(expected, testIndex.findConc("[pos = 'adj' & lemma = '.*u.*']"));
	}

	@Test
	public void testTags() throws ParseException {
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
	public void testNfa4() throws ParseException {
		expected = Arrays.asList("[May the Force be with] you");
		Assert.assertEquals(expected, testIndex.findConc(" 'May' '.*e'+ 'with' "));
	}

	@Test
	public void testOnlyRepetition() throws ParseException {
		expected = Arrays.asList("[The] quick", "over [the] lazy", "May [the] Force", "is [the] question");
		Assert.assertEquals(expected, testIndex.findConc("[lemma='.*he']{0,10}"));
	}

	@Test
	public void testConstraintSimple0() throws ParseException {
		expected = Arrays.asList("the [Force] be");
		Assert.assertEquals(expected, testIndex.findConc("a:'Force' :: a.word = 'Force'"));
	}

	@Test
	public void testConstraintSimple1() throws ParseException {
		expected = Arrays.asList("noot [mier aap mier] mier", "noot [aap aap aap] aap", "aap [aap aap aap]");
		Assert.assertEquals(expected, testIndex.findConc("a:[] 'aap' b:[] :: a.word = b.word"));
	}

	@Test
	public void testConstraintSimple2() throws ParseException {
		expected = Arrays.asList("noot [mier aap mier] mier", "noot [aap aap aap] aap", "aap [aap aap aap]");
		Assert.assertEquals(expected, testIndex.findConc("a:[] 'aap' b:[] :: a.word = b.lemma"));
	}

	@Test
	public void testConstraintSimple3() throws ParseException {
		expected = Arrays.asList("noot [mier aap mier mier] mier");
		Assert.assertEquals(expected, testIndex.findConc("a:[] 'aap' 'mier' b:[] :: a.word = b.word"));
	}

	@Test
	public void testConstraintSimple4() throws ParseException {
		expected = Arrays.asList("[The quick brown fox jumps over the] lazy");
		Assert.assertEquals(expected, testIndex.findConc("a:[] ([]{1,5} containing 'brown') b:[] :: a.lemma = b.lemma"));
	}

	@Test
	public void testConstraintOr1() throws ParseException {
		expected = Arrays.asList("noot [mier aap mier] mier", "noot [aap aap aap] aap", "aap [aap aap aap]");
		Assert.assertEquals(expected, testIndex.findConc("a:[] 'aap' b:[] :: a.word = b.lemma | a.word = b.pos"));
	}

	@Test
	public void testConstraintOr2() throws ParseException {
		expected = Arrays.asList("noot [mier aap mier] mier", "noot [aap aap aap] aap", "aap [aap aap aap]");
		Assert.assertEquals(expected, testIndex.findConc("a:[] 'aap' b:[] :: a.word = b.lemma | a.lemma = b.word"));
	}

	@Test
	public void testConstraintAnd1() throws ParseException {
		expected = Arrays.asList();
		Assert.assertEquals(expected, testIndex.findConc("a:[] 'aap' b:[] :: a.word = b.lemma & a.word = b.pos"));
	}

	@Test
	public void testConstraintAnd2() throws ParseException {
		expected = Arrays.asList("noot [mier aap mier] mier", "noot [aap aap aap] aap", "aap [aap aap aap]");
		Assert.assertEquals(expected, testIndex.findConc("a:[] 'aap' b:[] :: a.word = b.lemma & a.word != b.pos"));
	}

	@Test
	public void testConstraintAnd3() throws ParseException {
		expected = Arrays.asList("noot [mier aap mier] mier", "noot [aap aap aap] aap", "aap [aap aap aap]");
		Assert.assertEquals(expected, testIndex.findConc("a:[] 'aap' b:[] :: a.word = b.lemma & a.pos = b.pos"));
	}

	@Test
	public void testConstraintImplication1() throws ParseException {
		expected = Arrays.asList(
				"[noot mier aap mier] mier",    // left side matches, right side holds
				"noot [mier aap mier] mier",    // left side doesn't match
				"noot [noot aap aap] aap",      // left side doesn't match
				"noot [noot aap aap aap] aap",  // left side matches, right side holds
				"noot [aap aap aap] aap",       // left side doesn't match
				"aap [aap aap aap]"             // left side doesn't match
				);
		// If left side of implication is true, right side must also be true
		Assert.assertEquals(expected, testIndex.findConc("(c:'noot')? a:[] 'aap' b:[] :: c -> (a.word = b.word)"));
	}

	@Test
	public void testConstraintImplication2() throws ParseException {
		expected = Arrays.asList(
				"noot [mier aap mier] mier",
				"noot [noot aap aap] aap",
				"noot [aap aap aap] aap",
				"aap [aap aap aap]");
		// If left side of implication is always false, right side is ignored
		Assert.assertEquals(expected, testIndex.findConc("(c:'NOTININDEX')? a:[] 'aap' b:[] :: c -> a.word = b.word"));
	}

	// Backreferences not implemented yet
	@Ignore
	@Test
	public void testBackref() throws ParseException {
		expected = Arrays.asList("noot [mier aap mier] mier", "noot [aap aap aap] aap", "aap [aap aap aap]");
		Assert.assertEquals(expected, testIndex.findConc("a:[] 'aap' b:[word = a.word]"));
	}

}
