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

import java.io.StringReader;

import org.junit.Assert;
import org.junit.Test;

import nl.inl.blacklab.queryParser.corpusql.CorpusQueryLanguageParser;
import nl.inl.blacklab.queryParser.corpusql.ParseException;

public class TestTextPatternRewrite {

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

	static void assertNoRewrite(String cql, String result) {
		assertRewrite(cql, result, result);
	}

	static void assertRewrite(String cql, String before, String after) {
		TextPattern original = getPatternFromCql(cql);
		Assert.assertEquals(before, original.toString());
		TextPattern rewritten = original.rewrite();
		Assert.assertEquals(after, rewritten.toString());
	}

	static void assertRewriteResult(String cql, String after) {
		TextPattern original = getPatternFromCql(cql);
		TextPattern rewritten = original.rewrite();
		Assert.assertEquals(after, rewritten.toString());
	}

	@Test
	public void testRewrite() {
		assertRewrite("[!(word != 'Water')]",
			"NOT(NOT(PROP(word, REGEX(^Water$))))",
			"PROP(word, TERM(Water))");
	}

	@Test
	public void testRewriteInsensitive() {
		assertRewrite("[word = '(?i)Appel']",
			"PROP(word, REGEX(^(?i)Appel$))",
			"PROP(word, SENSITIVE(i, TERM(Appel)))");
	}

	@Test
	public void testRewriteInsensitive2() {
		assertRewrite("[word = '(?i)Appel.*']",
				"PROP(word, REGEX(^(?i)Appel.*$))",
				"PROP(word, SENSITIVE(i, PREFIX(Appel)))");
	}

	@Test
	public void testRewriteInsensitive3() {
		assertRewrite("[word = '(?i).*Appel']",
				"PROP(word, REGEX(^(?i).*Appel$))",
				"PROP(word, SENSITIVE(i, WILDCARD(*Appel)))");
	}

	@Test
	public void testRewriteInsensitive4() {
		assertRewrite("[word = '(?i)[ao]ppel']",
				"PROP(word, REGEX(^(?i)[ao]ppel$))",
				"PROP(word, SENSITIVE(i, REGEX(^[ao]ppel$)))");
	}

	@Test
	public void testRewriteSensitive() {
		assertRewrite("[word = '(?-i)Bla']",
				"PROP(word, REGEX(^(?-i)Bla$))",
				"PROP(word, SENSITIVE(s, TERM(Bla)))");

		assertRewrite("[word = '(?c)Bla']",
				"PROP(word, REGEX(^(?c)Bla$))",
				"PROP(word, SENSITIVE(s, TERM(Bla)))");
	}

	@Test
	public void testRewriteNestedAnd() {
		assertRewrite("[word = 'a' & lemma = 'b' & pos != 'c']",
				"AND(PROP(word, REGEX(^a$)), AND(PROP(lemma, REGEX(^b$)), NOT(PROP(pos, REGEX(^c$)))))",
				"ANDNOT([PROP(word, TERM(a)), PROP(lemma, TERM(b))], [PROP(pos, TERM(c))])");
	}

	@Test
	public void testRewriteNestedOr() {
		assertRewrite("[word = 'a' | word = 'b' | word = 'c']",
				"OR(PROP(word, REGEX(^a$)), OR(PROP(word, REGEX(^b$)), PROP(word, REGEX(^c$))))",
				"OR(PROP(word, TERM(a)), PROP(word, TERM(b)), PROP(word, TERM(c)))");
	}

	@Test
	public void testRewriteNegativeAnd() {
		assertRewrite("[word != 'a' & word != 'b']",
				"AND(NOT(PROP(word, REGEX(^a$))), NOT(PROP(word, REGEX(^b$))))",
				"NOT(OR(PROP(word, TERM(a)), PROP(word, TERM(b))))");
	}

	@Test
	public void testRewriteNegativeOr() {
		assertRewrite("[word != 'a' | lemma != 'b']",
				"OR(NOT(PROP(word, REGEX(^a$))), NOT(PROP(lemma, REGEX(^b$))))",
				"NOT(AND(PROP(word, TERM(a)), PROP(lemma, TERM(b))))");
	}

	@Test
	public void testRewriteAndNot() {
		assertRewrite("[word = 'a' & lemma != 'b']",
				"AND(PROP(word, REGEX(^a$)), NOT(PROP(lemma, REGEX(^b$))))",
				"ANDNOT([PROP(word, TERM(a))], [PROP(lemma, TERM(b))])");
	}

	@Test
	public void testRewriteNotAndNot() {
		assertRewrite("[ !(word = 'a' & lemma != 'b') ]",
				"NOT(AND(PROP(word, REGEX(^a$)), NOT(PROP(lemma, REGEX(^b$)))))",
				"OR(NOT(PROP(word, TERM(a))), PROP(lemma, TERM(b)))");
	}

	@Test
	public void testRewriteRepetitionWord() {
		assertRewrite("'a' 'a'",
				"SEQ(REGEX(^a$), REGEX(^a$))",
				"REP(TERM(a), 2, 2)");
		assertRewrite("'a.*' 'a.*'",
				"SEQ(REGEX(^a.*$), REGEX(^a.*$))",
				"REP(PREFIX(a), 2, 2)");
	}

