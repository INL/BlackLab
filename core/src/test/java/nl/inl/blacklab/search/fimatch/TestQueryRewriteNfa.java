/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package nl.inl.blacklab.search.fimatch;

import java.io.StringReader;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import nl.inl.blacklab.TestIndex;
import nl.inl.blacklab.queryParser.corpusql.CorpusQueryLanguageParser;
import nl.inl.blacklab.queryParser.corpusql.ParseException;
import nl.inl.blacklab.search.QueryExplanation;
import nl.inl.blacklab.search.Searcher;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.optimize.ClauseCombinerNfa;
import nl.inl.blacklab.search.textpattern.TextPattern;

public class TestQueryRewriteNfa {

    static TestIndex testIndex;

    private static Searcher searcher;

    @BeforeClass
    public static void setUp() throws Exception {
        testIndex = new TestIndex();
        searcher = testIndex.getSearcher();
        ClauseCombinerNfa.setNfaThreshold(ClauseCombinerNfa.MAX_NFA_MATCHING);
    }

    @AfterClass
    public static void tearDown() {
        ClauseCombinerNfa.setNfaThreshold(ClauseCombinerNfa.DEFAULT_NFA_THRESHOLD);
        searcher.close();
        testIndex.close();
    }

    static TextPattern getPatternFromCql(String cqlQuery) {
        try {
            cqlQuery = cqlQuery.replaceAll("'", "\""); // makes queries more readable in tests
            CorpusQueryLanguageParser parser = new CorpusQueryLanguageParser(new StringReader(
                    cqlQuery));
            return parser.query();
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    void assertNoRewrite(String cql, String result) {
        assertRewrite(cql, result, result);
    }

    void assertRewrite(String cql, String before, String after) {
        QueryExplanation explanation = searcher.explain(getPatternFromCql(cql));
        if (before != null) {
            BLSpanQuery original = explanation.getOriginalQuery();
            Assert.assertEquals(before, original.toString());
        }
        BLSpanQuery rewritten = explanation.getRewrittenQuery();
        Assert.assertEquals(after, rewritten.toString());
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
