package nl.inl.blacklab.search.fimatch;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import nl.inl.blacklab.TestIndex;
import nl.inl.blacklab.queryParser.corpusql.ParseException;
import nl.inl.blacklab.search.lucene.SpanQuerySequence;

public class TestSearchesNfa {

	static TestIndex testIndex;

	/**
	 * Expected search results;
	 */
	List<String> expected;

	@BeforeClass
	public static void setUp() throws Exception {
		SpanQuerySequence.setNfaFactor(Integer.MAX_VALUE);
		testIndex = new TestIndex();
	}

	@AfterClass
	public static void tearDown() {
		testIndex.close();
		SpanQuerySequence.setNfaFactor(SpanQuerySequence.DEFAULT_NFA_FACTOR);
	}

	@Test
	public void testSequence1() throws ParseException {
		expected = Arrays.asList("[May the] Force");
		Assert.assertEquals(expected, testIndex.findConc(" 'May' 'the' "));
	}

	@Test
	public void testSequence2() throws ParseException {
		expected = Arrays.asList("[May the Force be with you]");
		Assert.assertEquals(expected, testIndex.findConc(" 'May' 'the' ('force' 'be' 'with') 'you' "));
	}

	@Test
	public void testSequence3() throws ParseException {
		expected = Collections.emptyList();
		Assert.assertEquals(expected, testIndex.findConc(" 'May' 'Force' "));
	}

	@Test
	public void testRepetition0() throws ParseException {
		expected = Arrays.asList("[May the] Force");
		Assert.assertEquals(expected, testIndex.findConc(" 'May' 'the'+ "));
	}

	@Test
	public void testRepetition1() throws ParseException {
		expected = Arrays.asList("[May the Force be with] you");
		Assert.assertEquals(expected, testIndex.findConc(" 'May' '.*e'+ 'with' "));
	}

	@Test
	public void testRepetition2() throws ParseException {
		expected = Arrays.asList("[May the Force be with] you");
		Assert.assertEquals(expected, testIndex.findConc(" 'May' '(?-i).*e'{2,3} 'with' "));
	}

	@Test
	public void testRepetition3() throws ParseException {
		expected = Arrays.asList("[May the] Force");
		Assert.assertEquals(expected, testIndex.findConc(" 'May' 'dsgsdg'* 'the' "));
	}

	@Test
	public void testRepetition4() throws ParseException {
		expected = Collections.emptyList();
		Assert.assertEquals(expected, testIndex.findConc(" 'May' 'dsgsdg'+ 'the' "));
	}

	@Test
	public void testRepetitionCaseSensitive() throws ParseException {
		expected = Arrays.asList("[May the Force be with] you");
		Assert.assertEquals(expected, testIndex.findConc(" 'May' '(?-i).*e'+ 'with' "));
	}

	@Test
	public void testCaseInsensitive() throws ParseException {
		expected = Arrays.asList("[The quick] brown", "May [the Force] be");
		Assert.assertEquals(expected, testIndex.findConc(" 'the' '.*c.' "));
	}

	@Test
	public void testExpansion1() throws ParseException {
		expected = Arrays.asList("[May the Force be with] you");
		Assert.assertEquals(expected, testIndex.findConc(" 'May' 'the' []{2,3} 'with' "));
	}

	@Test
	public void testExpansion2() throws ParseException {
		expected = Arrays.asList("[May the Force] be");
		Assert.assertEquals(expected, testIndex.findConc(" 'May' 'the' []{0,2} 'Force' "));
	}

	@Test
	public void testExpansion3() throws ParseException {
		expected = Collections.emptyList();
		Assert.assertEquals(expected, testIndex.findConc(" 'May' 'the' []+ 'Force' "));
	}

	@Test
	public void testExpansion4() throws ParseException {
		expected = Arrays.asList("[May the Force] be");
		Assert.assertEquals(expected, testIndex.findConc(" 'May' []+ 'Force' "));
	}

}
