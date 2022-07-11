package nl.inl.blacklab.analysis;

import org.junit.Assert;
import org.junit.Test;

public class TestBuiltinAnalyzers {

    @Test
    public void testFromString() {
        Assert.assertEquals(BuiltinAnalyzers.DUTCH, BuiltinAnalyzers.fromString("default"));
        Assert.assertEquals(BuiltinAnalyzers.DUTCH, BuiltinAnalyzers.fromString("dutch"));
        Assert.assertEquals(BuiltinAnalyzers.DUTCH, BuiltinAnalyzers.fromString("Dutch"));
        Assert.assertEquals(BuiltinAnalyzers.STANDARD, BuiltinAnalyzers.fromString("STANDARD"));
        Assert.assertEquals(BuiltinAnalyzers.NONTOKENIZING, BuiltinAnalyzers.fromString("nontokenizing"));
        Assert.assertEquals(BuiltinAnalyzers.NONTOKENIZING, BuiltinAnalyzers.fromString("nontokenized"));
        Assert.assertEquals(BuiltinAnalyzers.NONTOKENIZING, BuiltinAnalyzers.fromString("untokenized"));
        Assert.assertEquals(BuiltinAnalyzers.WHITESPACE, BuiltinAnalyzers.fromString("whitespace"));
        Assert.assertEquals(BuiltinAnalyzers.UNKNOWN, BuiltinAnalyzers.fromString("notathing"));
    }
}
