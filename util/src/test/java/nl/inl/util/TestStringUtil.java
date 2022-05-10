package nl.inl.util;

import org.junit.Assert;
import org.junit.Test;

public class TestStringUtil {

    @Test
    public void testRemoveAccents() {
        Assert.assertEquals("He, jij!", StringUtil.stripAccents("Hé, jij!"));
    }

}
