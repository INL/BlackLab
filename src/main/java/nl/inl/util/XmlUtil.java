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
package nl.inl.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.w3c.dom.Document;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Utilities for working with XML.
 */
public class XmlUtil {
	private static boolean namespaceAware = false;

	static TransformerFactory transformerFactory = TransformerFactory.newInstance();

	public static boolean isNamespaceAware() {
		return namespaceAware;
	}

	public static void setNamespaceAware(boolean namespaceAware) {
		XmlUtil.namespaceAware = namespaceAware;
	}

	/**
	 * Parse XML in string form to a DOM Document
	 *
	 * @param xml
	 *            the XML document
	 * @return the DOM representation of the document
	 * @throws SAXException
	 */
	public static Document parseXml(String xml) throws SAXException {
		return parseXml(new StringReader(xml));
	}

	/**
	 * Parse XML from reader to a DOM Document
	 *
	 * @param reader
	 *            where to read the XML document from
	 * @return the DOM representation of the document
	 * @throws SAXException
	 */
	public static Document parseXml(Reader reader) throws SAXException {
		try {
			DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
			domFactory.setNamespaceAware(namespaceAware);
			DocumentBuilder domBuilder = domFactory.newDocumentBuilder();
			// Avoid errors written to stderr
			domBuilder.setErrorHandler(new ErrorHandler() {
			    @Override
			    public void warning(SAXParseException e) {
			        //
			    }

			    @Override
			    public void fatalError(SAXParseException e) throws SAXException {
			        throw e;
			    }

			    @Override
			    public void error(SAXParseException e) throws SAXException {
			        throw e;
			    }
			});
			Document document = domBuilder.parse(new InputSource(reader));
			return document;
		} catch (ParserConfigurationException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Convert a DOM document tree to its XML string representation
	 *
	 * @param document
	 *            the document to convert
	 * @return the string representation
	 * @throws TransformerFactoryConfigurationError
	 * @throws TransformerException
	 */
	public String serializeDomDocument(Document document)
			throws TransformerFactoryConfigurationError, TransformerException {
		// Prepare the DOM document for writing
		Source source = new DOMSource(document);

		StringWriter stringWriter = new StringWriter();
		StreamResult streamResult = new StreamResult(stringWriter);

		// Maak een string van het document
		Transformer xformer = transformerFactory.newTransformer();
		xformer.transform(source, streamResult);
		return stringWriter.toString();
	}

	/**
	 * Transform an XML file to a HTML (or other) file using an XSLT stylesheet.
	 *
	 * @param inputFile
	 * @param xsltFile
	 * @param outputFile
	 *
	 * @deprecated renamed to transformXslt()
	 */
	@Deprecated
	public static void transformFile(File inputFile, File xsltFile, File outputFile) {
		transformXslt(inputFile, xsltFile, outputFile);
	}

	/**
	 * Transform an XML file to a HTML (or other) file using an XSLT stylesheet.
	 *
	 * @param inputFile input XML file
	 * @param xsltFile XSLT tranformation file
	 * @param outputFile output XML file
	 */
	public static void transformXslt(File inputFile, File xsltFile, File outputFile) {
		Transformer transformer = getXsltTransformer(xsltFile);
		StreamSource source = new StreamSource(inputFile);
		PrintWriter out = FileUtil.openForWriting(outputFile);
		transform(source, transformer, out);
	}

	public static Transformer getXsltTransformer(File xsltFile) {
		try {
			return transformerFactory.newTransformer(new StreamSource(xsltFile));
		} catch (TransformerConfigurationException e) {
			throw new RuntimeException(e);
		}
	}

	public static String transform(String input, Transformer transformer) {
		StringWriter out = new StringWriter();
		transform(input, transformer, out);
		return out.toString();
	}

	public static String transform(File inputFile, Transformer transformer) throws FileNotFoundException {
		StringWriter out = new StringWriter();
		transform(inputFile, transformer, out);
		return out.toString();
	}

	/**
	 * Transform XML according to the supplied transformer
	 *
	 * @param input
	 *            the XML document
	 * @param transformer
	 *            the transformer to use
	 * @param out
	 *            where to write the result
	 */
	public static void transform(String input, Transformer transformer, Writer out) {
		transform(new StreamSource(new StringReader(input)), transformer, out);
	}

	/**
	 * Transform XML according to the supplied transformer
	 *
	 * @param inputFile
	 *            the XML document
	 * @param transformer
	 *            the transformer to use
	 * @param out
	 *            where to write the result
	 * @throws FileNotFoundException
	 */
	public static void transform(File inputFile, Transformer transformer, Writer out)
			throws FileNotFoundException {
		transform(new StreamSource(inputFile), transformer, out);
			}

	/**
	 * Transform XML according to the supplied transformer
	 *
	 * @param inputSource
	 *            the XML document
	 * @param transformer
	 *            the transformer to use
	 * @param out
	 *            where to write the result
	 */
	public static void transform(StreamSource inputSource, Transformer transformer, Writer out)  {
		try {
			StreamResult result = new StreamResult(out);
			transformer.transform(inputSource, result);
			transformer.reset();
			out.flush();
		} catch (TransformerException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Deprecated
	public static String transformString(String input, File xsltFile) throws FileNotFoundException {
		return transformXslt(input, xsltFile);
	}

	public static String transformXslt(String input, File xsltFile) throws FileNotFoundException {
		StringWriter out = new StringWriter();
		transformString(input, xsltFile, out);
		return out.toString();
	}

	/**
	 * Transform XML to HTML.
	 *
	 * @param input
	 *            the XML document
	 * @param xsltFile
	 *            the XSLT file to use
	 * @param out
	 *            where to write the result
	 * @throws FileNotFoundException
	 * @deprecated renamed to transformXslt()
	 */
	@Deprecated
	public static void transformString(String input, File xsltFile, Writer out)
			throws FileNotFoundException {
		transformXslt(input, xsltFile, out);
	}

	/**
	 * Transform XML to HTML.
	 *
	 * @param input
	 *            the XML document
	 * @param xsltFile
	 *            the XSLT file to use
	 * @param out
	 *            where to write the result
	 * @throws FileNotFoundException
	 */
	public static void transformXslt(String input, File xsltFile, Writer out)
			throws FileNotFoundException {
		if (!xsltFile.exists())
			throw new FileNotFoundException("XSLT file " + xsltFile + " not found");
		Transformer transformer = getXsltTransformer(xsltFile);
		StreamSource inputSource = new StreamSource(new StringReader(input));
		transform(inputSource, transformer, out);
	}

	/**
	 * Takes an XML input string and... - removes tags - replaces entities with characters -
	 * normalizes whitespace
	 *
	 * @param conc
	 *            the input XML string
	 * @return the plain text output string
	 */
	public static String xmlToPlainText(String conc) {
		return xmlToPlainText(conc, false);
	}

	/**
	 * States of the xmlToPlainText() state machine
	 */
	private static enum XmlToPlainTextState {

		/** Copy these characters to the destination */
		COPY,

		/** We're inside a tag; don't copy */
		IN_TAG,

		/** We're inside an entity; don't copy, but add appropriate character at end */
		IN_ENTITY,
	}

	/**
	 * Takes an XML input string and...
	 * * removes tags
	 * * replaces entities with characters
	 * * normalizes whitespace
	 * * (optionally) replaces spaces with non-breaking spaces
	 *
	 * @param conc
	 *            the input XML string
	 * @param makeNonBreaking
	 *            if true, the output string only contains non-breaking spaces
	 * @return the plain text output string
	 */
	public static String xmlToPlainText(String conc, boolean makeNonBreaking) {
		// Allocate buffer.
		int inputLength = conc.length();
		int bufferLength = inputLength;
		char[] src = new char[bufferLength];

		// Get character array
		conc.getChars(0, inputLength, src, 0);

		// Loop through character array
		int dstIndex = 0;
		XmlToPlainTextState state = XmlToPlainTextState.COPY;
		int entityStart = -1;
		char space = ' ';
		if (makeNonBreaking)
			space = StringUtil.CHAR_NON_BREAKING_SPACE; // Non-breaking space (codepoint 160)
		boolean lastCopiedASpace = false; // To normalize whitespace
		for (int srcIndex = 0; srcIndex < inputLength; srcIndex++) {
			char c = src[srcIndex];
			switch (c) {
			case '<':
				// Entering tag
				state = XmlToPlainTextState.IN_TAG;
				break;

			case '>':
				// Leaving tag, back to copy
				state = XmlToPlainTextState.COPY;
				break;

			case '&':
				// Entering entity (NOTE: ignore entities if we're inside a tag)
				if (state != XmlToPlainTextState.IN_TAG) {
					// Go to entity state
					state = XmlToPlainTextState.IN_ENTITY;
					entityStart = srcIndex + 1;
				}
				break;

			case ';':
				if (state == XmlToPlainTextState.IN_ENTITY) {
					// Leaving entity
					char whichEntity = '!';
					String entityName = conc.substring(entityStart, srcIndex);
					if (entityName.equals("lt"))
						whichEntity = '<';
					else if (entityName.equals("gt"))
						whichEntity = '>';
					else if (entityName.equals("amp"))
						whichEntity = '&';
					else if (entityName.equals("quot"))
						whichEntity = '"';
					else if (entityName.startsWith("#x")) {
						// Hex entity
						whichEntity = (char) Integer.parseInt(entityName.substring(2), 16);
					} else if (entityName.length() > 0 && entityName.charAt(0) == '#') {
						// Decimal entity
						whichEntity = (char) Integer.parseInt(entityName.substring(1), 10);
					} else {
						// Unknown entity!
						whichEntity = '?';
					}

					// Put character in destination buffer
					src[dstIndex] = whichEntity;
					dstIndex++;
					lastCopiedASpace = false; // should be: whichEntity == ' ' || ...

					// Back to copy state
					state = XmlToPlainTextState.COPY;
				} else if (state == XmlToPlainTextState.COPY) {
					// Not in entity or tag, just copy character
					src[dstIndex] = c;
					dstIndex++;
					lastCopiedASpace = false;
				}
				// else: inside tag, ignore all characters until end of tag
				break;

			case ' ':
			case '\t':
			case '\n':
			case '\r':
			case '\u00A0':
				if (state == XmlToPlainTextState.COPY && !lastCopiedASpace) {
					// First space in a run; copy it
					src[dstIndex] = space;
					dstIndex++;
					lastCopiedASpace = true;
				}
				break;

			default:
				if (state == XmlToPlainTextState.COPY) {
					// Copy character
					src[dstIndex] = c;
					dstIndex++;
					lastCopiedASpace = false;
				}
				break;
			}
		}

		return new String(src, 0, dstIndex);
	}

}
