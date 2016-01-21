package nl.inl.blacklab.search;

import java.io.File;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import nl.inl.blacklab.example.DocIndexerExample;
import nl.inl.blacklab.index.IndexListenerDevNull;
import nl.inl.blacklab.index.Indexer;
import nl.inl.blacklab.queryParser.corpusql.CorpusQueryLanguageParser;
import nl.inl.blacklab.queryParser.corpusql.ParseException;
import nl.inl.util.StringUtil;

public class TestSearches {

	/**
	 * The BlackLab searcher object.
	 */
	static Searcher searcher;

	/**
	 * Expected search results;
	 */
	List<String> expected;

	/**
	 * Some test XML data to index.
	 */
	static String[] testData = {
		"<doc><s><entity><w l='the'   p='art' >The</w> "
		+ "<w l='quick' p='adj'>quick</w> "
		+ "<w l='brown' p='adj'>brown</w> "
		+ "<w l='fox'   p='nou'>fox</w></entity> "
		+ "<w l='jump'  p='vrb' >jumps</w> "
		+ "<w l='over'  p='pre' >over</w> "
		+ "<entity><w l='the'   p='art' >the</w> "
		+ "<w l='lazy'  p='adj'>lazy</w> "
		+ "<w l='dog'   p='nou'>dog</w></entity>" + ".</s></doc>",

		"<doc> <s><w l='may' p='vrb'>May</w> "
		+ "<entity><w l='the' p='art'>the</w> "
		+ "<w l='force' p='nou'>Force</w></entity> "
		+ "<w l='be' p='vrb'>be</w> "
		+ "<w l='with' p='pre'>with</w> "
		+ "<w l='you' p='pro'>you</w>" + ".</s></doc>",

		"<doc> <s><w l='to' p='pre'>To</w> "
		+ "<w l='find' p='vrb'>find</w> "
		+ "<w l='or' p='con'>or</w> "
		+ "<w l='be' p='adv'>not</w> "
		+ "<w l='to' p='pre'>to</w> "
		+ "<w l='find' p='vrb'>find</w>.</s>"
		+ "<s><w l='that' p='pro'>That</w> "
		+ "<w l='be' p='vrb'>is</w> "
		+ "<w l='the' p='art'>the</w> "
		+ "<w l='question' p='nou'>question</w>."
		+ "</s></doc>",
	};

	private static File indexDir;

	@BeforeClass
	public static void setUp() throws Exception {
		// Get a temporary directory for our test index
		indexDir = new File(System.getProperty("java.io.tmpdir"),
				"BlackLabExample");
		if (indexDir.exists()) {
			// Delete the old example dir
			// (NOTE: we also try to do this on exit but it may fail due to
			// memory mapping (on Windows))
			deleteTree(indexDir);
		}

		// Instantiate the BlackLab indexer, supplying our DocIndexer class
		Indexer indexer = new Indexer(indexDir, true, DocIndexerExample.class);
		indexer.setListener(new IndexListenerDevNull()); // no output
		try {
			// Index each of our test "documents".
			for (int i = 0; i < testData.length; i++) {
				indexer.index("test" + (i + 1), new StringReader(testData[i]));
			}
		} finally {
			// Finalize and close the index.
			indexer.close();
		}

		// Create the BlackLab searcher object
		searcher = Searcher.open(indexDir);
		searcher.setDefaultContextSize(1);
	}

	@AfterClass
	public static void tearDown() {
		if (searcher != null)
			searcher.close();
		deleteTree(indexDir);
	}

	private static void deleteTree(File dir) {
		for (File f: dir.listFiles()) {
			if (f.isFile())
				f.delete();
			else if (f.isDirectory())
				deleteTree(f);
		}
		dir.delete();
	}

	/**
	 * Parse a Corpus Query Language query
	 *
	 * @param query
	 *            the query to parse
	 * @return the resulting BlackLab text pattern
	 * @throws ParseException
	 */
	private List<String> find(String query) throws ParseException {
		// Parse query using the CorpusQL parser
		TextPattern tp = CorpusQueryLanguageParser.parse(query);

		// Execute the search
		Hits hits = searcher.find(tp);

		//hits.sort(new HitPropertyHitText(hits, "contents"));
		return getConcordances(hits);
	}

	/**
	 * Return a list of concordance strings.
	 *
	 * @param hits
	 *            the hits to display
	 * @return
	 */
	static List<String> getConcordances(Hits hits) {
		List<String> results = new ArrayList<>();
		for (Hit hit : hits) {
			Kwic kwic = hits.getKwic(hit);
			String left = StringUtil.join(kwic.getLeft("word"), " ");
			String match = StringUtil.join(kwic.getMatch("word"), " ");
			String right = StringUtil.join(kwic.getRight("word"), " ");
			String conc = left + " [" + match + "] " + right;
			results.add(conc.trim());
		}
		return results;
	}

