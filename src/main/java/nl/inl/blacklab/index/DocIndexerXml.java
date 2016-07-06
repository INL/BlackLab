/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package nl.inl.blacklab.index;

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Abstract base class for a DocIndexer processing XML files.
 * @deprecated use DocIndexerXmlHandlers instead; see project site for a guide
 */
@Deprecated
public abstract class DocIndexerXml extends DocIndexerAbstract {
	class SaxParseHandler extends DefaultHandler {
		/** to keep track of the position within the document */
		protected Locator locator;

		@Override
		public void setDocumentLocator(Locator locator) {
			this.locator = locator;
		}

		@Override
		public void characters(char[] buffer, int start, int length) throws SAXException {
			super.characters(buffer, start, length);
			DocIndexerXml.this.characters(buffer, start, length);
		}

		@Override
		public void endElement(String uri, String localName, String qName) throws SAXException {
			super.endElement(uri, localName, qName);
			DocIndexerXml.this.endElement(uri, localName, qName);
		}

		@Override
		public void startElement(String uri, String localName, String qName, Attributes attributes)
				throws SAXException {
			super.startElement(uri, localName, qName, attributes);
			DocIndexerXml.this.startElement(uri, localName, qName, attributes);
		}

		@Override
		public void processingInstruction(String target, String data) throws SAXException {
			super.processingInstruction(target, data);
			DocIndexerXml.this.processingInstruction(target, data);
		}

		@Override
		public void startPrefixMapping(String prefix, String uri) throws SAXException {
			super.startPrefixMapping(prefix, uri);
			DocIndexerXml.this.startPrefixMapping(prefix, uri);
		}

		@Override
		public void endPrefixMapping(String prefix) throws SAXException {
			super.endPrefixMapping(prefix);
			DocIndexerXml.this.endPrefixMapping(prefix);
		}

		public String describePosition() {
			return "line " + locator.getLineNumber() + ", position " + locator.getColumnNumber();
		}

	}

	SaxParseHandler saxParseHandler = new SaxParseHandler();

	/**
	 * What namespace prefix mappings have we encountered but not output in a start tag
	 * yet? (used to make sure the stored XML contains all the required mappings)
	 */
	final protected static Map<String,String> outputPrefixMapping = new HashMap<>();

	/**
	 * Encountered a prefix to namespace mapping; now in effect.
	 * @param prefix the prefix that is now in effect
	 * @param uri the namespace the prefix refers to
	 */
	public void startPrefixMapping(String prefix, String uri) {
		outputPrefixMapping.put(prefix, uri);
	}

	/**
	 * A previously encountered namespace prefix mapping is no longer in effect.
	 * @param prefix the prefix that's no longer in effect.
	 */
	public void endPrefixMapping(String prefix) {
		//System.out.println("END PREFIX MAPPING: " + prefix);
	}

	public DocIndexerXml(Indexer indexer, String fileName, Reader reader) {
		super(indexer, fileName, reader);
	}

	/**
	 * StringBuffer re-used for building start/end tags and processing instructions.
	 */
	StringBuilder elementBuilder = new StringBuilder();

	public void startElement(String uri, String localName, String qName, Attributes attributes) {
		elementBuilder.delete(0, elementBuilder.length());
		elementBuilder.append("<").append(qName);
		for (int i = 0; i < attributes.getLength(); i++) {
			String value = escapeXmlChars(attributes.getValue(i));
			elementBuilder.append(" ").append(attributes.getQName(i)).append("=\"")
					.append(value).append("\"");
		}
		// Append any namespace mapping not yet outputted
		for (Map.Entry<String, String> e: outputPrefixMapping.entrySet()) {
			if (e.getKey().length() == 0)
				elementBuilder.append(" xmlns=\"").append(e.getValue()).append("\"");
			else
				elementBuilder.append(" xmlns:").append(e.getKey()).append("=\"").append(e.getValue()).append("\"");
		}
		outputPrefixMapping.clear(); // outputted all prefix mappings for now
		elementBuilder.append(">");
		processContent(elementBuilder.toString());
	}

	/**
	 * StringBuffer re-used for escaping XML chars
	 */
	StringBuilder escapeBuilder = new StringBuilder();

