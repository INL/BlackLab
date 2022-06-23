package nl.inl.blacklab.forwardindex;

import java.text.Collator;
import java.util.Locale;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import nl.inl.blacklab.forwardindex.AnnotationForwardIndex.CollatorVersion;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

public class TestCollators {

    private static final char CHAR_SOFT_HYPHEN = '\u00ad';

    private static final char CHAR_GENERAL_PUNCTUATION = '\u2003';

    private static Collator getDefaultEnglishCollator() {
        return Collator.getInstance(new Locale("en", "GB"));
    }

    private static Collator getBlackLabCollator() {
        Collator coll = getDefaultEnglishCollator();
        //Collator coll = Collator.getInstance(new Locale("en", "GB"));
        Collators colls = new Collators(coll, CollatorVersion.V2);
        return colls.get(MatchSensitivity.INSENSITIVE);
    }

    @Before
    public void setUp() {
    }

    private void testInsensitiveCollatorIgnoresChars(char... chars) {
        for (char c: chars) {
            Assert.assertEquals("insensitive collator ignores char " + ((int)c), 0, getBlackLabCollator().compare(c + "te" + c + "st" + c, "t" + c + "est"));
            Assert.assertEquals("desensitize() removes char " + ((int)c), MatchSensitivity.INSENSITIVE.desensitize(c + "te" + c + "st" + c), "test");
        }
    }

    private void testInsensitiveCollatorComparesChar(char... chars) {
        for (char c: chars) {
            Assert.assertEquals("insensitive collator compares char " + c, 0,
                    getBlackLabCollator().compare(c + "te" + c + "st" + c, c + "te" + c + "st" + c));
            Assert.assertEquals("desensitize() doesn't remove char " + c,
                    MatchSensitivity.INSENSITIVE.desensitize(c + "te" + c + "st" + c), c + "te" + c + "st" + c);
            Assert.assertNotEquals("desensitize() doesn't remove char " + c,
                    MatchSensitivity.INSENSITIVE.desensitize("te" + c + "st"), "t" + c + "est");
            Assert.assertNotEquals("desensitize() doesn't remove char 2 " + c,
                    MatchSensitivity.INSENSITIVE.desensitize("te" + c + "st"), "t" + c + "est");
        }
    }

    @Test
    public void testInsensitiveCompare() {
        testInsensitiveCollatorIgnoresChars(CHAR_SOFT_HYPHEN, CHAR_GENERAL_PUNCTUATION);
        testInsensitiveCollatorComparesChar(' ', '\t', '\n', '\r');
    }

}
