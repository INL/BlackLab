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

import junit.framework.Assert;
import nl.inl.blacklab.queryParser.corpusql.CorpusQueryLanguageParser;
import nl.inl.blacklab.queryParser.corpusql.ParseException;

import org.junit.Test;

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
}
