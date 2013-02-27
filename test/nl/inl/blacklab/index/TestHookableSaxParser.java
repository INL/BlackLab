package nl.inl.blacklab.index;

import java.io.IOException;
import java.io.StringReader;

import junit.framework.Assert;
import nl.inl.blacklab.index.HookableSaxParser.SaxParserHandler;

import org.junit.Before;
import org.junit.Test;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class TestHookableSaxParser {

	private HookableSaxParser hookableSaxParser;

	private InputSource inputSource;

	private SaxParserHandler handler;

	@Before
	public void setUp() {
		String testXml = "<root>"
				+ "<child><name>A</name><child att='123'><name>C</name></child></child>"
				+ "<child><name>B</name><child att='456'><name>D</name></child></child>"
				+ "</root>";
		inputSource = new InputSource(new StringReader(testXml));
		hookableSaxParser = new HookableSaxParser();
		handler = new StringConcatenateHandler();
	}

	class StringConcatenateHandler extends SaxParserHandler {

		private StringBuilder builder = new StringBuilder();

		public StringConcatenateHandler() {
			//
		}

		@Override
		public String toString() {
			return builder.toString();
		}

		@Override
		public void startElement(String uri, String localName, String qName, Attributes attributes) {
			builder.append("<");
		}

		@Override
		public void endElement(String uri, String localName, String qName) {
			builder.append(">");
		}

		@Override
		public void characters(char[] ch, int start, int length) {
			builder.append("$");
		}

		@Override
		public void attribute(String name, String value) {
			builder.append("@");
		}

	}

	@Test
	public void testRootChild() throws SAXException, IOException {
		hookableSaxParser.addHook("/root/child", false, handler);
		hookableSaxParser.parse(inputSource);
		Assert.assertEquals("<><>", handler.toString());
	}

	@Test
	public void testAnyChildName() throws SAXException, IOException {
		hookableSaxParser.addHook("//child/name", false, handler);
		hookableSaxParser.parse(inputSource);
		Assert.assertEquals("<$><$><$><$>", handler.toString());
	}

	@Test
	public void testAllDescendants() throws SAXException, IOException {
		hookableSaxParser.addHook("/root", true, handler);
		hookableSaxParser.parse(inputSource);
		Assert.assertEquals("<<<$><<$>>><<$><<$>>>>", handler.toString());
	}

	@Test
	public void testAttribute() throws SAXException, IOException {
		hookableSaxParser.addHook("//@att", true, handler);
		hookableSaxParser.parse(inputSource);
		Assert.assertEquals("@@", handler.toString());
	}
}
