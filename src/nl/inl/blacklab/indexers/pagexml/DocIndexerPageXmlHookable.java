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
package nl.inl.blacklab.indexers.pagexml;

import java.io.Reader;
import java.util.regex.Pattern;

import nl.inl.blacklab.index.DocIndexerXmlHookable;
import nl.inl.blacklab.index.HookableSaxHandler.ContentCapturingHandler;
import nl.inl.blacklab.index.HookableSaxHandler.HookHandler;
import nl.inl.blacklab.index.Indexer;
import nl.inl.blacklab.index.complex.ComplexField;
import nl.inl.blacklab.index.complex.ComplexFieldImpl;
import nl.inl.blacklab.index.complex.ComplexFieldProperty.SensitivitySetting;
import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.search.Searcher;
import nl.inl.util.ExUtil;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.Field.TermVector;
import org.apache.lucene.document.NumericField;
import org.xml.sax.Attributes;

/**
 * Index a PageXML (OCR'ed text) file.
 */
public class DocIndexerPageXmlHookable extends DocIndexerXmlHookable {

	/** The Lucene Document we're currently constructing (corresponds to the document we're indexing) */
	Document currentLuceneDoc;

	/** Name of the document currently being indexed */
	String currentDocumentName;

	/** Complex field where different aspects (word form, named entity status, etc.) of the main
	 * content of the document are captured for indexing. */
	ComplexField contentsField;

	/** Number of words processed (for reporting progress) */
	int wordsDone;

	public DocIndexerPageXmlHookable(Indexer indexer, String fileName, Reader reader) {
		super(indexer, fileName, reader);

		// Define the properties that make up our complex field
		String fieldName = Searcher.DEFAULT_CONTENTS_FIELD_NAME;
		String mainPropName = ComplexFieldUtil.getDefaultMainPropName();
		contentsField = new ComplexFieldImpl(fieldName, mainPropName, SensitivitySetting.CASE_AND_DIACRITICS_SEPARATE);
		contentsField.addProperty(ComplexFieldUtil.START_TAG_PROP_NAME, SensitivitySetting.ONLY_INSENSITIVE); // start tag positions (just NE tags for now)
		contentsField.addProperty(ComplexFieldUtil.END_TAG_PROP_NAME, SensitivitySetting.ONLY_INSENSITIVE); // end tag positions (just NE tags for now)

		// Root element
		addHook("//PcGts", new PcGtsHandler());

		// Metadata elements in the header
		addHook("//header/*", new MetadataHandler());

		// Page element
		addHook("//Page", new PageHandler());

		// Words
		addHook("//Word", new WordHandler(), true); // true because we want to be called for the character content of child elements

		// Named entities
		addHook("//NE", new NeHandler());

	}

	/** A new document was started. */
	public void startDocument() {
		startCaptureContent(contentsField.getName());
	}

	/** The current document ended. */
	public void endDocument() {
		// Finish storing the document in the document store (parts of it may already have been
		// written because we write in chunks to save memory), retrieve the content id, and store
		// that in Lucene.
		int contentId = storeCapturedContent();
		currentLuceneDoc.add(new NumericField(ComplexFieldUtil.contentIdField(contentsField.getName()),
				Store.YES, true).setIntValue(contentId));

		// Store the different properties of the complex contents field that were gathered in
		// lists while parsing.
		contentsField.addToLuceneDoc(currentLuceneDoc);

		// Add words property (case-sensitive tokens) to forward index and add id to Lucene doc
		String wordPropFieldName = ComplexFieldUtil.propertyField(contentsField.getName(), contentsField.getMainProperty().getName());
		int fiidContents = indexer.addToForwardIndex(wordPropFieldName, contentsField.getMainProperty().getValues());
		currentLuceneDoc.add(new NumericField(ComplexFieldUtil.forwardIndexIdField(wordPropFieldName),
				Store.YES, true).setIntValue(fiidContents));

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

		// Reset contents field for next document
		contentsField.clear();

		// Stop if required
		if (!indexer.continueIndexing())
			throw new MaxDocsReachedException();
	}

	/** Handle &lt;Word&gt; tags (word tokens). */
	class WordHandler extends ContentCapturingHandler {

		/** Open tag: save start character position */
		@Override
		public void startElement(String uri, String localName, String qName, Attributes attributes) {
			if (localName.equals("Word")) { // Not one of the child elements of Word?
				super.startElement(uri, localName, qName, attributes);
				contentsField.addStartChar(getContentPosition());
			}
		}

		/** Punctuation and/or whitespace at the end of the token */
		Pattern junkAtEnd = Pattern.compile("[\\p{P}\\s]+$");

		/** Punctuation and/or whitespace at the start of the token */
		Pattern junkAtStart = Pattern.compile("^[\\p{P}\\s]+");

		/** Close tag: save end character position, add token to contents field and report progress. */
		@Override
		public void endElement(String uri, String localName, String qName) {
			super.endElement(uri, localName, qName);
			if (localName.equals("Word")) { // Not one of the child elements of Word?
				contentsField.addEndChar(getContentPosition());
				String word = getElementContent();
				word = junkAtEnd.matcher(word).replaceAll("");
				word = junkAtStart.matcher(word).replaceAll("");
				contentsField.addValue(word);

				// Report progress regularly but not too often
				wordsDone++;
				if (wordsDone >= 5000) {
					reportCharsProcessed();
					reportTokensProcessed(wordsDone);
					wordsDone = 0;
				}
			}
		}
	}