	@Test
	public void testSearches() throws ParseException {
		testSimple();
		testSequences();
		testMatchAll();
		testOptional();
		testRepetition();
		testStringRegexes();
		testTags();
	}

	@Test
	public void testSimple() throws ParseException {
		expected = Arrays.asList(
				"[The] quick",
				"over [the] lazy",
				"May [the] Force",
				"is [the] question");
		Assert.assertEquals(expected, find(" 'the' "));

		expected = Arrays.asList(
				"over [the] lazy",
				"May [the] Force",
				"is [the] question");
		Assert.assertEquals(expected, find(" '(?-i)the' "));

		expected = Arrays.asList(
				"brown [fox] jumps",
				"lazy [dog]",
				"the [Force] be",
				"the [question]");
		Assert.assertEquals(expected, find(" [pos='nou'] "));
	}

	@Test
	public void testSequences() throws ParseException {
		expected = Arrays.asList(
				"quick [brown fox] jumps",
				"the [lazy dog]");
		Assert.assertEquals(expected, find(" [pos='adj'] [pos='nou'] "));
	}

	@Test
	public void testMatchAll() throws ParseException {
		expected = Arrays.asList(
				"brown [fox jumps] over",
				"the [Force be] with");
		Assert.assertEquals(expected, find(" [pos='nou'] [] "));

		expected = Arrays.asList(
				"quick [brown fox] jumps",
				"the [lazy dog]",
				"May [the Force] be",
				"is [the question]");
		Assert.assertEquals(expected, find(" [] [pos='nou'] "));
	}

	@Test
	public void testOptional() throws ParseException {
		expected = Arrays.asList(
				"be [with you]",
				"with [you]",
				"to [find That] is",
				"find [That] is"
				);
		Assert.assertEquals(expected, find(" []? [pos='pro'] "));

		expected = Arrays.asList(
				"with [you]",
				"find [That] is",
				"find [That is] the"
				);
		Assert.assertEquals(expected, find(" [pos='pro'] []? "));

		expected = Arrays.asList(
				"be [with] you",
				"be [with you]",
				"with [you]",
				"To [find] or",
				"to [find] That",
				"to [find That] is",
				"find [That] is"
				);
		Assert.assertEquals(expected, find(" 'with|find'? [pos='pro']? "));
	}

	@Test
	public void testRepetition() throws ParseException {
		expected = Arrays.asList(
				"The [quick brown] fox");
		Assert.assertEquals(expected, find(" [pos='adj']{2} "));

		expected = Arrays.asList(
				"The [quick] brown",
				"The [quick brown] fox",
				"quick [brown] fox",
				"the [lazy] dog");
		Assert.assertEquals(expected, find(" [pos='adj']{1,} "));
	}

	@Test
	public void testStringRegexes() throws ParseException {
		expected = Arrays.asList(
				"quick [brown] fox",
				"Force [be] with");
		Assert.assertEquals(expected, find(" 'b.*' "));

		expected = Arrays.asList(
				"brown [fox] jumps",
				"the [Force] be");
		Assert.assertEquals(expected, find(" 'fo[xr].*' "));
	}

	@Test
	public void testUniq() throws ParseException {
		expected = Arrays.asList(
				"fox [jumps] over");
		Assert.assertEquals(expected, find("[word = 'jumps' | lemma = 'jump']"));
	}

	@Test
	public void testOr() throws ParseException {
		expected = Arrays.asList(
				"fox [jumps] over",
				"jumps [over] the");
		Assert.assertEquals(expected, find("[word = 'jumps' | lemma = 'over']"));
	}

	@Test
	@Ignore
	public void testAnd() throws ParseException {
		expected = Arrays.asList(
				"The [quick] brown");
		Assert.assertEquals(expected, find("[pos = 'adj' & lemma = '.*u.*']"));
	}

	@Test
	public void testTags() throws ParseException {
		expected = Arrays.asList(
				"quick [brown] fox");
		Assert.assertEquals(expected, find(" 'b.*' within <entity/> "));

		expected = Arrays.asList(
				"[The quick brown fox] jumps");
		Assert.assertEquals(expected, find(" <entity/> containing 'b.*' "));

		expected = Arrays.asList(
				"[The] quick");
		Assert.assertEquals(expected, find(" <s> 'the' "));

		expected = Arrays.asList(
				"lazy [dog]");
		Assert.assertEquals(expected, find(" 'dog' </s> "));
	}

}
