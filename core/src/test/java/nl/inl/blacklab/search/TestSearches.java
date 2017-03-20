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
	public void testOptional() throws ParseException {
		expected = Arrays.asList(
				"be [with you]",
				"with [you]",
				"to [find That] is",
				"find [That] is"
				);
		Assert.assertEquals(expected, testIndex.findConc(" []? [pos='pro'] "));

		expected = Arrays.asList(
				"with [you]",
				"find [That] is",
				"find [That is] the"
				);
		Assert.assertEquals(expected, testIndex.findConc(" [pos='pro'] []? "));

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

}
