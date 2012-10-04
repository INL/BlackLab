package nl.inl.util;

import org.junit.Assert;
import org.junit.Test;

public class TestXmlUtil {

	@Test
	public void testXmlToPlainText() {
		// Remove tags
		Assert.assertEquals("test test test", XmlUtil.xmlToPlainText("test <bla>test</bla> test"));

		// Interpret entities
		Assert.assertEquals("test > test", XmlUtil.xmlToPlainText("test &gt; test"));

		// Interpret numerical entities
		Assert.assertEquals("test A test", XmlUtil.xmlToPlainText("test &#65; test"));

		// Interpret hex numerical entities
		Assert.assertEquals("test B test", XmlUtil.xmlToPlainText("test &#x42; test"));

		// Ignore entities inside tags
		Assert.assertEquals("test test", XmlUtil.xmlToPlainText("test <bla test=\"&quot;\" > test"));

		// Other whitespace characters normalized to space
		Assert.assertEquals("test test", XmlUtil.xmlToPlainText("test\ntest"));

		// Normalize whitespace; keep leading space
		Assert.assertEquals(" test test", XmlUtil.xmlToPlainText("\t\ttest \n\rtest"));

		// Replace with non-breaking spaces
		Assert.assertEquals("test\u00A0test", XmlUtil.xmlToPlainText("test test", true));

		// Replace with non-breaking spaces; keep trailing space
		Assert.assertEquals("test\u00A0test\u00A0", XmlUtil.xmlToPlainText("test test ", true));
	}

}