	@Test
	public void testRewriteRepetitionLemma() {
		assertRewrite("[lemma='a'] [lemma='a']",
				"SEQ(PROP(lemma, REGEX(^a$)), PROP(lemma, REGEX(^a$)))",
				"REP(PROP(lemma, TERM(a)), 2, 2)");
		assertRewrite("[lemma='a'] [lemma='b']",
				"SEQ(PROP(lemma, REGEX(^a$)), PROP(lemma, REGEX(^b$)))",
				"SEQ(PROP(lemma, TERM(a)), PROP(lemma, TERM(b)))");
		assertRewrite("[lemma='a'] [word='a']",
				"SEQ(PROP(lemma, REGEX(^a$)), PROP(word, REGEX(^a$)))",
				"SEQ(PROP(lemma, TERM(a)), PROP(word, TERM(a)))");
	}

	@Test
	public void testRewriteRepetitionTags() {
		assertRewrite("<s test='1' /> <s test='1' />",
				"SEQ(TAGS(s, test=1), TAGS(s, test=1))",
				"REP(TAGS(s, test=1), 2, 2)");

		assertNoRewrite("<s test='1' /> <t test='1' />", "SEQ(TAGS(s, test=1), TAGS(t, test=1))");
		assertNoRewrite("<s test='1' /> <s test='2' />", "SEQ(TAGS(s, test=1), TAGS(s, test=2))");
	}

	@Test
	public void testRewriteRepetitionAndOr() {
		assertRewriteResult("('a'|'b') ('a'|'b')",
				"REP(OR(TERM(a), TERM(b)), 2, 2)");
		assertRewriteResult("('a'|'b') ('a'|'c')",
				"SEQ(OR(TERM(a), TERM(b)), OR(TERM(a), TERM(c)))");

		assertRewriteResult("('a'&'b') ('a'&'b')",
				"REP(AND(TERM(a), TERM(b)), 2, 2)");
		assertRewriteResult("('a'&'b') ('a'&'c')",
				"SEQ(AND(TERM(a), TERM(b)), AND(TERM(a), TERM(c)))");

		assertRewriteResult("('a'& [word != 'b']) ('a'& [word != 'b'])",
				"REP(ANDNOT([TERM(a)], [PROP(word, TERM(b))]), 2, 2)");
		assertRewriteResult("('a'& [word != 'b']) ('a'& [word != 'c'])",
				"SEQ(ANDNOT([TERM(a)], [PROP(word, TERM(b))]), ANDNOT([TERM(a)], [PROP(word, TERM(c))]))");
	}

	@Test
	public void testRewriteAny() {
		assertRewriteResult("[]{0,1}", "ANYTOKEN(0, 1)");
		assertRewriteResult("[]{2,3}", "ANYTOKEN(2, 3)");
		assertRewriteResult("[]{2,}", "ANYTOKEN(2, -1)");
	}

	@Test
	public void testRewriteRepetitionAny() {
		assertRewriteResult("'a' []{2,3}", "EXPAND(TERM(a), false, 2, 3)");
		assertRewriteResult("'a' ([]){2,3}", "EXPAND(TERM(a), false, 2, 3)");
		assertRewriteResult("'a' ([]{2}){3}", "EXPAND(TERM(a), false, 6, 6)");
		assertRewriteResult("'a' []{1,2} []{3,4}", "EXPAND(TERM(a), false, 4, 6)");
	}

	@Test
	public void testRewriteSequenceExpand() {
		assertRewriteResult("'a' 'b' 'c' []{1,2}",
			"EXPAND(SEQ(TERM(a), TERM(b), TERM(c)), false, 1, 2)");
	}

	@Test
	public void testRewriteContaining() {
		assertRewriteResult("(<s/> containing 'a') (<s/> containing 'a')", "REP(POSFILTER(TAGS(s), TERM(a), CONTAINING), 2, 2)");
	}

