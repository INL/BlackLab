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
package nl.inl.blacklab.search;

import java.io.IOException;
import java.io.StringReader;

import org.apache.lucene.search.Query;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import nl.inl.blacklab.TestIndex;
import nl.inl.blacklab.queryParser.corpusql.CorpusQueryLanguageParser;
import nl.inl.blacklab.queryParser.corpusql.ParseException;
import nl.inl.blacklab.search.lucene.BLSpanQuery;

public class TestQueryRewrite {

	TestIndex testIndex;
	private Searcher searcher;

	@Before
	public void setUp() throws Exception {
		testIndex = new TestIndex();
		searcher = testIndex.getSearcher();
	}

	@After
	public void tearDown() {
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
		BLSpanQuery original = searcher.createSpanQuery(getPatternFromCql(cql), searcher.getMainContentsFieldName(), (Query)null);
		Assert.assertEquals(before, original.toString());
		try {
			BLSpanQuery rewritten = original.rewrite(searcher.getIndexReader());
			Assert.assertEquals(after, rewritten.toString());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	void assertRewriteResult(String cql, String after) {
		BLSpanQuery original = searcher.createSpanQuery(getPatternFromCql(cql), searcher.getMainContentsFieldName(), (Query)null);
		try {
			BLSpanQuery rewritten = original.rewrite(searcher.getIndexReader());
			Assert.assertEquals(after, rewritten.toString());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	public void testRewrite() {
		assertRewrite("[!(word != 'Water')]",
			"NOT(NOT(PROP(word, REGEX(^Water$))))",
			"PROP(word, TERM(contents%word@i:Water))");
	}

	@Test
	public void testRewriteInsensitive() {
		assertNoRewrite("[word = '(?i)Fox']", "TERM(contents%word@i:fox)");
	}

	@Test
	public void testRewriteInsensitive2() {
		assertRewrite("[word = '(?i)b.*']",
				"SPANWRAP(contents%word@i:b*)",
				"OR(TERM(contents%word@i:be), TERM(contents%word@i:brown))");
	}

	@Test
	public void testRewriteInsensitive3() {
		assertRewrite("[word = '(?i).*s']",
				"SPANWRAP(contents%word@i:*s)",
				"OR(TERM(contents%word@i:is), TERM(contents%word@i:jumps))");
	}

	@Test
	public void testRewriteInsensitive4() {
		assertRewrite("[word = '(?i)(th|b)e']",
				"SPANWRAP(contents%word@i:/(th|b)e/)",
				"OR(TERM(contents%word@i:be), TERM(contents%word@i:the))");
	}

	@Test
	public void testRewriteSensitive() {
		assertNoRewrite("[word = '(?-i)Bla']", "TERM(contents%word@s:Bla)");

		assertNoRewrite("[word = '(?c)Bla']", "TERM(contents%word@s:Bla)");
	}

	@Test
	public void testRewriteNestedAnd() {
		assertRewrite("[word = 'a' & lemma = 'b' & pos != 'c']",
				"AND(TERM(contents%word@i:a), AND(TERM(contents%lemma@i:b), NOT(TERM(contents%pos@i:c))))",
				"AND(TERM(contents%word@i:a), POSFILTER(TERM(contents%lemma@i:b), TERM(contents%pos@i:c), NOTMATCHES))");
		// @@@ Waarom niet rewritten naar:
		// POSFILTER(AND(TERM(contents%word@i:a), TERM(contents%lemma@i:b)), TERM(contents%pos@i:c), NOTMATCHES)
		// ? Is ws. efficienter omdat je minder hoeft te filteren.
	}

	@Test
	public void testRewriteNestedOr() {
		assertRewrite("[word = 'a' | word = 'b' | word = 'c']",
				"OR(PROP(word, REGEX(^a$)), OR(PROP(word, REGEX(^b$)), PROP(word, REGEX(^c$))))",
				"OR(PROP(word, TERM(contents%word@i:a)), PROP(word, TERM(contents%word@i:b)), PROP(word, TERM(contents%word@i:c)))");
	}

	@Test
	public void testRewriteNegativeAnd() {
		assertRewrite("[word != 'a' & word != 'b']",
				"AND(NOT(PROP(word, REGEX(^a$))), NOT(PROP(word, REGEX(^b$))))",
				"NOT(OR(PROP(word, TERM(contents%word@i:a)), PROP(word, TERM(contents%word@i:b))))");
	}

	@Test
	public void testRewriteNegativeOr() {
		assertRewrite("[word != 'a' | lemma != 'b']",
				"OR(NOT(PROP(word, REGEX(^a$))), NOT(PROP(lemma, REGEX(^b$))))",
				"NOT(AND(PROP(word, TERM(contents%word@i:a)), PROP(lemma, TERM(contents%word@i:b))))");
	}

	@Test
	public void testRewriteAndNot() {
		assertRewrite("[word = 'a' & lemma != 'b']",
				"AND(PROP(word, REGEX(^a$)), NOT(PROP(lemma, REGEX(^b$))))",
				"ANDNOT([PROP(word, TERM(contents%word@i:a))], [PROP(lemma, TERM(contents%word@i:b))])");
	}

	@Test
	public void testRewriteNotAndNot() {
		assertRewrite("[ !(word = 'a' & lemma != 'b') ]",
				"NOT(AND(TERM(contents%word@i:a), NOT(TERM(contents%lemma@i:b))))",
				"NOT(POSFILTER(TERM(contents%word@i:a), TERM(contents%lemma@i:b), NOTMATCHES))");
	}

	@Test
	public void testRewriteRepetitionWord() {
		assertRewrite("'the' 'the'",
				"SEQ(TERM(contents%word@i:the), TERM(contents%word@i:the))",
				"REP(TERM(contents%word@i:the), 2, 2)");
		assertRewrite("'the.*' 'the.*'",
				"SEQ(SPANWRAP(contents%word@i:the*), SPANWRAP(contents%word@i:the*))",
				"REP(OR(TERM(contents%word@i:the)), 2, 2)");
	}

	@Test
	public void testRewriteRepetitionLemma() {
		assertRewrite("[lemma='a'] [lemma='a']",
				"SEQ(PROP(lemma, REGEX(^a$)), PROP(lemma, REGEX(^a$)))",
				"REP(PROP(lemma, TERM(contents%word@i:a)), 2, 2)");
		assertRewrite("[lemma='a'] [lemma='b']",
				"SEQ(PROP(lemma, REGEX(^a$)), PROP(lemma, REGEX(^b$)))",
				"SEQ(PROP(lemma, TERM(contents%word@i:a)), PROP(lemma, TERM(contents%word@i:b)))");
		assertRewrite("[lemma='a'] [word='a']",
				"SEQ(PROP(lemma, REGEX(^a$)), PROP(word, REGEX(^a$)))",
				"SEQ(PROP(lemma, TERM(contents%word@i:a)), PROP(word, TERM(contents%word@i:a)))");
	}

	@Test
	public void testRewriteRepetitionTags() {
		assertRewrite("<s test='1' /> <s test='1' />",
				"SEQ(POSFILTER(TAGS(s), TERM(contents%starttag@s:@test__1), STARTS_AT), POSFILTER(TAGS(s), TERM(contents%starttag@s:@test__1), STARTS_AT))",
				"REP(POSFILTER(TAGS(s), TERM(contents%starttag@s:@test__1), STARTS_AT), 2, 2)");

		assertNoRewrite("<s test='1' /> <t test='1' />",
				"SEQ(POSFILTER(TAGS(s), TERM(contents%starttag@s:@test__1), STARTS_AT), POSFILTER(TAGS(t), TERM(contents%starttag@s:@test__1), STARTS_AT))");
		assertNoRewrite("<s test='1' /> <s test='2' />",
				"SEQ(POSFILTER(TAGS(s), TERM(contents%starttag@s:@test__1), STARTS_AT), POSFILTER(TAGS(s), TERM(contents%starttag@s:@test__2), STARTS_AT))");
	}

	@Test
	public void testRewriteRepetitionAndOr() {
		assertRewriteResult("('a'|'b') ('a'|'b')",
				"REP(OR(TERM(contents%word@i:a), TERM(contents%word@i:b)), 2, 2)");
		assertRewriteResult("('a'|'b') ('a'|'c')",
				"SEQ(OR(TERM(contents%word@i:a), TERM(contents%word@i:b)), OR(TERM(contents%word@i:a), TERM(contents%word@i:c)))");

		assertRewriteResult("('a'&'b') ('a'&'b')",
				"REP(AND(TERM(contents%word@i:a), TERM(contents%word@i:b)), 2, 2)");
		assertRewriteResult("('a'&'b') ('a'&'c')",
				"SEQ(AND(TERM(contents%word@i:a), TERM(contents%word@i:b)), AND(TERM(contents%word@i:a), TERM(contents%word@i:c)))");

		assertRewriteResult("('a'& [word != 'b']) ('a'& [word != 'b'])",
				"REP(ANDNOT([TERM(contents%word@i:a)], [PROP(word, TERM(contents%word@i:b))]), 2, 2)");
		assertRewriteResult("('a'& [word != 'b']) ('a'& [word != 'c'])",
				"SEQ(ANDNOT([TERM(contents%word@i:a)], [PROP(word, TERM(contents%word@i:b))]), ANDNOT([TERM(contents%word@i:a)], [PROP(word, TERM(contents%word@i:c))]))");
	}

	@Test
	public void testRewriteAny() {
		assertRewriteResult("[]{0,1}", "ANYTOKEN(0, 1)");
		assertRewriteResult("[]{2,3}", "ANYTOKEN(2, 3)");
		assertRewriteResult("[]{2,}", "ANYTOKEN(2, -1)");
	}

	@Test
	public void testRewriteRepetitionAny() {
		assertRewriteResult("'a' []{2,3}", "EXPAND(TERM(contents%word@i:a), false, 2, 3)");
		assertRewriteResult("'a' ([]){2,3}", "EXPAND(TERM(contents%word@i:a), false, 2, 3)");
		assertRewriteResult("'a' ([]{2}){3}", "EXPAND(TERM(contents%word@i:a), false, 6, 6)");
		assertRewriteResult("'a' []{1,2} []{3,4}", "EXPAND(TERM(contents%word@i:a), false, 4, 6)");
	}

	@Test
	public void testRewriteSequenceExpand() {
		assertRewriteResult("'a' 'b' 'c' []{1,2}",
			"EXPAND(SEQ(TERM(contents%word@i:a), TERM(contents%word@i:b), TERM(contents%word@i:c)), false, 1, 2)");
	}

	@Test
	public void testRewriteContaining() {
		assertRewriteResult("(<s/> containing 'a') (<s/> containing 'a')", "REP(POSFILTER(TAGS(s), TERM(contents%word@i:a), CONTAINING), 2, 2)");
	}

	@Test
	public void testRewriteProblematicNegativeClauses() {
		assertRewriteResult("'b' [word != 'a']", "POSFILTER(EXPAND(TERM(contents%word@i:b), false, 1, 1), PROP(word, TERM(contents%word@i:a)), NOTCONTAINING, 1, 0)");
		assertRewriteResult("'b' [word != 'a']{2}", "POSFILTER(EXPAND(TERM(contents%word@i:b), false, 2, 2), PROP(word, TERM(contents%word@i:a)), NOTCONTAINING, 1, 0)");
		assertRewriteResult("'b' 'c' [word != 'a']{2}", "POSFILTER(SEQ(TERM(contents%word@i:b), EXPAND(TERM(contents%word@i:c), false, 2, 2)), PROP(word, TERM(contents%word@i:a)), NOTCONTAINING, 2, 0)");
		assertRewriteResult("[word != 'a']{2} 'b' 'c'", "POSFILTER(SEQ(EXPAND(TERM(contents%word@i:b), true, 2, 2), TERM(contents%word@i:c)), PROP(word, TERM(contents%word@i:a)), NOTCONTAINING, 0, -2)");
		assertRewriteResult("'a' [word != 'b']{1,20} 'c'", "POSFILTER(SEQ(EXPAND(TERM(contents%word@i:a), false, 1, 20), TERM(contents%word@i:c)), PROP(word, TERM(contents%word@i:b)), NOTCONTAINING, 1, -1)");
		assertRewriteResult("[word != 'a']? 'b' [word != 'c']?", "OR(POSFILTER(POSFILTER(EXPAND(EXPAND(TERM(contents%word@i:b), true, 1, 1), false, 1, 1), PROP(word, TERM(contents%word@i:c)), NOTCONTAINING, 2, 0), PROP(word, TERM(contents%word@i:a)), NOTCONTAINING, 0, -2), POSFILTER(EXPAND(TERM(contents%word@i:b), false, 1, 1), PROP(word, TERM(contents%word@i:c)), NOTCONTAINING, 1, 0), POSFILTER(EXPAND(TERM(contents%word@i:b), true, 1, 1), PROP(word, TERM(contents%word@i:a)), NOTCONTAINING, 0, -1), TERM(contents%word@i:b))");
		assertRewriteResult("[word != 'a'] [pos='V.*']?", "OR(POSFILTER(EXPAND(PROP(pos, PREFIX(V)), true, 1, 1), PROP(word, TERM(contents%word@i:a)), NOTCONTAINING, 0, -1), NOT(PROP(word, TERM(contents%word@i:a))))");
		assertRewriteResult("[pos='V.*']? [word != 'a']", "OR(POSFILTER(EXPAND(PROP(pos, PREFIX(V)), false, 1, 1), PROP(word, TERM(contents%word@i:a)), NOTCONTAINING, 1, 0), NOT(PROP(word, TERM(contents%word@i:a))))");
	}

	@Test
	public void testRewriteRepetition() {
		assertRewriteResult("('a'*)* 'b'", "OR(SEQ(REP(TERM(contents%word@i:a), 1, -1), TERM(contents%word@i:b)), TERM(contents%word@i:b))");
		assertRewriteResult("('a'+)* 'b'", "OR(SEQ(REP(TERM(contents%word@i:a), 1, -1), TERM(contents%word@i:b)), TERM(contents%word@i:b))");
		assertRewriteResult("('a'*)+ 'b'", "OR(SEQ(REP(TERM(contents%word@i:a), 1, -1), TERM(contents%word@i:b)), TERM(contents%word@i:b))");
		assertRewriteResult("('a'+)+", "REP(TERM(contents%word@i:a), 1, -1)");
		assertRewriteResult("('a'?)? 'b'", "OR(SEQ(TERM(contents%word@i:a), TERM(contents%word@i:b)), TERM(contents%word@i:b))");
		assertRewriteResult("('a'{2,3}){1,1}", "REP(TERM(contents%word@i:a), 2, 3)");
		assertRewriteResult("('a'{1,1}){2,3}", "REP(TERM(contents%word@i:a), 2, 3)");
		assertRewriteResult("'a'{1,1}", "TERM(contents%word@i:a)");
		assertRewriteResult("'a'? 'b'?", "OR(SEQ(TERM(contents%word@i:a), TERM(contents%word@i:b)), TERM(contents%word@i:b), TERM(contents%word@i:a))");
		assertRewriteResult("'a' 'a'*", "REP(TERM(contents%word@i:a), 1, -1)");
		assertRewriteResult("'a'? 'a'? 'b'", "OR(SEQ(REP(TERM(contents%word@i:a), 1, 2), TERM(contents%word@i:b)), TERM(contents%word@i:b))");
		assertRewriteResult("'a'* 'a'", "REP(TERM(contents%word@i:a), 1, -1)");
		assertRewriteResult("'a'* 'a'* 'b'", "OR(SEQ(REP(TERM(contents%word@i:a), 1, -1), TERM(contents%word@i:b)), TERM(contents%word@i:b))");
		assertRewriteResult("'a' 'a'+", "REP(TERM(contents%word@i:a), 2, -1)");
		assertRewriteResult("'a'+ 'a'", "REP(TERM(contents%word@i:a), 2, -1)");
		assertRewriteResult("'a'+ 'a'+", "REP(TERM(contents%word@i:a), 2, -1)");
	}

	@Test
	public void testRewriteTags() {
		assertRewriteResult("<s/> containing 'a' 'b'", "POSFILTER(TAGS(s), SEQ(TERM(contents%word@i:a), TERM(contents%word@i:b)), CONTAINING)");
		assertRewriteResult("<s> []* 'a' 'b' []* </s>", "POSFILTER(TAGS(s), SEQ(TERM(contents%word@i:a), TERM(contents%word@i:b)), CONTAINING)");
		assertRewriteResult("<s> 'a' 'b' []* </s>", "POSFILTER(TAGS(s), SEQ(TERM(contents%word@i:a), TERM(contents%word@i:b)), CONTAINING_AT_START)");
		assertRewriteResult("<s> []* 'a' 'b' </s>", "POSFILTER(TAGS(s), SEQ(TERM(contents%word@i:a), TERM(contents%word@i:b)), CONTAINING_AT_END)");
		assertRewriteResult("<s> 'a' 'b' </s>", "POSFILTER(TAGS(s), SEQ(TERM(contents%word@i:a), TERM(contents%word@i:b)), MATCHES)");
		assertRewriteResult("<s> ('a' 'b') 'c' </s>", "POSFILTER(TAGS(s), SEQ(TERM(contents%word@i:a), TERM(contents%word@i:b), TERM(contents%word@i:c)), MATCHES)");
		assertRewriteResult("<s test='1'> 'a' </s>", "POSFILTER(TAGS(s, test=1), TERM(contents%word@i:a), MATCHES)");
	}

	@Test
	public void testRewriteNGramFilter() {
		assertRewriteResult("[]{2,4} containing 'a' 'b'", "FILTERNGRAMS(SEQ(TERM(contents%word@i:a), TERM(contents%word@i:b)), CONTAINING, 2, 4)");
		assertRewriteResult("[]{1,2} within 'a' 'b' 'c'", "FILTERNGRAMS(SEQ(TERM(contents%word@i:a), TERM(contents%word@i:b), TERM(contents%word@i:c)), WITHIN, 1, 2)");
	}

}
