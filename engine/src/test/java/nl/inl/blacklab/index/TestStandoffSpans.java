package nl.inl.blacklab.index;

import java.io.File;
import java.io.IOException;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.DocumentFormatNotFound;
import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.BlackLabIndexWriter;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryTags;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.searches.SearchEmpty;
import nl.inl.util.UtilsForTesting;

public class TestStandoffSpans {

    public static final String TEST_FORMAT_NAME = "tei-standoff-spans";

    public UtilsForTesting.TestDir testDir;
    public BlackLabIndex testIndex;

    @BeforeClass
    public static void beforeClass() throws IOException {
        DocIndexerFactoryConfig factoryConfig = new DocIndexerFactoryConfig();
        File file = getFile("standoff/tei-standoff-spans.blf.yaml");
        factoryConfig.load(TEST_FORMAT_NAME, file).orElseThrow();
        DocumentFormats.registerFactory(factoryConfig);
    }

    @Before
    public void before() {
        testDir = UtilsForTesting.createBlackLabTestDir("TestStandoffSpans");
        // Instantiate the BlackLab indexer, supplying our DocIndexer class
        try {
            BlackLabIndexWriter indexWriter = BlackLab.openForWriting(testDir.file(), true,
                    TEST_FORMAT_NAME);
            Indexer indexer = Indexer.create(indexWriter);
            indexer.setListener(new IndexListener() {
                @Override
                public boolean errorOccurred(Throwable e, String path, File f) {
                    System.err.println("Error while indexing. path=" + path + ", file=" + f);
                    e.printStackTrace();
                    return false; // don't continue
                }
            });
            try {
                indexer.index(getFile("standoff/test.xml"));
            } finally {
                // Finalize and close the index.
                indexer.close();
            }

            // Create the BlackLab index object
            testIndex = BlackLab.open(testDir.file());
        } catch (DocumentFormatNotFound | ErrorOpeningIndex e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    private static File getFile(String path) {
        ClassLoader classLoader = TestStandoffSpans.class.getClassLoader();
        return new File(classLoader.getResource(path).getFile());
    }

    @Test
    public void testStandoffSpans() throws IOException, InvalidQuery {
        SearchEmpty s = testIndex.search();
        String fieldName = testIndex.mainAnnotatedField().tagsAnnotation().
                sensitivity(MatchSensitivity.SENSITIVE).luceneField();
        BLSpanQuery query = new SpanQueryTags(s.queryInfo(), fieldName,
                "character", null);
        Hits results = s.find(query).execute();
        Assert.assertEquals(2, results.size());
        Assert.assertEquals(0, results.get(0).start());
        Assert.assertEquals(2, results.get(0).end()); // FAILS, actually 3, but that's wrong
        Assert.assertEquals(3, results.get(1).start());
        Assert.assertEquals(5, results.get(1).end());
    }

    @After
    public void teardown() {
        testIndex.close();
        testDir.close();
    }
}
