package nl.inl.blacklab.search;

import java.text.Collator;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.lucene.index.LeafReaderContext;
import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.eclipse.collections.impl.set.mutable.primitive.IntHashSet;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import nl.inl.blacklab.forwardindex.AnnotationForwardIndex;
import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.index.annotated.AnnotationSensitivities;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.FieldType;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.indexmetadata.ValueListComplete;
import nl.inl.blacklab.testutil.TestIndex;

/**
 * Test the integrated index.
 */
public class TestIndexIntegrated {

    private static final int NUMBER_OF_TERMS = 26;

    private static TestIndex testIndex;

    private static BlackLabIndex index;

    private static AnnotatedField contents;

    private static Annotation word;

    private static AnnotationForwardIndex wordFi;

    private static AnnotationForwardIndex posFi;

    private static Terms wordTerms;

    @BeforeClass
    public static void setUp() {
        BlackLab.config().getFeatureFlags().put(BlackLab.FEATURE_INTEGRATE_EXTERNAL_FILES, "true");
        testIndex = TestIndex.get();
        index = testIndex.index();
        contents = index.mainAnnotatedField();
        word = contents.mainAnnotation();
        wordFi = index.forwardIndex(contents).get(word);
        posFi = index.forwardIndex(contents).get(contents.annotation("pos"));
        wordTerms = wordFi.terms();
    }

    @AfterClass
    public static void tearDown() {
        if (testIndex != null)
            testIndex.close();
        BlackLab.config().getFeatureFlags().put(BlackLab.FEATURE_INTEGRATE_EXTERNAL_FILES, "false");
    }

    @Test
    public void testSimple() {
        Assert.assertEquals("Number of terms", NUMBER_OF_TERMS, wordTerms.numberOfTerms());
    }

    @Test
    public void testIndexOfAndGet() {
        MutableIntSet s = new IntHashSet();
        for (int i = 0; i < NUMBER_OF_TERMS; i++) {
            Assert.assertEquals("indexOf(get(" + i + "))", i, wordTerms.indexOf(wordTerms.get(i)));
            s.clear();
            wordTerms.indexOf(s, wordTerms.get(i), MatchSensitivity.SENSITIVE);
            Assert.assertEquals("indexOf(get(" + i + "))", i, s.intIterator().next());
        }
    }

    /** Choose random terms and check that the comparison yields the expected value.
     */
    @Test
    public void testCompareTerms() {
        testCompareTerms(MatchSensitivity.SENSITIVE);
        testCompareTerms(MatchSensitivity.INSENSITIVE);
    }

    /**
     * Choose random terms and check that the comparison yields the expected value.
     * @param sensitive match sensitivity to use
     */
    private void testCompareTerms(MatchSensitivity sensitive) {
        Terms terms = wordFi.terms();
        Collator collator = wordFi.collators().get(sensitive);
        Random random = new Random(123_456);
        for (int i = 0; i < 100; i++) {
            int a = random.nextInt(NUMBER_OF_TERMS);
            int b = random.nextInt(NUMBER_OF_TERMS);
            String ta = terms.get(a);
            String tb = terms.get(b);
            int expected = collator.compare(ta, tb);
            int actual = terms.compareSortPosition(a, b, sensitive);
            Assert.assertEquals(
                    ta + "(" + a + ") <=> " + tb + "(" + b + ")",
                    expected,
                    actual
            );
        }
    }

    @Test
    public void testDocLength() {
        for (int i = 0; i < TestIndex.DOC_LENGTHS_TOKENS.length; i++) {
            int expectedLength = TestIndex.DOC_LENGTHS_TOKENS[i] + BlackLabIndexAbstract.IGNORE_EXTRA_CLOSING_TOKEN;
            int docId = testIndex.getDocIdForDocNumber(i);

            Assert.assertEquals(expectedLength, wordFi.docLength(docId));

            // pos annotation doesn't occur in all docs; test that this doesn't mess up doc length
            Assert.assertEquals(expectedLength, posFi.docLength(docId));
        }
    }

    int getToken(AnnotationForwardIndex afi, int docId, int pos) {
        List<int[]> parts = afi.retrievePartsInt(docId, new int[] { pos }, new int[] { pos + 1 });
        return parts.get(0)[0];
    }

    @Test
    public void testRetrieve() {
        System.err.flush();
        String[] expected = { "The", "quick", "brown", "fox", "jumps", "over", "the", "lazy", "dog" };
        int docId = testIndex.getDocIdForDocNumber(0);
        for (int i = 0; i < expected.length; i++) {
            Assert.assertEquals(expected[i], wordTerms.get(getToken(wordFi, docId, i)));
        }
    }

    /** if token offset out of range, throw an exception */
    @Test(expected = IllegalArgumentException.class)
    public void testRetrieveOutOfRange() {
        wordTerms.get(getToken(wordFi, 0, TestIndex.DOC_LENGTHS_TOKENS[0]));
    }

    /** translating a -1 term from segment to global should also return -1 */
    @Test
    public void testNoTerm() {
        LeafReaderContext lrc = index.reader().leaves().iterator().next();
        Assert.assertEquals(-1, wordTerms.segmentIdsToGlobalIds(lrc.ord, new int[] {-1})[0]);
    }

    @Test
    public void testMetadataCounts() {
        int expectedTokenCount = Arrays.stream(TestIndex.DOC_LENGTHS_TOKENS).sum();
        Assert.assertEquals(expectedTokenCount, index.metadata().tokenCount());
        Assert.assertEquals(TestIndex.DOC_LENGTHS_TOKENS.length, index.metadata().documentCount());
    }

    @Test
    public void testMetadataMetadataField() {
        MetadataField field = index.metadata().metadataFields().get("pid");
        Assert.assertEquals(FieldType.TOKENIZED, field.type());
        Assert.assertEquals(ValueListComplete.YES, field.isValueListComplete());
        Map<String, Integer> map = field.valueDistribution();
        int expectedNumberOfDocuments = TestIndex.DOC_LENGTHS_TOKENS.length;
        Assert.assertEquals(expectedNumberOfDocuments, map.size());
        for (int i = 0; i < expectedNumberOfDocuments; i++)
            Assert.assertEquals(1, (int)map.get(Integer.toString(i)));
        Assert.assertEquals(TestIndex.DOC_LENGTHS_TOKENS.length, index.metadata().documentCount());
    }

    @Test
    public void testMetadataAnnotatedField() {
        AnnotatedField field = index.metadata().annotatedFields().get("contents");
        Assert.assertEquals(true, field.hasXmlTags());
        Assert.assertEquals(true, field.hasContentStore());
        Set<String> expectedAnnotations = new HashSet<>(Arrays.asList("word", "lemma", "pos", "starttag", "punct"));
        Set<String> actualAnnotations = field.annotations().stream().map(Annotation::name).collect(Collectors.toSet());
        Assert.assertEquals(expectedAnnotations, actualAnnotations);
        Assert.assertEquals("word", field.mainAnnotation().name());
        Assert.assertEquals(AnnotationSensitivities.SENSITIVE_AND_INSENSITIVE, field.mainAnnotation().sensitivitySetting());
    }
}