	/**
	 * Escape the special XML chars (<, >, &, ") with their named entity equivalents.
	 *
	 * NOTE: copy of StringUtil.escapeXmlChars that re-uses its StringBuilder for increased memory
	 * efficiency.
	 *
	 * @param source
	 *            the source string
	 * @return the escaped string
	 */
	public String escapeXmlChars(String source) {
		escapeBuilder.delete(0, escapeBuilder.length());
		int start = 0;
		char[] srcArr = new char[source.length()];
		source.getChars(0, source.length(), srcArr, 0);
		int end = source.length();
		for (int i = 0; i < end; i++) {
			char c = srcArr[i]; // source.charAt(i);
			if (c == '<' || c == '>' || c == '&' || c == '"') {
				escapeBuilder.append(srcArr, start, i - start); // source.substring(start, i));
				switch (c) {
				case '<':
					escapeBuilder.append("&lt;");
					break;
				case '>':
					escapeBuilder.append("&gt;");
					break;
				case '&':
					escapeBuilder.append("&amp;");
					break;
				case '"':
					escapeBuilder.append("&quot;");
					break;
				default:
					// Not possible
					throw new RuntimeException();
				}
				start = i + 1;
			}
		}
		escapeBuilder.append(srcArr, start, end - start); // source.substring(start));
		return escapeBuilder.toString();
	}

	/**
	 * Escape the special XML chars (<, >, &, ") with their named entity equivalents.
	 *
	 * NOTE: copy of StringUtil.escapeXmlChars that re-uses its StringBuilder for increased memory
	 * efficiency.
	 *
	 * @param source
	 *            the source string
	 * @param start where in the string to start processing
	 * @param length how many characters to process
	 * @return the escaped string
	 */
	public String escapeXmlChars(char[] source, int start, int length) {
		escapeBuilder.delete(0, escapeBuilder.length());
		int end = start + length;
		for (int i = start; i < end; i++) {
			char c = source[i];
			if (c == '<' || c == '>' || c == '&' || c == '"') {
				escapeBuilder.append(source, start, i - start);
				switch (c) {
				case '<':
					escapeBuilder.append("&lt;");
					break;
				case '>':
					escapeBuilder.append("&gt;");
					break;
				case '&':
					escapeBuilder.append("&amp;");
					break;
				case '"':
					escapeBuilder.append("&quot;");
					break;
				}
				start = i + 1;
			}
		}
		escapeBuilder.append(source, start, end - start);
		return escapeBuilder.toString();
	}

	public void characters(char[] buffer, int start, int length) {
		String s = escapeXmlChars(buffer, start, length);
		processContent(s);
	}

	public void endElement(String uri, String localName, String qName) {
		elementBuilder.delete(0, elementBuilder.length());
		elementBuilder.append("</").append(qName).append(">");
		processContent(elementBuilder.toString());
	}

	public void processingInstruction(String target, String data) {
		elementBuilder.delete(0, elementBuilder.length());
		elementBuilder.append("<?").append(target).append(" ").append(data).append("?>");
		processContent(elementBuilder.toString());
	}

	@Override
	public void index() throws IOException, InputFormatException  {
		SAXParserFactory factory = SAXParserFactory.newInstance();
		factory.setNamespaceAware(true);
		SAXParser parser;
		try {
			parser = factory.newSAXParser();
		} catch (Exception e1) {
			// Unrecoverable error, throw runtime exception
			throw new RuntimeException(e1);
		}
//		try {
			try {
				parser.parse(new InputSource(reader), saxParseHandler);
			} catch (IOException e) {
				throw e;
			} catch (SAXException e) {
				throw new InputFormatException();
			} catch (DocIndexer.MaxDocsReachedException e) {
				// OK; just stop indexing prematurely
			}
//		} catch (Exception e) {
//			System.out.println("An exception occurred while parsing at " + describePosition());
//			throw e;
//		}

		if (nDocumentsSkipped > 0)
			System.err.println("Skipped " + nDocumentsSkipped + " large documents");
	}

	protected String describePosition() {
		return saxParseHandler.describePosition();
	}

	// protected abstract OutputStreamWriter getXmlContentWriter();
}
