package nl.inl.blacklab.testutil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.DocumentFormatNotFound;
import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.exceptions.WildcardTermTooBroad;
import nl.inl.blacklab.index.DocumentFormats;
import nl.inl.blacklab.index.IndexListener;
import nl.inl.blacklab.index.Indexer;
import nl.inl.blacklab.mocks.DocIndexerExample;
import nl.inl.blacklab.queryParser.corpusql.CorpusQueryLanguageParser;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.Kwic;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.results.ContextSize;
import nl.inl.blacklab.search.results.Hit;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.Kwics;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.util.UtilsForTesting;

public class TestIndex {
    
    private static final class IndexListenerAbortOnError extends IndexListener {
        @Override
        public boolean errorOccurred(Throwable e, String path, File f) {
            // FileProcessor doesn't like when we re-throw the exception.
            //throw new BlackLabRuntimeException("Error in indexer, path=" + path + ", file=" + f, e);
            System.err.println("Error while indexing. path=" + path + ", file=" + f);
            e.printStackTrace();
            return false; // don't continue
        }
    }

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

    /**
     * The BlackLab index object.
     */
    BlackLabIndex index;

    private File indexDir;

    private Annotation word;

    public TestIndex() {
        this(false);
    }
    
    public TestIndex(boolean testDelete) {

        // Get a temporary directory for our test index
        indexDir = UtilsForTesting.createBlackLabTestDir("TestIndex");
        if (indexDir.exists()) {
            // Delete the old example dir
            // (NOTE: we also try to do this on exit but it may fail due to
            // memory mapping (on Windows))
            deleteTree(indexDir);
        }

        // Instantiate the BlackLab indexer, supplying our DocIndexer class
        DocumentFormats.registerFormat(testFormat, DocIndexerExample.class);
        try {
            Indexer indexer = Indexer.createNewIndex(indexDir, testFormat);
            indexer.setListener(new IndexListenerAbortOnError()); // throw on error
            try {
                // Index each of our test "documents".
                for (int i = 0; i < testData.length; i++) {
                    indexer.index("test" + (i + 1), testData[i].getBytes());
                }
                if (testDelete) {
                    // Delete the first doc, to test deletion.
                    // (close and re-open to be sure the document was written to disk first) 
                    indexer.close();
                    indexer = Indexer.openIndex(indexDir);
                    String luceneField = indexer.indexWriter().annotatedField("contents").annotation("word").sensitivity(MatchSensitivity.INSENSITIVE).luceneField();
                    indexer.indexWriter().delete(new TermQuery(new Term(luceneField, "dog")));
                }
            } finally {
                // Finalize and close the index.
                indexer.close();
            }

            // Create the BlackLab index object
            index = BlackLab.open(indexDir);
            word = index.mainAnnotatedField().annotation("word");
        } catch (DocumentFormatNotFound | ErrorOpeningIndex e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    public BlackLabIndex index() {
        return index;
    }

    public void close() {
        if (index != null)
            index.close();
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
     */
    public List<String> findConc(String query) {
        Hits hits = find(query, null);
        return getConcordances(hits, word);
    }

    /**
     * Find concordances from a Corpus Query Language query.
     *
     * @param query the query to parse
     * @param sortBy property to sort by
     * @return the resulting BlackLab text pattern
     */
    public List<String> findConc(String query, HitProperty sortBy) {
        Hits hits = find(query, null).sort(sortBy);
        return getConcordances(hits, word);
    }
    
    public List<String> findConc(String query, HitProperty prop, PropertyValue value) {
        Hits hits = find(query, null).filter(prop, value);
        return getConcordances(hits, word);
    }

    /**
     * Find concordances from a Corpus Query Language query.
     *
     * @param pattern CorpusQL pattern to find
     * @param filter how to filter the query
     * @return the resulting BlackLab text pattern
     */
    public List<String> findConc(String pattern, Query filter) {
        return getConcordances(find(pattern, filter), word);
    }

    /**
     * Find hits from a Corpus Query Language query.
     *
     * @param pattern CorpusQL pattern to find
     * @param filter how to filter the query
     * @return the resulting BlackLab text pattern
     */
    public Hits find(String pattern, Query filter) {
        try {
            return index.find(CorpusQueryLanguageParser.parse(pattern).toQuery(QueryInfo.create(index), filter), null);
        } catch (InvalidQuery e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    /**
     * Find hits from a Corpus Query Language query.
     *
     * @param pattern CorpusQL pattern to find
     * @return the resulting BlackLab text pattern
     */
    public Hits find(String pattern) {
        return find(pattern, null);
    }

    /**
     * Find hits from a Corpus Query Language query.
     *
     * @param query what to find
     * @return the resulting BlackLab text pattern
     */
    public List<String> findConc(BLSpanQuery query) {
        try {
            return getConcordances(index.find(query, null), word);
        } catch (WildcardTermTooBroad e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    /**
     * Return a list of concordance strings.
     *
     * @param hits the hits to display
     * @return the left, match and right values for the "word" annotation
     */
    static List<String> getConcordances(Hits hits, Annotation word) {
        List<String> results = new ArrayList<>();
        Kwics kwics = hits.kwics(ContextSize.get(1));
        for (Hit hit : hits) {
            Kwic kwic = kwics.get(hit);
            String left = StringUtils.join(kwic.left(word), " ");
            String match = StringUtils.join(kwic.match(word), " ");
            String right = StringUtils.join(kwic.right(word), " ");
            String conc = left + " [" + match + "] " + right;
            results.add(conc.trim());
        }
        return results;
    }

}
