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

import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Constructor;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import nl.inl.blacklab.index.HookableSaxHandler.ContentCapturingHandler;
import nl.inl.blacklab.index.HookableSaxHandler.ElementHandler;
import nl.inl.blacklab.index.complex.ComplexField;
import nl.inl.blacklab.index.complex.ComplexFieldProperty;
import nl.inl.blacklab.index.complex.ComplexFieldProperty.SensitivitySetting;
import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.search.Searcher;
import nl.inl.blacklab.search.indexstructure.FieldType;
import nl.inl.blacklab.search.indexstructure.IndexStructure;
import nl.inl.blacklab.search.indexstructure.MetadataFieldDesc;
import nl.inl.blacklab.search.indexstructure.MetadataFieldDesc.UnknownCondition;
import nl.inl.util.ExUtil;
import nl.inl.util.StringUtil;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.IntField;
import org.apache.lucene.util.BytesRef;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Abstract base class for a DocIndexer processing XML files using the hookable
 * SAX parser.
 */
public abstract class DocIndexerXmlHandlers extends DocIndexerAbstract {

	/**
	 * Max. length of captured character content. Should only be used for short
	 * strings, such as a word, or the value of a metadata field.
	 */
	private static final int MAX_CHARACTER_CONTENT_CAPTURE_LENGTH = 4000;

	private HookableSaxHandler hookableHandler = new HookableSaxHandler();

	private SaxParseHandler saxParseHandler = new SaxParseHandler();

	/**
	 * What namespace prefix mappings have we encountered but not output in a
	 * start tag yet? (used to make sure the stored XML contains all the
	 * required mappings)
	 */
	final protected static Map<String, String> outputPrefixMapping = new HashMap<>();

	/**
	 * Handle Document element. Starts a new Lucene document and adds the
	 * attributes of this element (if any) as metadata fields.
	 */
	public class DocumentElementHandler extends ElementHandler {

		/** Open tag: start indexing this document */
		@Override
		public void startElement(String uri, String localName, String qName,
				Attributes attributes) {
			startCaptureContent(contentsField.getName());

			currentLuceneDoc = new Document();
			currentDocumentName = fileName;
			if (currentDocumentName == null)
				currentDocumentName = "?";
			// Store attribute values from the tag as metadata fields
			for (int i = 0; i < attributes.getLength(); i++) {
				addMetadataField(attributes.getLocalName(i),
						attributes.getValue(i));
			}
			currentLuceneDoc.add(new Field("fromInputFile", currentDocumentName, indexer.metadataFieldTypeUntokenized));
			addMetadataFieldsFromParameters();
			indexer.getListener().documentStarted(currentDocumentName);
		}