	@Test
	public void testRewriteProblematicNegativeClauses() {
		assertRewriteResult("'b' [word != 'a']", "POSFILTER(EXPAND(TERM(b), false, 1, 1), PROP(word, TERM(a)), NOTCONTAINING, 1, 0)");
		assertRewriteResult("'b' [word != 'a']{2}", "POSFILTER(EXPAND(TERM(b), false, 2, 2), PROP(word, TERM(a)), NOTCONTAINING, 1, 0)");
		assertRewriteResult("'b' 'c' [word != 'a']{2}", "POSFILTER(SEQ(TERM(b), EXPAND(TERM(c), false, 2, 2)), PROP(word, TERM(a)), NOTCONTAINING, 2, 0)");
		assertRewriteResult("[word != 'a']{2} 'b' 'c'", "POSFILTER(SEQ(EXPAND(TERM(b), true, 2, 2), TERM(c)), PROP(word, TERM(a)), NOTCONTAINING, 0, -2)");
		assertRewriteResult("'a' [word != 'b']{1,20} 'c'", "POSFILTER(SEQ(EXPAND(TERM(a), false, 1, 20), TERM(c)), PROP(word, TERM(b)), NOTCONTAINING, 1, -1)");
		assertRewriteResult("[word != 'a']? 'b' [word != 'c']?", "OR(POSFILTER(POSFILTER(EXPAND(EXPAND(TERM(b), true, 1, 1), false, 1, 1), PROP(word, TERM(c)), NOTCONTAINING, 2, 0), PROP(word, TERM(a)), NOTCONTAINING, 0, -2), POSFILTER(EXPAND(TERM(b), false, 1, 1), PROP(word, TERM(c)), NOTCONTAINING, 1, 0), POSFILTER(EXPAND(TERM(b), true, 1, 1), PROP(word, TERM(a)), NOTCONTAINING, 0, -1), TERM(b))");
		assertRewriteResult("[word != 'a'] [pos='V.*']?", "OR(POSFILTER(EXPAND(PROP(pos, PREFIX(V)), true, 1, 1), PROP(word, TERM(a)), NOTCONTAINING, 0, -1), NOT(PROP(word, TERM(a))))");
		assertRewriteResult("[pos='V.*']? [word != 'a']", "OR(POSFILTER(EXPAND(PROP(pos, PREFIX(V)), false, 1, 1), PROP(word, TERM(a)), NOTCONTAINING, 1, 0), NOT(PROP(word, TERM(a))))");
	}

	@Test
	public void testRewriteRepetition() {
		assertRewriteResult("('a'*)* 'b'", "OR(SEQ(REP(TERM(a), 1, -1), TERM(b)), TERM(b))");
		assertRewriteResult("('a'+)* 'b'", "OR(SEQ(REP(TERM(a), 1, -1), TERM(b)), TERM(b))");
		assertRewriteResult("('a'*)+ 'b'", "OR(SEQ(REP(TERM(a), 1, -1), TERM(b)), TERM(b))");
		assertRewriteResult("('a'+)+", "REP(TERM(a), 1, -1)");
		assertRewriteResult("('a'?)? 'b'", "OR(SEQ(TERM(a), TERM(b)), TERM(b))");
		assertRewriteResult("('a'{2,3}){1,1}", "REP(TERM(a), 2, 3)");
		assertRewriteResult("('a'{1,1}){2,3}", "REP(TERM(a), 2, 3)");
		assertRewriteResult("'a'{1,1}", "TERM(a)");
		assertRewriteResult("'a'? 'b'?", "OR(SEQ(TERM(a), TERM(b)), TERM(b), TERM(a))");
		assertRewriteResult("'a' 'a'*", "REP(TERM(a), 1, -1)");
		assertRewriteResult("'a'? 'a'? 'b'", "OR(SEQ(REP(TERM(a), 1, 2), TERM(b)), TERM(b))");
		assertRewriteResult("'a'* 'a'", "REP(TERM(a), 1, -1)");
		assertRewriteResult("'a'* 'a'* 'b'", "OR(SEQ(REP(TERM(a), 1, -1), TERM(b)), TERM(b))");
		assertRewriteResult("'a' 'a'+", "REP(TERM(a), 2, -1)");
		assertRewriteResult("'a'+ 'a'", "REP(TERM(a), 2, -1)");
		assertRewriteResult("'a'+ 'a'+", "REP(TERM(a), 2, -1)");
	}

	@Test
	public void testRewriteTags() {
		assertRewriteResult("<s/> containing 'a' 'b'", "POSFILTER(TAGS(s), SEQ(TERM(a), TERM(b)), CONTAINING)");
		assertRewriteResult("<s> []* 'a' 'b' []* </s>", "POSFILTER(TAGS(s), SEQ(TERM(a), TERM(b)), CONTAINING)");
		assertRewriteResult("<s> 'a' 'b' []* </s>", "POSFILTER(TAGS(s), SEQ(TERM(a), TERM(b)), CONTAINING_AT_START)");
		assertRewriteResult("<s> []* 'a' 'b' </s>", "POSFILTER(TAGS(s), SEQ(TERM(a), TERM(b)), CONTAINING_AT_END)");
		assertRewriteResult("<s> 'a' 'b' </s>", "POSFILTER(TAGS(s), SEQ(TERM(a), TERM(b)), MATCHES)");
		assertRewriteResult("<s> ('a' 'b') 'c' </s>", "POSFILTER(TAGS(s), SEQ(TERM(a), TERM(b), TERM(c)), MATCHES)");
		assertRewriteResult("<s test='1'> 'a' </s>", "POSFILTER(TAGS(s, test=1), TERM(a), MATCHES)");
	}

	@Test
	public void testRewriteNGramFilter() {
		assertRewriteResult("[]{2,4} containing 'a' 'b'", "FILTERNGRAMS(SEQ(TERM(a), TERM(b)), CONTAINING, 2, 4)");
		assertRewriteResult("[]{1,2} within 'a' 'b' 'c'", "FILTERNGRAMS(SEQ(TERM(a), TERM(b), TERM(c)), WITHIN, 1, 2)");
	}

}
