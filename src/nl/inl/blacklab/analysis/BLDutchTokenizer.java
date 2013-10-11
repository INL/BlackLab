package nl.inl.blacklab.analysis;

import java.io.Reader;

import org.apache.lucene.analysis.CharTokenizer;
import org.apache.lucene.util.AttributeSource;
import org.apache.lucene.util.Version;

/**
 * A simple tokenizer for Dutch texts. Basically the whitespace tokenizer with
 * a few exceptional punctuation characters that are included in tokens.
 *
 * These are the exceptions:
 * * apostrophes (e.g. zo'n, da's: apostrophes at the beginning or end of a token will be filtered out later)
 * * dashes (e.g. ex-man, multi-)
 * * periods (e.g. a.u.b., N.B.; will be filtered out later)
 * * parens and brackets (e.g. bel(len), (pre)cursor; will be filtered out later)
 */
public class BLDutchTokenizer extends CharTokenizer {

	public BLDutchTokenizer(AttributeFactory factory, Reader input) {
		super(Version.LUCENE_36, factory, input);
	}

	public BLDutchTokenizer(AttributeSource source, Reader input) {
		super(Version.LUCENE_36, source, input);
	}

	public BLDutchTokenizer(Reader input) {
		super(Version.LUCENE_36, input);
	}

	@Override
	protected boolean isTokenChar(int c) {

		if (Character.isWhitespace(c))
			return false;

		if (Character.isLetter(c))
			return true;

		if (c == '\'' || c == '-' || c == '.' || c == '(' || c == '[' || c == ')' || c == ']') {
			/* These are exceptions to the rule that non-letters are not token chars.
			 *
			 * An example of each:
			 * * zo'n, da's     (apostrophes at the beginning or end of a token will be filtered out later)
			 * * ex-man, multi-
			 * * a.u.b., N.B.   (periods will be filtered out later)
			 * * bel(len),      (pre)cursor (parens and brackets will be filtered out later)
			 */
			return true;
		}

		// Some non-exceptional punctuation character. Not part of the token.
		return false;
	}

}
