package nl.inl.blacklab.search;

import org.junit.Assert;
import org.junit.Test;

import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.search.textpattern.TextPatternRegex;
import nl.inl.blacklab.search.textpattern.TextPatternTerm;

public class TestTextPatternRegex {

    @Test
    public void testEmptyPattern() {
        TextPattern r = new TextPatternRegex("");
        Assert.assertEquals("", ((TextPatternRegex) r).getValue());

        Assert.assertTrue(r instanceof TextPatternTerm);
        Assert.assertEquals("", ((TextPatternTerm) r).getValue());
    }

    @Test
    public void testBasicPattern() {
        TextPattern r = new TextPatternRegex("bla");
        Assert.assertEquals("bla", ((TextPatternRegex) r).getValue());
    }
}
