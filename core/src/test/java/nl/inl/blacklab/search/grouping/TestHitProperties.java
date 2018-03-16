package nl.inl.blacklab.search.grouping;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import nl.inl.blacklab.MockTerms;
import nl.inl.blacklab.TestIndex;
import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.queryParser.corpusql.ParseException;
import nl.inl.blacklab.search.Hits;

public class TestHitProperties {

	private final static int NO_TERM = Terms.NO_TERM;

	private static TestIndex testIndex;

	private static Terms terms;

	@BeforeClass
	public static void setUp() throws Exception {
		testIndex = new TestIndex();
		terms = testIndex.getSearcher().getForwardIndex("contents%word").getTerms();
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
	public void testHitPropHitText() throws ParseException {
		Hits hits = testIndex.find(" 'the' ");
		HitProperty p = new HitPropertyHitText(hits, true);
		HitGroups g = hits.groupedBy(p);
		HitGroup group = g.getGroup(new HitPropValueContextWords(hits, "word", new int[] {term("the")}, true));
		Assert.assertEquals(3, group.size());
		group = g.getGroup(new HitPropValueContextWords(hits, "word", new int[] {term("The")}, true));
		Assert.assertEquals(1, group.size());
	}

	@Test
	public void testHitPropContextWords() throws ParseException {
		Hits hits = testIndex.find(" 'the' ");
		HitProperty p = new HitPropertyContextWords(hits, "contents", "word", true, "L1-1;H1-2");
		HitGroups g = hits.groupedBy(p);
		Assert.assertEquals(4, g.numberOfGroups());
		HitGroup group;
		group = g.getGroup(new HitPropValueContextWords(hits, "word", new int[] {NO_TERM, term("The"), NO_TERM}, true));
		Assert.assertEquals(1, group.size());
		group = g.getGroup(new HitPropValueContextWords(hits, "word", new int[] {term("over"), term("the"), NO_TERM}, true));
		Assert.assertEquals(1, group.size());
		group = g.getGroup(new HitPropValueContextWords(hits, "word", new int[] {term("May"), term("the"), NO_TERM}, true));
		Assert.assertEquals(1, group.size());
		group = g.getGroup(new HitPropValueContextWords(hits, "word", new int[] {term("is"), term("the"), NO_TERM}, true));
		Assert.assertEquals(1, group.size());
	}

	@Test
	public void testHitPropContextWordsReverse() throws ParseException {
		Hits hits = testIndex.find(" 'the' 'lazy' ");
		HitProperty p = new HitPropertyContextWords(hits, "contents", "word", true, "L1;H2-1;R1");
		HitGroups g = hits.groupedBy(p);
		Assert.assertEquals(1, g.numberOfGroups());
		HitGroup group;
		group = g.getGroup(new HitPropValueContextWords(hits, "word", new int[] {term("over"), term("lazy"), term("the"), term("dog")}, true));
		Assert.assertEquals(1, group.size());
	}

	@Test
	public void testTermSerialization() {
        String[] words = {"aap", "~", "~~", ""};
        String[] expected = {"aap", "~~", "~~~", ""};
        MockTerms mockTerms = new MockTerms(words);
        for (int i = 0; i < mockTerms.numberOfTerms(); i++) {
            Assert.assertEquals(expected[i], mockTerms.serializeTerm(i));
            Assert.assertEquals(expected[i], mockTerms.serializeTerm(mockTerms.deserializeToken(expected[i])));
        }
        Assert.assertEquals("~", mockTerms.serializeTerm(Terms.NO_TERM));
        Assert.assertEquals("~", mockTerms.serializeTerm(mockTerms.deserializeToken("~")));
	}

}
