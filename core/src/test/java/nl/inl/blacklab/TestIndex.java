package nl.inl.blacklab;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.search.Query;

import nl.inl.blacklab.index.DocumentFormats;
import nl.inl.blacklab.index.IndexListenerDevNull;
import nl.inl.blacklab.index.Indexer;
import nl.inl.blacklab.queryParser.corpusql.CorpusQueryLanguageParser;
import nl.inl.blacklab.queryParser.corpusql.ParseException;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.ConfigReader;
import nl.inl.blacklab.search.Kwic;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.results.Hit;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.HitsSettings;
import nl.inl.blacklab.search.results.Kwics;
import nl.inl.blacklab.testutil.DocIndexerExample;
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

            "<doc> <w l='noot'>noot</w> "
                    + "<w l='mier'>mier</w> "
                    + "<w l='aap'>aap</w> "
                    + "<w l='mier'>mier</w> "
                    + "<w l='mier'>mier</w> "
                    + "<w l='mier'>mier</w> "
                    + "<w l='noot'>noot</w> "
                    + "<w l='noot'>noot</w> "
                    + "<w l='aap'>aap</w> "
                    + "<w l='aap'>aap</w> "
                    + "<w l='aap'>aap</w> "
                    + "<w l='aap'>aap</w> "
                    + "</doc>",
    };

    final static String testFormat = "testformat";

    static {
        // Ensure repeatable tests. Indexer opens Searcher, which in turn
        // will look for blacklab.yaml in several directories. We want our
        // tests to be independent of the local filesystem, so skip this step
        // for any tests using the TestIndex class.
        ConfigReader.setIgnoreConfigFile(true);
    }

    /**
     * The BlackLab searcher object.
     */
    BlackLabIndex searcher;

    private File indexDir;

    private Annotation word;

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
        DocumentFormats.registerFormat(testFormat, DocIndexerExample.class);
        Indexer indexer = Indexer.createNewIndex(indexDir, testFormat);
        indexer.setListener(new IndexListenerDevNull()); // no output
        try {
            // Index each of our test "documents".
            for (int i = 0; i < testData.length; i++) {
                indexer.index("test" + (i + 1), new ByteArrayInputStream(testData[i].getBytes()));
            }
        } finally {
            // Finalize and close the index.
            indexer.close();
        }

        // Create the BlackLab searcher object
        HitsSettings settings = HitsSettings.defaults().withContextSize(1);
        searcher = BlackLabIndex.open(indexDir, settings);
        word = searcher.mainAnnotatedField().annotations().get("word");
    }

    public BlackLabIndex getSearcher() {
        return searcher;
    }

    public void close() {
        if (searcher != null)
            searcher.close();
        deleteTree(indexDir);
    }

    private static void deleteTree(File dir) {
        for (File f : dir.listFiles()) {
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
     * @param query the query to parse
     * @return the resulting BlackLab text pattern
     * @throws ParseException
     */
    public List<String> findConc(String query) throws ParseException {
        Hits hits = find(query, null);
        return getConcordances(hits, word);
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
        return getConcordances(find(pattern, filter), word);
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
        return searcher.find(CorpusQueryLanguageParser.parse(pattern), searcher.mainAnnotatedField(), filter, null);
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
        return getConcordances(searcher.find(query, null), word);
    }

    /**
     * Return a list of concordance strings.
     *
     * @param hits the hits to display
     * @return the left, match and right values for the "word" annotation
     */
    static List<String> getConcordances(Hits hits, Annotation word) {
        List<String> results = new ArrayList<>();
        Kwics kwics = hits.kwics(-1);
        for (Hit hit : hits) {
            Kwic kwic = kwics.get(hit);
            String left = StringUtil.join(kwic.getLeft(word), " ");
            String match = StringUtil.join(kwic.getMatch(word), " ");
            String right = StringUtil.join(kwic.getRight(word), " ");
            String conc = left + " [" + match + "] " + right;
            results.add(conc.trim());
        }
        return results;
    }

}
