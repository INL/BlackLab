package nl.inl.blacklab.analysis;

import org.apache.lucene.analysis.util.CharTokenizer;
import org.apache.lucene.util.AttributeFactory;

/**
 * A simple tokenizer for Dutch texts. Basically the whitespace tokenizer with a
 * few exceptional punctuation characters that are included in tokens.
 *
 * These are the exceptions: * apostrophes (e.g. zo'n, da's: apostrophes at the
 * beginning or end of a token will be filtered out later) * dashes (e.g.
 * ex-man, multi-) * periods (e.g. a.u.b., N.B.; will be filtered out later) *
 * parens and brackets (e.g. bel(len), (pre)cursor; will be filtered out later)
 */
public class BLDutchTokenizer extends CharTokenizer {

    public BLDutchTokenizer() {
        super();
    }

    public BLDutchTokenizer(AttributeFactory factory) {
        super(factory);
    }

    @Override
    protected boolean isTokenChar(int c) {

        if (Character.isWhitespace(c))
            return false;

        if (Character.isLetter(c) || Character.isDigit(c))
            return true;

        /* These are exceptions to the rule that non-letters are not token chars.
        *
        * An example of each:
        * 
        * <ul>
        * <li>zo'n, da's     (apostrophes at the beginning or end of a token will be filtered out later)
        * <li>ex-man, multi-
        * <li>a.u.b., N.B.   (periods will be filtered out later)
        * <li>bel(len),      (pre)cursor (parens and brackets will be filtered out later)
        * </ul>
        * 
        * Any other characters is some non-exceptional punctuation character, so not part of the token.
        */
        return c == '\'' || c == '-' || c == '.' || c == '(' || c == '[' || c == ')' || c == ']';
    }

}