		/** Open tag: end indexing the document */
		@Override
		public void endElement(String uri, String localName, String qName) {
			// Make sure all the properties have an equal number of values.
			// See what property has the highest position
			// (in practice, only starttags and endtags should be able to have
			// a position one higher than the rest)
			int lastValuePos = 0;
			for (ComplexFieldProperty prop : contentsField.getProperties()) {
				if (prop.lastValuePosition() > lastValuePos)
					lastValuePos = prop.lastValuePosition();
			}

			// Make sure we always have one more token than the number of
			// words, so there's room for any tags after the last word, and we
			// know we should always skip the last token when matching.
			if (propMain.lastValuePosition() == lastValuePos)
				lastValuePos++;

			// Add empty values to all lagging properties
			for (ComplexFieldProperty prop : contentsField.getProperties()) {
				while (prop.lastValuePosition() < lastValuePos) {
					prop.addValue("");
					if (prop.hasPayload())
						prop.addPayload(null);
					if (prop == propMain) {
						contentsField.addStartChar(getCharacterPosition());
						contentsField.addEndChar(getCharacterPosition());
					}
				}
			}

			// Finish storing the document in the document store (parts of it
			// may already have been written because we write in chunks to save memory),
			// retrieve the content id, and store that in Lucene.
			// (Note that we do this after adding the dummy token, so the character
			//  positions for the dummy token still make (some) sense)
			int contentId = storeCapturedContent();
			currentLuceneDoc.add(new IntField(ComplexFieldUtil
					.contentIdField(contentsField.getName()), contentId,
					Store.YES));

			// Store the different properties of the complex contents field that
			// were gathered in
			// lists while parsing.
			contentsField.addToLuceneDoc(currentLuceneDoc);


			// Add all properties to forward index
			for (ComplexFieldProperty prop : contentsField.getProperties()) {
				if (!prop.hasForwardIndex())
					continue;

				// Add property (case-sensitive tokens) to forward index and add
				// id to Lucene doc
				String propName = prop.getName();
				String fieldName = ComplexFieldUtil.propertyField(
						contentsField.getName(), propName);
				int fiid = indexer.addToForwardIndex(fieldName, prop);
				currentLuceneDoc.add(new IntField(ComplexFieldUtil
						.forwardIndexIdField(fieldName), fiid, Store.YES));
			}

			// If there's an external metadata fetcher, call it now so it can
			// add the metadata for this document and (optionally) store the
			// metadata
			// document in the content store (and the corresponding id in the
			// Lucene doc)
			MetadataFetcher m = getMetadataFetcher();
			if (m != null) {
				m.addMetadata();
			}

			// See what metadatafields are missing or empty and add unknown value
			// if desired.
			IndexStructure struct = indexer.getSearcher().getIndexStructure();
			for (String fieldName: struct.getMetadataFields()) {
				MetadataFieldDesc fd = struct.getMetadataFieldDesc(fieldName);
				boolean missing = false, empty = false;
				String currentValue = currentLuceneDoc.get(fieldName);
				if (currentValue == null)
					missing = true;
				else if (currentValue.length() == 0)
					empty = true;
				UnknownCondition cond = fd.getUnknownCondition();
				boolean useUnknownValue = false;
				switch(cond) {
				case EMPTY:
					useUnknownValue = empty;
					break;
				case MISSING:
					useUnknownValue = missing;
					break;
				case MISSING_OR_EMPTY:
					useUnknownValue = missing | empty;
					break;
				case NEVER:
					useUnknownValue = false;
					break;
				}
				if (useUnknownValue)
					addMetadataField(fieldName, fd.getUnknownValue());
			}

			try {
				// Add Lucene doc to indexer
				indexer.add(currentLuceneDoc);
			} catch (Exception e) {
				throw ExUtil.wrapRuntimeException(e);
			}

			// Report progress
			reportCharsProcessed();
			reportTokensProcessed(wordsDone);
			wordsDone = 0;
			indexer.getListener().documentDone(currentDocumentName);

			// Reset contents field for next document
			contentsField.clear();
			currentLuceneDoc = null;

			// Stop if required
			if (!indexer.continueIndexing())
				throw new MaxDocsReachedException();
		}
	}

	/**
	 * If any metadata fields were supplied in the indexer parameters,
	 * add them now.
	 */
	void addMetadataFieldsFromParameters() {
		for (Entry<String, String> e: parameters.entrySet()) {
			if (e.getKey().startsWith("meta-")) {
				String fieldName = e.getKey().substring(5);
				String fieldValue = e.getValue();
				currentLuceneDoc.add(new Field(fieldName, fieldValue, indexer.metadataFieldTypeUntokenized));
			}
		}
	}

	/**
	 * Stores metadata field with element name as name and element content as
	 * value.
	 */
	public class MetadataElementHandler extends ContentCapturingHandler {
		/** Close tag: store the value of this metadata field */
		@Override
		public void endElement(String uri, String localName, String qName) {
			super.endElement(uri, localName, qName);

			// Header element ended; index the element with the character
			// content captured
			// (this is stuff like title, yearFrom, yearTo, etc.)
			addMetadataField(localName, getElementContent().trim());
		}
	}

	/** Add element attributes as metadata. */
	public class MetadataAttributesHandler extends ContentCapturingHandler {

		/** Open tag: add attributes as metadata */
		@Override
		public void startElement(String uri, String localName, String qName,
				Attributes attributes) {
			super.startElement(uri, localName, qName, attributes);

			// Store attribute values from the tag as fields
			for (int i = 0; i < attributes.getLength(); i++) {
				addMetadataField(attributes.getLocalName(i),
						attributes.getValue(i));
			}
		}
	}

