package nl.inl.blacklab.server.requesthandlers;

import java.text.Collator;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import org.junit.Assert;

import nl.inl.blacklab.server.requesthandlers.RequestHandlerFieldInfo;

public class TestValueSort {
    
    @Test
    public void testValueSort() throws ParseException {
        List<String> list = Arrays.asList("vuur", ")vis(", "noot", "(mies)", "aap", "aa(n)", "aa(s)");
        List<String> expected = Arrays.asList("aa(n)", "aap", "aa(s)", "(mies)", "noot", ")vis(", "vuur");
        Collator coll = RequestHandlerFieldInfo.getValueSortCollator();
        Collections.sort(list, coll);
        Assert.assertEquals(expected, list);
    }

}
