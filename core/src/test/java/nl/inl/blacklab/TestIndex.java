package nl.inl.blacklab;

import java.io.File;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.search.Query;

import nl.inl.blacklab.index.IndexListenerDevNull;
import nl.inl.blacklab.index.Indexer;
import nl.inl.blacklab.queryParser.corpusql.CorpusQueryLanguageParser;
import nl.inl.blacklab.queryParser.corpusql.ParseException;
import nl.inl.blacklab.search.Hit;
import nl.inl.blacklab.search.Hits;
import nl.inl.blacklab.search.Kwic;
import nl.inl.blacklab.search.Searcher;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.tools.indexexample.DocIndexerExample;
import nl.inl.util.StringUtil;

public class TestIndex {
	/**
	 * Some test XML data to index.
	 */
	final static String[] testData = {
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

	/**
	 * The BlackLab searcher object.
	 */
	Searcher searcher;

	private File indexDir;

	public TestIndex() throws Exception {
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
		searcher.hitsSettings().setContextSize(1);
	}

	public Searcher getSearcher() {
		return searcher;
	}

	public void close() {
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
	 * Find concordances from a Corpus Query Language query.
	 *
	 * @param query
	 *            the query to parse
	 * @return the resulting BlackLab text pattern
	 * @throws ParseException
	 */
	public List<String> findConc(String query) throws ParseException {
		Hits hits = find(query, null);
		return getConcordances(hits);
	}

	/**
	 * Find concordances from a Corpus Query Language query.
	 *
	 * @param pattern CorpusQL pattern to find
	 * @param filter how to filter the query
	 * @return the resulting BlackLab text pattern
	 * @throws ParseException
	 */
	public List<String> findConc(String pattern, Query filter) throws ParseException {
		return getConcordances(find(pattern, filter));
	}

	/**
	 * Find hits from a Corpus Query Language query.
	 *
	 * @param pattern CorpusQL pattern to find
	 * @param filter how to filter the query
	 * @return the resulting BlackLab text pattern
	 * @throws ParseException
	 */
	public Hits find(String pattern, Query filter) throws ParseException {
		return searcher.find(CorpusQueryLanguageParser.parse(pattern), filter);
	}

	/**
	 * Find hits from a Corpus Query Language query.
	 *
	 * @param pattern CorpusQL pattern to find
	 * @return the resulting BlackLab text pattern
	 * @throws ParseException
	 */
	public Hits find(String pattern) throws ParseException {
		return find(pattern, null);
	}

	/**
	 * Find hits from a Corpus Query Language query.
	 *
	 * @param query what to find
	 * @return the resulting BlackLab text pattern
	 * @throws ParseException
	 */
	public List<String> findConc(BLSpanQuery query) throws ParseException {
		return getConcordances(searcher.find(query));
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


}