	/**
	 * Add a metadatafield based on two attributes of an element, a name
	 * attribute (giving the field name) and a value attribute (giving the field
	 * value).
	 */
	public class MetadataNameValueAttributeHandler extends
			ContentCapturingHandler {

		private String nameAttribute;

		private String valueAttribute;

		public MetadataNameValueAttributeHandler(String nameAttribute,
				String valueAttribute) {
			this.nameAttribute = nameAttribute;
			this.valueAttribute = valueAttribute;
		}

		public MetadataNameValueAttributeHandler() {
			this("name", "value");
		}

		/** Open tag: add metadata field */
		@Override
		public void startElement(String uri, String localName, String qName,
				Attributes attributes) {
			super.startElement(uri, localName, qName, attributes);
			String name = attributes.getValue(nameAttribute);
			String value = attributes.getValue(valueAttribute);
			if (name != null && value != null)
				addMetadataField(name, value);
		}
	}

	/* Position of start tags and their index in the property arrays, so we can add payload when we find the end tags */
	class OpenTagInfo {
		public int position;
		public int index;
		public OpenTagInfo(int position, int index) {
			this.position = position;
			this.index = index;
		}
	}
	List<OpenTagInfo> openTags = new ArrayList<>();

	/** Handle tags. */
	public class InlineTagHandler extends ElementHandler {

		/** Open tag: store the start tag location and the attribute values */
		@Override
		public void startElement(String uri, String localName, String qName,
				Attributes attributes) {
			int lastStartTagPos = propStartTag.lastValuePosition();
			int currentPos = propMain.lastValuePosition() + 1;
			int posIncrement = currentPos - lastStartTagPos;
			propStartTag.addValue(localName, posIncrement);
			propStartTag.addPayload(null);
			int startTagIndex = propStartTag.getLastValueIndex();
			openTags.add(new OpenTagInfo(currentPos, startTagIndex));
			for (int i = 0; i < attributes.getLength(); i++) {
				// Index element attribute values
				String name = attributes.getLocalName(i);
				String value = attributes.getValue(i);
				propStartTag.addValue(
						"@" + name.toLowerCase() + "__" + value.toLowerCase(),
						0);
				propStartTag.addPayload(null);
			}
		}

		/** Close tag: store the end tag location */
		@Override
		public void endElement(String uri, String localName, String qName) {
			int currentPos = propMain.lastValuePosition() + 1;

			// Add payload to start tag property indicating end position
			OpenTagInfo openTag = openTags.remove(openTags.size() - 1);
			byte[] payload = ByteBuffer.allocate(4).putInt(currentPos).array();
			propStartTag.setPayloadAtIndex(openTag.index, new BytesRef(payload));
		}
	}

	/**
	 * Base handler for word tags: adds start and end positions around the
	 * element.
	 */
	public class WordHandlerBase extends ElementHandler {

		/** Open tag: save start character position */
		@Override
		public void startElement(String uri, String localName, String qName,
				Attributes attributes) {
			super.startElement(uri, localName, qName, attributes);
			contentsField.addStartChar(getCharacterPosition());
		}

		/**
		 * Close tag: save end character position, add token to contents field
		 * and report progress.
		 */
		@Override
		public void endElement(String uri, String localName, String qName) {
			super.endElement(uri, localName, qName);
			contentsField.addEndChar(getCharacterPosition());

			// Report progress regularly but not too often
			wordsDone++;
			if (wordsDone >= 5000) {
				reportCharsProcessed();
				reportTokensProcessed(wordsDone);
				wordsDone = 0;
			}
		}

	}

	/** Handle &lt;Word&gt; tags (word tokens). */
	public class DefaultWordHandler extends WordHandlerBase {

		/** Open tag: save start character position */
		@Override
		public void startElement(String uri, String localName, String qName,
				Attributes attributes) {
			super.startElement(uri, localName, qName, attributes);
			propPunct.addValue(StringUtil
					.normalizeWhitespace(consumeCharacterContent()));
		}

		/**
		 * Close tag: save end character position, add token to contents field
		 * and report progress.
		 */
		@Override
		public void endElement(String uri, String localName, String qName) {
			super.endElement(uri, localName, qName);
			propMain.addValue(getWord());
		}

