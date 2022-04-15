package nl.inl.blacklab.search.fimatch;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.queryParser.corpusql.CorpusQueryLanguageParser;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.QueryExplanation;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.optimize.ClauseCombinerNfa;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.testutil.TestIndex;

public class TestQueryRewriteNfa {

    static TestIndex testIndex;

    private static BlackLabIndex index;

    @BeforeClass
    public static void setUp() {
        testIndex = new TestIndex();
        index = testIndex.index();
        ClauseCombinerNfa.setNfaThreshold(ClauseCombinerNfa.MAX_NFA_MATCHING);
        ClauseCombinerNfa.setOnlyUseNfaForManyUniqueTerms(false);
    }

    @AfterClass
    public static void tearDown() {
        ClauseCombinerNfa.setOnlyUseNfaForManyUniqueTerms(true);
        ClauseCombinerNfa.setNfaThreshold(ClauseCombinerNfa.defaultForwardIndexMatchingThreshold);
        if (index != null)
            index.close();
        if (testIndex != null)
            testIndex.close();
    }

    static TextPattern getPatternFromCql(String cqlQuery) {
        cqlQuery = cqlQuery.replaceAll("'", "\""); // makes queries more readable in tests
        try {
            return CorpusQueryLanguageParser.parse(cqlQuery);
        } catch (InvalidQuery e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    void assertNoRewrite(String cql, String result) {
        assertRewrite(cql, result, result);
    }

    void assertRewrite(String cql, String before, String after) {
        try {
            BLSpanQuery q = getPatternFromCql(cql).toQuery(QueryInfo.create(index));
            QueryExplanation explanation = index.explain(q);
            if (before != null) {
                BLSpanQuery original = explanation.originalQuery();
                Assert.assertEquals(before, original.toString());
            }
            BLSpanQuery rewritten = explanation.rewrittenQuery();
            Assert.assertEquals(after, rewritten.toString());
        } catch (InvalidQuery e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    void assertRewriteResult(String cql, String after) {
        assertRewrite(cql, null, after);
    }

    @Test
    public void testRewrite() {
        assertRewriteResult("\"the\" [word=\"quick\" & lemma=\"quick\"] [lemma=\"brown\"]",
                "FISEQ(FISEQ(AND(TERM(contents%word@i:quick), TERM(contents%lemma@i:quick)), NFA:#1:TOKEN(brown,DANGLING), 1), NFA:#1:TOKEN(the,DANGLING), -1)");
    }

    @Ignore // hard to test properly with tiny indices
    @Test
    public void testRewriteSuffix() {
        assertRewriteResult("\"noot\" \".*p\"",
                "FISEQ(TERM(contents%word@i:noot), NFA:#1:REGEX(^.*p$,DANGLING), 1)");
    }

    @Test
    public void testRewritePrefix() {
        assertRewriteResult("\"a.*\" \"b.*\" \"c.*\"",
                "FISEQ(TERM(contents%word@i:aap), NFA:#1:REGEX(b.*,#2:REGEX(c.*,DANGLING)), 1)");
    }

}
