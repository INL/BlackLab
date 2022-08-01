package nl.inl.util;

import org.junit.Assert;
import org.junit.Test;

public class TestStringUtil {

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
}