	/** Handle PageXML root element &lt;PcGts&gt;. */
	class PcGtsHandler extends HookHandler {

		/** Open tag: start indexing this document */
		@Override
		public void startElement(String uri, String localName, String qName, Attributes attributes) {
			startDocument();
		}

		/** Open tag: end indexing the document */
		@Override
		public void endElement(String uri, String localName, String qName) {
			endDocument();
		}

	}

	/** Handle &lt;Page&gt; elements (containing the annotated text). */
	class PageHandler extends HookHandler {

		/** Open tag: initialize Lucene doc, add attributes as metadata,
		 *  save document name, report starting this document */
		@Override
		public void startElement(String uri, String localName, String qName, Attributes attributes) {
			currentLuceneDoc = new Document();
			currentLuceneDoc.add(new Field("fromInputFile", fileName, Store.YES, indexNotAnalyzed,
					TermVector.NO));

			// Store attribute values from the tag as fields
			for (int i = 0; i < attributes.getLength(); i++) {
				String attName = attributes.getLocalName(i);
				String value = attributes.getValue(i);
				currentLuceneDoc.add(new Field(attName, value, Store.YES, indexAnalyzed,
						TermVector.WITH_POSITIONS_OFFSETS));
			}

			currentDocumentName = attributes.getValue("id");
			if (currentDocumentName == null)
				currentDocumentName = "?";

			indexer.getListener().documentStarted(currentDocumentName);
		}

		/**
		 * Close tag: make sure all the properties have an equal number of values
		 * and report that we're done with the document.
		 */
		@Override
		public void endElement(String uri, String localName, String qName) {
			try {
				int lastStartTagPos = contentsField.getProperty(ComplexFieldUtil.START_TAG_PROP_NAME).lastValuePosition();
				int lastEndTagPos = contentsField.getProperty(ComplexFieldUtil.START_TAG_PROP_NAME).lastValuePosition();
				int currentPos = contentsField.getMainProperty().lastValuePosition();
				boolean startTagsAhead = lastStartTagPos > currentPos;
				boolean endTagsAhead = lastEndTagPos > currentPos;
				if (startTagsAhead || endTagsAhead) {
					// Start and/or end tag(s) were found after the last token. They were
					// added to the "next token", so in order to maintain synch, we have to
					// add dummy values for all the other properties now.
					contentsField.addStartChar(getContentPosition());
					contentsField.addEndChar(getContentPosition());
					contentsField.addValue("");
					if (!startTagsAhead)
						contentsField.addPropertyValue(ComplexFieldUtil.START_TAG_PROP_NAME, "");
					if (!endTagsAhead)
						contentsField.addPropertyValue(ComplexFieldUtil.END_TAG_PROP_NAME, "");
				}

				indexer.getListener().documentDone(currentDocumentName);

			} catch (Exception e) {
				throw ExUtil.wrapRuntimeException(e);
			}
		}
	}

	/** Handle &lt;NE&gt; (named entity) tags. */
	class NeHandler extends HookHandler {

		/** Open tag: store the start tag location and the attribute values */
		@Override
		public void startElement(String uri, String localName, String qName, Attributes attributes) {
			int lastStartTagPos = contentsField.getProperty(ComplexFieldUtil.START_TAG_PROP_NAME).lastValuePosition();
			int currentPos = contentsField.getMainProperty().lastValuePosition();
			int posIncrement = currentPos - lastStartTagPos + 1;
			contentsField.addPropertyValue(ComplexFieldUtil.START_TAG_PROP_NAME, "ne", posIncrement);
			for (int i = 0; i < attributes.getLength(); i++) {
				// Index element attribute values
				String name = attributes.getLocalName(i);
				String value = attributes.getValue(i);
				contentsField.addPropertyValue(ComplexFieldUtil.START_TAG_PROP_NAME, "@" + name.toLowerCase() + "__" + value.toLowerCase(), 0);
			}
		}

		/** Close tag: store the end tag location */
		@Override
		public void endElement(String uri, String localName, String qName) {
			int lastEndTagPos = contentsField.getProperty(ComplexFieldUtil.END_TAG_PROP_NAME).lastValuePosition();
			int currentPos = contentsField.getMainProperty().lastValuePosition();
			int posIncrement = currentPos - lastEndTagPos + 1;
			contentsField.addPropertyValue(ComplexFieldUtil.END_TAG_PROP_NAME, "ne", posIncrement);
		}
	}

	/** Handle all tags under the &lt;header&gt; tag (document metadata). */
	class MetadataHandler extends ContentCapturingHandler {

		/** Close tag: store the value of this metadata field */
		@Override
		public void endElement(String uri, String localName, String qName) {
			super.endElement(uri, localName, qName);

			// Header element ended; index the element with the character content captured
			// (this is stuff like title, yearFrom, yearTo, etc.)
			String value = getElementContent().trim();
			currentLuceneDoc.add(new Field(localName, value, Store.YES, indexAnalyzed,
					TermVector.NO));
			if (localName.equals("yearFrom") || localName.equals("yearTo")) {
				// Index these fields as numeric too, for faster range queries
				// (we do both because yearFrom/yearTo aren't always clean numeric fields)
				NumericField nf = new NumericField(localName + "Numeric", Store.YES, true);
				int n = 0;
				try {
					n = Integer.parseInt(value);
				} catch (NumberFormatException e) {
					// This just happens sometimes, e.g. given multiple years, or
					// descriptive text like "around 1900". OK to ignore.
				}
				nf.setIntValue(n);
				currentLuceneDoc.add(nf);
			}
			value = null;
		}
	}
}
