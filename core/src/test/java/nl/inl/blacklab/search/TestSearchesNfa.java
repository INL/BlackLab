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
import nl.inl.blacklab.search.lucene.SpanQuerySequence;

public class TestSearchesNfa {

	static TestIndex testIndex;

	/**
	 * Expected search results;
	 */
	List<String> expected;

	@BeforeClass
	public static void setUp() throws Exception {
		SpanQuerySequence.setNfaFactor(2);
		testIndex = new TestIndex();
	}

	@AfterClass
	public static void tearDown() {
		testIndex.close();
		SpanQuerySequence.setNfaFactor(SpanQuerySequence.DEFAULT_NFA_FACTOR);
	}

	@Test
	public void testNfa1() throws ParseException {
		expected = Arrays.asList("[May the] Force");
		Assert.assertEquals(expected, testIndex.findConc(" 'May' 'the' "));
	}

//	@Test
//	public void testNfa2() throws ParseException {
//		expected = Arrays.asList("[May the] Force");
//		Assert.assertEquals(expected, testIndex.findConc(" 'May' 'the'+ "));
//	}

}