		protected String getWord() {
			return consumeCharacterContent();
		}

	}

	/** Handle &lt;Word&gt; tags (word tokens). */
	public class WordInAttributeHandler extends DefaultWordHandler {

		private String attName;

		protected String currentWord;

		public WordInAttributeHandler(String attName) {
			this.attName = attName;
		}

		/** Open tag: get word from attribute value */
		@Override
		public void startElement(String uri, String localName, String qName,
				Attributes attributes) {
			super.startElement(uri, localName, qName, attributes);
			currentWord = attributes.getValue(attName);
			if (currentWord == null)
				currentWord = "";
		}

		@Override
		protected String getWord() {
			return currentWord;
		}

	}

	/**
	 * Encountered a prefix to namespace mapping; now in effect.
	 *
	 * @param prefix
	 *            the prefix that is now in effect
	 * @param uri
	 *            the namespace the prefix refers to
	 */
	public void startPrefixMapping(String prefix, String uri) {
		outputPrefixMapping.put(prefix, uri);
	}

	/**
	 * A previously encountered namespace prefix mapping is no longer in effect.
	 *
	 * @param prefix
	 *            the prefix that's no longer in effect.
	 */
	public void endPrefixMapping(String prefix) {
		// System.out.println("END PREFIX MAPPING: " + prefix);
	}

	SensitivitySetting getSensitivitySetting(String propName) {
		// See if it's specified in a parameter
		String strSensitivity = getParameter(propName + "_sensitivity");
		if (strSensitivity != null) {
			if (strSensitivity.equals("i"))
				return SensitivitySetting.ONLY_INSENSITIVE;
			if (strSensitivity.equals("s"))
				return SensitivitySetting.ONLY_SENSITIVE;
			if (strSensitivity.equals("si") || strSensitivity.equals("is"))
				return SensitivitySetting.SENSITIVE_AND_INSENSITIVE;
			if (strSensitivity.equals("all"))
				return SensitivitySetting.CASE_AND_DIACRITICS_SEPARATE;
		}

		// Not in parameter (or unrecognized value), use default based on
		// propName
		if (propName.equals(ComplexFieldUtil.getDefaultMainPropName())
				|| propName.equals(ComplexFieldUtil.LEMMA_PROP_NAME)) {
			// Word: default to sensitive/insensitive
			return SensitivitySetting.SENSITIVE_AND_INSENSITIVE;
		}
		if (propName.equals(ComplexFieldUtil.PUNCTUATION_PROP_NAME)) {
			// Punctuation: default to only insensitive
			return SensitivitySetting.ONLY_INSENSITIVE;
		}
		if (propName.equals(ComplexFieldUtil.START_TAG_PROP_NAME)
				|| propName.equals(ComplexFieldUtil.END_TAG_PROP_NAME)) {
			// XML tag properties: default to only sensitive
			return SensitivitySetting.ONLY_SENSITIVE;
		}

		// Unrecognized; default to only insensitive
		return SensitivitySetting.ONLY_INSENSITIVE;
	}

	protected ComplexFieldProperty addProperty(String propName) {
		return addProperty(propName, false);
	}

	protected ComplexFieldProperty addProperty(String propName, boolean includePayloads) {
		return contentsField.addProperty(propName,
				getSensitivitySetting(propName), includePayloads);
	}

	public DocIndexerXmlHandlers(Indexer indexer, String fileName, Reader reader) {
		super(indexer, fileName, reader);

		// Define the properties that make up our complex field
		String mainPropName = ComplexFieldUtil.getDefaultMainPropName();
		contentsField = new ComplexField(Searcher.DEFAULT_CONTENTS_FIELD_NAME,
				mainPropName, getSensitivitySetting(mainPropName), false);
		propMain = contentsField.getMainProperty();
		propPunct = addProperty(ComplexFieldUtil.PUNCTUATION_PROP_NAME);
		propStartTag = addProperty(ComplexFieldUtil.START_TAG_PROP_NAME, true); // start tag positions
		propStartTag.setForwardIndex(false);
		IndexStructure indexStructure = indexer.getSearcher().getIndexStructure();
		indexStructure.registerComplexField(contentsField.getName(), propMain.getName());

		// If the indexmetadata file specified a list of properties that shouldn't get a forward index,
		// make the new complex field aware of this.
		Set<String> noForwardIndexProps = indexStructure.getComplexFieldDesc(Searcher.DEFAULT_CONTENTS_FIELD_NAME).getNoForwardIndexProps();
		contentsField.setNoForwardIndexProps(noForwardIndexProps);
	}

