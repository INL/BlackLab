package nl.inl.util;

import java.util.ArrayList;
import java.util.Arrays;

import junit.framework.Assert;

import org.junit.Test;

public class TestStringUtil {
	@Test
	public void testJoin() {
		Assert.assertEquals("aap noot mies",
				StringUtil.join(Arrays.asList("aap", "noot", "mies"), " "));
		Assert.assertEquals("aap", StringUtil.join(Arrays.asList("aap"), " "));
		Assert.assertEquals("", StringUtil.join(new ArrayList<String>(), " "));
	}

	@Test
	public void testRemoveAccents() {
		Assert.assertEquals("He, jij!", StringUtil.removeAccents("Hé, jij!"));
	}

	@Test
	public void testRemovePunctuation() {
		Assert.assertEquals("Hé  jij1 ", StringUtil.removePunctuation("Hé, jij1!"));
	}

	@Test
	public void testEscapeXmlChars() {
		Assert.assertEquals("Test &lt; &amp; &gt; &quot; test",
				StringUtil.escapeXmlChars("Test < & > \" test"));
	}

}
