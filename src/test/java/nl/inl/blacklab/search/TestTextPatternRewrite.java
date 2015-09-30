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

	static TextPatternTranslatorString stringifier = new TextPatternTranslatorString();

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

	@Test
	public void testRewrite() {
		TextPattern original = getPatternFromCql("[!(word != 'Water')]");
		Assert.assertEquals("NOT(NOT(REGEX(contents%word@i, ^water$)))", original.translate(stringifier));
		TextPattern rewritten = original.rewrite();
		String rewrittenStr = rewritten.translate(stringifier);
		Assert.assertEquals("TERM(contents%word@i, water)", rewrittenStr);
	}

	@Test
	public void testRewriteInsensitive() {
		TextPattern original = getPatternFromCql("[word = '(?i)Appel']");
		Assert.assertEquals("REGEX(contents%word@i, ^(?i)appel$)", original.translate(stringifier));
		TextPattern rewritten = original.rewrite();
		String rewrittenStr = rewritten.translate(stringifier);
		Assert.assertEquals("TERM(contents%word@i, appel)", rewrittenStr);
	}

	@Test
	public void testRewriteInsensitive2() {
		TextPattern original = getPatternFromCql("[word = '(?i)Appel.*']");
		Assert.assertEquals("REGEX(contents%word@i, ^(?i)appel.*$)", original.translate(stringifier));
		TextPattern rewritten = original.rewrite();
		String rewrittenStr = rewritten.translate(stringifier);
		Assert.assertEquals("PREFIX(contents%word@i, appel)", rewrittenStr);
	}

	@Test
	public void testRewriteInsensitive3() {
		TextPattern original = getPatternFromCql("[word = '(?i).*Appel']");
		Assert.assertEquals("REGEX(contents%word@i, ^(?i).*appel$)", original.translate(stringifier));
		TextPattern rewritten = original.rewrite();
		String rewrittenStr = rewritten.translate(stringifier);
		Assert.assertEquals("WILDCARD(contents%word@i, *appel)", rewrittenStr);
	}

	@Test
	public void testRewriteInsensitive4() {
		TextPattern original = getPatternFromCql("[word = '(?i)[ao]ppel']");
		Assert.assertEquals("REGEX(contents%word@i, ^(?i)[ao]ppel$)", original.translate(stringifier));
		TextPattern rewritten = original.rewrite();
		String rewrittenStr = rewritten.translate(stringifier);
		Assert.assertEquals("REGEX(contents%word@i, ^[ao]ppel$)", rewrittenStr);
	}

	@Test
	public void testRewriteSensitive() {
		TextPattern original = getPatternFromCql("[word = '(?-i)Bla']");
		Assert.assertEquals("REGEX(contents%word@i, ^(?-i)bla$)", original.translate(stringifier));
		TextPattern rewritten = original.rewrite();
		String rewrittenStr = rewritten.translate(stringifier);
		Assert.assertEquals("TERM(contents%word@s, Bla)", rewrittenStr);

		original = getPatternFromCql("[word = '(?c)Bla']");
		Assert.assertEquals("REGEX(contents%word@i, ^(?c)bla$)", original.translate(stringifier));
		rewritten = original.rewrite();
		rewrittenStr = rewritten.translate(stringifier);
		Assert.assertEquals("TERM(contents%word@s, Bla)", rewrittenStr);
	}

	@Test
	public void testRewriteNestedAnd() {
		TextPattern original = getPatternFromCql("[word = 'a' & lemma = 'b' & pos != 'c']");
		Assert.assertEquals("AND(REGEX(contents%word@i, ^a$), AND(REGEX(contents%lemma@i, ^b$), NOT(REGEX(contents%pos@i, ^c$))))", original.translate(stringifier));
		TextPattern rewritten = original.rewrite();
		Assert.assertEquals("ANDNOT(AND(TERM(contents%word@i, a), TERM(contents%lemma@i, b)), TERM(contents%pos@i, c))", rewritten.translate(stringifier));
	}

	@Test
	public void testRewriteNestedOr() {
		TextPattern original = getPatternFromCql("[word = 'a' | word = 'b' | word = 'c']");
		Assert.assertEquals("OR(REGEX(contents%word@i, ^a$), OR(REGEX(contents%word@i, ^b$), REGEX(contents%word@i, ^c$)))", original.translate(stringifier));
		TextPattern rewritten = original.rewrite();
		Assert.assertEquals("OR(TERM(contents%word@i, a), TERM(contents%word@i, b), TERM(contents%word@i, c))", rewritten.translate(stringifier));
	}

	@Test
	public void testRewriteNegativeAnd() {
		TextPattern original = getPatternFromCql("[word != 'a' & word != 'b']");
		Assert.assertEquals("AND(NOT(REGEX(contents%word@i, ^a$)), NOT(REGEX(contents%word@i, ^b$)))", original.translate(stringifier));
		TextPattern rewritten = original.rewrite();
		Assert.assertEquals("NOT(OR(TERM(contents%word@i, a), TERM(contents%word@i, b)))", rewritten.translate(stringifier));
	}

	@Test
	public void testRewriteNegativeOr() {
		TextPattern original = getPatternFromCql("[word != 'a' | lemma != 'b']");
		Assert.assertEquals("OR(NOT(REGEX(contents%word@i, ^a$)), NOT(REGEX(contents%lemma@i, ^b$)))", original.translate(stringifier));
		TextPattern rewritten = original.rewrite();
		Assert.assertEquals("NOT(AND(TERM(contents%word@i, a), TERM(contents%lemma@i, b)))", rewritten.translate(stringifier));
	}

	@Test
	public void testRewriteAndNot() {
		TextPattern original = getPatternFromCql("[word = 'a' & lemma != 'b']");
		Assert.assertEquals("AND(REGEX(contents%word@i, ^a$), NOT(REGEX(contents%lemma@i, ^b$)))", original.translate(stringifier));
		TextPattern rewritten = original.rewrite();
		Assert.assertEquals("ANDNOT(TERM(contents%word@i, a), TERM(contents%lemma@i, b))", rewritten.translate(stringifier));
	}
	
	@Test
	public void testRewriteNotAndNot() {
		TextPattern original = getPatternFromCql("[ !(word = 'a' & lemma != 'b') ]");
		Assert.assertEquals("NOT(AND(REGEX(contents%word@i, ^a$), NOT(REGEX(contents%lemma@i, ^b$))))", original.translate(stringifier));
		TextPattern rewritten = original.rewrite();
		Assert.assertEquals("ANDNOT(TERM(contents%lemma@i, b), TERM(contents%word@i, a))", rewritten.translate(stringifier));
	}
}