	public void addNumericFields(Collection<String> fields) {
		numericFields.addAll(fields);
	}

	/**
	 * StringBuffer re-used for building start/end tags and processing
	 * instructions.
	 */
	StringBuilder elementBuilder = new StringBuilder();

	public void startElement(String uri, String localName, String qName,
			Attributes attributes) {
		// Call any hooks associated with this element
		hookableHandler.startElement(uri, localName, qName, attributes);

		elementBuilder.setLength(0); // clear
		elementBuilder.append("<").append(qName);
		for (int i = 0; i < attributes.getLength(); i++) {
			String value = escapeXmlChars(attributes.getValue(i));
			elementBuilder.append(" ").append(attributes.getQName(i))
					.append("=\"").append(value).append("\"");
		}
		// Append any namespace mapping not yet outputted
		if (!outputPrefixMapping.isEmpty()) {
			for (Map.Entry<String, String> e : outputPrefixMapping.entrySet()) {
				if (e.getKey().length() == 0)
					elementBuilder.append(" xmlns=\"").append(e.getValue())
							.append("\"");
				else
					elementBuilder.append(" xmlns:").append(e.getKey())
							.append("=\"").append(e.getValue()).append("\"");
			}
			outputPrefixMapping.clear(); // outputted all prefix mappings for
											// now
		}
		elementBuilder.append(">");
		processContent(elementBuilder.toString());
	}

	/**
	 * StringBuffer re-used for escaping XML chars
	 */
	StringBuilder escapeBuilder = new StringBuilder();

