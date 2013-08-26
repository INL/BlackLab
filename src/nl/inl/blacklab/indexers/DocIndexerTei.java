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
package nl.inl.blacklab.indexers;

import java.io.Reader;

import nl.inl.blacklab.index.DocIndexerXmlHandlers;
import nl.inl.blacklab.index.HookableSaxHandler.ElementHandler;
import nl.inl.blacklab.index.Indexer;
import nl.inl.blacklab.index.complex.ComplexFieldProperty;
import nl.inl.util.StringUtil;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.Field.TermVector;
import org.xml.sax.Attributes;

/**
 * Index a TEI P4/P5 file.
 * For information about TEI, see http://www.tei-c.org/
 */
public class DocIndexerTei extends DocIndexerXmlHandlers {

	/** If true, we should capture metadata fields (interpGrp/interp) */
	boolean captureMetadata = false;

	/** Value of the type attribute of the interpGrp we're in (or null) */
	String interpGrpType;

	public DocIndexerTei(Indexer indexer, String fileName, Reader reader) {
		super(indexer, fileName, reader);

		// Get handles to the default properties (the main one & punct)
		final ComplexFieldProperty propMain = getMainProperty();
		final ComplexFieldProperty propPunct = getPropPunct();

		// Add some extra properties
		final boolean hasLemma = getParameter("hasLemma", true);
		final boolean hasType  = getParameter("hasType", true);
		final ComplexFieldProperty propLemma = hasLemma ? addProperty("lemma") : null;
		final ComplexFieldProperty propPartOfSpeech = hasType ? addProperty("pos") : null;

		// Doc element: the individual documents to index
		// Note that we add handlers for both TEI and TEI.2, to
		// handle both TEI P5 and P4 files.
		DocumentElementHandler documentElementHandler = new DocumentElementHandler() {

			@Override
			public void endElement(String uri, String localName, String qName) {

				// Combine levels 1 & 2 of author and title field for easier
				// searching and displaying
				combineAuthorAndTitleFields();

				super.endElement(uri, localName, qName);
			}

		};
		addHandler("TEI", documentElementHandler);
		addHandler("TEI.2", documentElementHandler);

		// Body element: clear character content at the beginning
		final ElementHandler body = addHandler("body", new ElementHandler() {
			@Override
			public void startElement(String uri, String localName, String qName,
					Attributes attributes) {
				consumeCharacterContent(); // clear it to capture punctuation and words
			}

			@Override
			public void endElement(String uri, String localName, String qName) {

				// Before ending the document, add the final bit of punctuation.
				propPunct.addValue(StringUtil.normalizeWhitespace(consumeCharacterContent()));

				super.endElement(uri, localName, qName);
			}


		});

		// listBibl element: keep track of id attribute
		addHandler("listBibl", new ElementHandler() {

			@Override
			public void startElement(String uri, String localName, String qName,
					Attributes attributes) {
				consumeCharacterContent(); // clear it to capture punctuation and words
				String listBiblId = attributes.getValue("id");
				String listBiblIdToCapture = getParameter("listBiblIdToCapture", "inlMetadata"); // TODO: remove INL-specific stuff
				captureMetadata = listBiblId != null && listBiblId.equals(listBiblIdToCapture);
			}

			@Override
			public void endElement(String uri, String localName, String qName) {
				captureMetadata = false;
			}
		});

		// interpGrp element: metadata category
		addHandler("interpGrp", new ElementHandler() {
			@Override
			public void startElement(String uri, String localName, String qName,
					Attributes attributes) {
				consumeCharacterContent(); // clear it to capture punctuation and words
				interpGrpType = attributes.getValue("type");
				if (interpGrpType == null)
					interpGrpType = "";
			}

			@Override
			public void endElement(String uri, String localName, String qName) {
				interpGrpType = null;
			}
		});

		// interp element: metadata value
		addHandler("interp", new ElementHandler() {
			@Override
			public void startElement(String uri, String localName, String qName,
					Attributes attributes) {
				if (!captureMetadata || interpGrpType == null)
					return;
				String value = attributes.getValue("value");
				if (value == null)
					value = "";
				addMetadataField(interpGrpType, value);
			}

		});

		// Word elements: index as main contents
		addHandler("w", new WordHandlerBase() {

			@Override
			public void startElement(String uri, String localName, String qName,
					Attributes attributes) {
				if (!body.insideElement())
					return;
				super.startElement(uri, localName, qName, attributes);

				// Determine headword and part of speech from the attributes
				if (hasLemma) {
					String lemma = attributes.getValue("lemma");
					if (lemma == null) {
						lemma = "";
					}
					propLemma.addValue(lemma);
				}
				if (hasType) {
					String pos = attributes.getValue("type");
					if (pos == null)
						pos = "?";
					propPartOfSpeech.addValue(pos);
				}

				// Add punctuation
				propPunct.addValue(StringUtil.normalizeWhitespace(consumeCharacterContent()));
			}

			@Override
			public void endElement(String uri, String localName, String qName) {
				if (!body.insideElement())
					return;
				super.endElement(uri, localName, qName);
				propMain.addValue(consumeCharacterContent());
			}

		});

		// Sentence tags: index as tags in the content
		addHandler("s", new InlineTagHandler() {

			@Override
			public void startElement(String uri, String localName, String qName,
					Attributes attributes) {
				if (body.insideElement())
					super.startElement(uri, localName, qName, attributes);
			}

			@Override
			public void endElement(String uri, String localName, String qName) {
				if (body.insideElement())
					super.endElement(uri, localName, qName);
			}

		});

	}

	void combineAuthorAndTitleFields() {
		// Make author field, which is authorLevel1 or authorLevel2 if the first is empty
		// Also make authorCombined, which is an indexed field combining the two levels (for searching).
		Document myLuceneDoc = getCurrentLuceneDoc();
		String author = myLuceneDoc.get("authorLevel1");
		String authorLevel2 = myLuceneDoc.get("authorLevel2");
		String authorCombined = author + " " + authorLevel2;
		if (author == null || author.isEmpty()) {
			author = authorLevel2;
		}
		if (author == null)
			author = "";
		myLuceneDoc.add(new Field("author", author, Store.YES, Index.ANALYZED_NO_NORMS, TermVector.WITH_POSITIONS_OFFSETS));
		myLuceneDoc.add(new Field("authorCombined", authorCombined, Store.NO, Index.ANALYZED_NO_NORMS, TermVector.NO));

		// Make title field, which is titleLevel1 or titleLevel2 if the first is empty
		// Also make titleCombined, which is an indexed field combining the two levels (for searching).
		String title = myLuceneDoc.get("titleLevel1");
		String titleLevel2 = myLuceneDoc.get("titleLevel2");
		String titleCombined = title + " " + titleLevel2;
		if (title == null || title.isEmpty()) {
			title = titleLevel2;
		}
		if (title == null)
			title = "";
		myLuceneDoc.add(new Field("title", title, Store.YES, Index.ANALYZED_NO_NORMS, TermVector.WITH_POSITIONS_OFFSETS));
		myLuceneDoc.add(new Field("titleCombined", titleCombined, Store.NO, Index.ANALYZED_NO_NORMS, TermVector.NO));
	}


}
