package nl.inl.util;

import org.junit.Assert;
import org.junit.Test;

public class TestStringUtil {

    private static final char CHAR_COMBINING_ACCENT_ACUTE = '\u0301';

    @Test
    public void testRemoveAccents() {
        Assert.assertEquals("He, jij!", StringUtil.stripAccents("Hé, jij!"));
    }

    @Test
    public void testEscapeRegexCharacters() {
        Assert.assertEquals("\\^the\\*\\.quick\\?brown\\(fox\\)\\$", StringUtil.escapeRegexCharacters("^the*.quick?brown(fox)$"));
        String charsToEscape = "|\\?*+()[]-^${}";
        for (int i = 0; i < charsToEscape.length(); i++) {
            char c = charsToEscape.charAt(i);
            Assert.assertEquals("test\\" + c + "test", StringUtil.escapeRegexCharacters("test" + c + "test"));
        }
    }

    @Test
    public void testSanitizeAndNormalizeUnicode() {
        // Sanitize
        Assert.assertEquals("", StringUtil.sanitizeAndNormalizeUnicode("" + StringUtil.CHAR_ZERO_WIDTH_SPACE + StringUtil.CHAR_SOFT_HYPHEN));

        // Normalize
        Assert.assertEquals("é", StringUtil.sanitizeAndNormalizeUnicode("é"));
        Assert.assertEquals("é", StringUtil.sanitizeAndNormalizeUnicode("e" + CHAR_COMBINING_ACCENT_ACUTE));
    }

    @Test
    public void testNormalizeWhitespace() {
        Assert.assertEquals(" ", StringUtil.normalizeWhitespace("\n "));
        Assert.assertEquals(" ", StringUtil.normalizeWhitespace(" \n \n "));
        Assert.assertEquals(" ", StringUtil.normalizeWhitespace("" + StringUtil.CHAR_EM_SPACE + StringUtil.CHAR_NON_BREAKING_SPACE + ""));
    }

    @Test
    public void testTrimWhitespace() {
        Assert.assertEquals("trim", StringUtil.trimWhitespace("trim "));
        Assert.assertEquals("trim", StringUtil.trimWhitespace("trim" + StringUtil.CHAR_EM_SPACE + StringUtil.CHAR_NON_BREAKING_SPACE));
        Assert.assertEquals("trim", StringUtil.trimWhitespace("" + StringUtil.CHAR_EM_SPACE + StringUtil.CHAR_NON_BREAKING_SPACE + "trim"));
        Assert.assertEquals("tr  im", StringUtil.trimWhitespace(" tr  im "));
    }
}