	/**
	 * Escape the special XML chars (<, >, &, ") with their named entity
	 * equivalents.
	 *
	 * NOTE: copy of StringUtil.escapeXmlChars that re-uses its StringBuilder
	 * for increased memory efficiency.
	 *
	 * @param source
	 *            the source string
	 * @return the escaped string
	 */
	public String escapeXmlChars(String source) {
		escapeBuilder.setLength(0); // clear
		int start = 0;
		char[] srcArr = new char[source.length()];
		source.getChars(0, source.length(), srcArr, 0);
		int end = source.length();
		for (int i = 0; i < end; i++) {
			char c = srcArr[i]; // source.charAt(i);
			if (c == '<' || c == '>' || c == '&' || c == '"') {
				escapeBuilder.append(srcArr, start, i - start); // source.substring(start,
																// i));
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
		escapeBuilder.append(srcArr, start, end - start); // source.substring(start));
		return escapeBuilder.toString();
	}

	/**
	 * Escape the special XML chars (<, >, &, ") with their named entity
	 * equivalents.
	 *
	 * NOTE: copy of StringUtil.escapeXmlChars that re-uses its StringBuilder
	 * for increased memory efficiency.
	 *
	 * @param source
	 *            the source string
	 * @param start
	 *            start index of the string to escape
	 * @param length
	 *            length of the string to escape
	 * @return the escaped string
	 */
	public String escapeXmlChars(char[] source, int start, int length) {
		escapeBuilder.setLength(0); // clear
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

	/**
	 * Character content encountered in the XML document since the last call to
	 * consumeCharacterContent().
	 */
	StringBuilder characterContent = new StringBuilder();

	/**
	 * Returns and resets the character content captured since the last call to
	 * this method.
	 *
	 * @return the captured character content.
	 */
	public String consumeCharacterContent() {
		String content = characterContent.toString();
		characterContent.setLength(0);
		return content;
	}

	public void characters(char[] buffer, int start, int length) {
		// Capture character content in string builder
		if (characterContent.length() < MAX_CHARACTER_CONTENT_CAPTURE_LENGTH)
			characterContent.append(buffer, start, length);

		String s = escapeXmlChars(buffer, start, length);
		processContent(s);

		// Call any hooks associated with this element
		hookableHandler.characters(buffer, start, length);
	}

	/**
	 * The Lucene Document we're currently constructing (corresponds to the
	 * document we're indexing)
	 */
	Document currentLuceneDoc;

	public Document getCurrentLuceneDoc() {
		return currentLuceneDoc;
	}

	/** Name of the document currently being indexed */
	String currentDocumentName;

	Set<String> numericFields = new HashSet<>();

	/**
	 * Complex field where different aspects (word form, named entity status,
	 * etc.) of the main content of the document are captured for indexing.
	 */
	ComplexField contentsField;

	/** Number of words processed (for reporting progress) */
	int wordsDone;

	/** The main property (usually "word") */
	ComplexFieldProperty propMain;

	/** The punctuation property */
	ComplexFieldProperty propPunct;

	/** The start tag property. Also contains tag length in payload. */
	ComplexFieldProperty propStartTag;

	/**
	 * A metadata fetcher can fetch the metadata for a document from some
	 * external source (a file, the network, etc.) and add it to the Lucene
	 * document.
	 */
	static abstract public class MetadataFetcher implements Closeable {

		protected DocIndexer docIndexer;

		public MetadataFetcher(DocIndexer docIndexer) {
			this.docIndexer = docIndexer;
		}

		/**
		 * Fetch the metadata for the document currently being indexed and add
		 * it to the document as indexed fields.
		 */
		abstract public void addMetadata();

		/** Close the fetcher, releasing any resources it holds
		 * @throws IOException if closing caused an error
		 */
		@Override
		public void close() throws IOException {
			// Nothing, by default
		}

	}

	/**
	 * Our external metadata fetcher (if any), responsible for looking up the
	 * metadata and adding it to the Lucene document.
	 */
	MetadataFetcher metadataFetcher;

	/**
	 * Get the external metadata fetcher for this indexer, if any.
	 *
	 * The metadata fetcher can be configured through the "metadataFetcherClass"
	 * parameter.
	 *
	 * @return the metadata fetcher if any, or null if there is none.
	 */
	MetadataFetcher getMetadataFetcher() {
		if (metadataFetcher == null) {
			String metadataFetcherClassName = getParameter("metadataFetcherClass");
			if (metadataFetcherClassName != null) {
				try {
					Class<? extends MetadataFetcher> metadataFetcherClass = Class.forName(metadataFetcherClassName).asSubclass(MetadataFetcher.class);
					Constructor<? extends MetadataFetcher> ctor = metadataFetcherClass.getConstructor(DocIndexer.class);
					metadataFetcher = ctor.newInstance(this);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		}
		return metadataFetcher;
	}

	public ComplexFieldProperty getPropPunct() {
		return propPunct;
	}

	public ComplexFieldProperty getPropStartTag() {
		return propStartTag;
	}

	public ComplexFieldProperty getMainProperty() {
		return propMain;
	}

	public ComplexField getContentsField() {
		return contentsField;
	}

	/**
	 * Returns the current word in the content.
	 *
	 * This is the position the next word will be stored at.
	 *
	 * @return the current word position
	 */
	public int getWordPosition() {
		return propMain.lastValuePosition() + 1;
	}

	public ComplexFieldProperty addProperty(String propName,
			SensitivitySetting sensitivity) {
		return contentsField.addProperty(propName, sensitivity);
	}

	public void addMetadataField(String name, String value) {

		IndexStructure struct = indexer.getSearcher().getIndexStructure();
		struct.registerMetadataField(name);

		MetadataFieldDesc desc = struct.getMetadataFieldDesc(name);
		FieldType type = desc.getType();
		desc.addValue(value);

		FieldType shouldBeType = getMetadataFieldTypeFromIndexerProperties(name);
		if (type == FieldType.TEXT
				&& shouldBeType != FieldType.TEXT) {
			// indexer.properties overriding default type
			type = shouldBeType;
		}
		if (type != FieldType.NUMERIC) {
			currentLuceneDoc.add(new Field(name, value,
					luceneTypeFromIndexStructType(type)));
		}
		if (type == FieldType.NUMERIC || numericFields.contains(name)) {
			String numFieldName = name;
			if (type != FieldType.NUMERIC) {
				numFieldName += "Numeric";
			}
			// Index these fields as numeric too, for faster range queries
			// (we do both because fields sometimes aren't exclusively numeric)
			int n = 0;
			try {
				n = Integer.parseInt(value);
			} catch (NumberFormatException e) {
				// This just happens sometimes, e.g. given multiple years, or
				// descriptive text like "around 1900". OK to ignore.
			}
			IntField nf = new IntField(numFieldName, n, Store.YES);
			currentLuceneDoc.add(nf);
		}
	}

	// protected Index getMetadataIndexSetting(String name) {
	// boolean analyzed = getParameter(name + "_analyzed", true);
	// return analyzed ? indexAnalyzed : indexNotAnalyzed;
	// }

	public void endElement(String uri, String localName, String qName) {
		elementBuilder.setLength(0); // clear
		elementBuilder.append("</").append(qName).append(">");
		processContent(elementBuilder.toString());

		// Call any hooks associated with this element
		hookableHandler.endElement(uri, localName, qName);
	}

	public void processingInstruction(String target, String data) {
		elementBuilder.setLength(0); // clear
		elementBuilder.append("<?").append(target).append(" ").append(data)
				.append("?>");
		processContent(elementBuilder.toString());
	}

	public ElementHandler addHandler(String condition,
			boolean callHandlerForAllDescendants, ElementHandler handler) {
		hookableHandler.addHook(condition, handler,
				callHandlerForAllDescendants);
		return handler;
	}

	public ElementHandler addHandler(String condition, ElementHandler handler) {
		hookableHandler.addHook(condition, handler);
		return handler;
	}

	@Override
	public void index() throws IOException, InputFormatException {
		SAXParserFactory factory = SAXParserFactory.newInstance();
		factory.setNamespaceAware(true);
		SAXParser parser;
		try {
			parser = factory.newSAXParser();
		} catch (Exception e1) {
			// Unrecoverable error, throw runtime exception
			throw new RuntimeException(e1);
		}
		try {
			InputSource is = new InputSource(reader);
			XMLReader xmlReader = parser.getXMLReader();
			xmlReader.setEntityResolver(new DummyEntityResolver());
			xmlReader.setContentHandler(saxParseHandler);
			xmlReader.parse(is);
		} catch (IOException e) {
			throw e;
		} catch (SAXException e) {
			throw new InputFormatException(e);
		} catch (DocIndexer.MaxDocsReachedException e) {
			// OK; just stop indexing prematurely
		}

		if (nDocumentsSkipped > 0)
			System.err.println("Skipped " + nDocumentsSkipped
					+ " large documents");
	}

	public String describePosition() {
		return saxParseHandler.describePosition();
	}

	class SaxParseHandler extends DefaultHandler {
		/** to keep track of the position within the document */
		protected Locator locator;

		@Override
		public void setDocumentLocator(Locator locator) {
			this.locator = locator;
		}

		@Override
		public void characters(char[] buffer, int start, int length)
				throws SAXException {
			super.characters(buffer, start, length);
			DocIndexerXmlHandlers.this.characters(buffer, start, length);
		}

		@Override
		public void endElement(String uri, String localName, String qName)
				throws SAXException {
			super.endElement(uri, localName, qName);
			DocIndexerXmlHandlers.this.endElement(uri, localName, qName);
		}

		@Override
		public void startElement(String uri, String localName, String qName,
				Attributes attributes) throws SAXException {
			super.startElement(uri, localName, qName, attributes);
			DocIndexerXmlHandlers.this.startElement(uri, localName, qName,
					attributes);
		}

		@Override
		public void processingInstruction(String target, String data)
				throws SAXException {
			super.processingInstruction(target, data);
			DocIndexerXmlHandlers.this.processingInstruction(target, data);
		}

		@Override
		public void startPrefixMapping(String prefix, String uri)
				throws SAXException {
			super.startPrefixMapping(prefix, uri);
			DocIndexerXmlHandlers.this.startPrefixMapping(prefix, uri);
		}

		@Override
		public void endPrefixMapping(String prefix) throws SAXException {
			super.endPrefixMapping(prefix);
			DocIndexerXmlHandlers.this.endPrefixMapping(prefix);
		}

		public String describePosition() {
			return "line " + locator.getLineNumber() + ", position "
					+ locator.getColumnNumber();
		}

	}

}
