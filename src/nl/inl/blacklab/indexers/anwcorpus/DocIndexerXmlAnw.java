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
package nl.inl.blacklab.indexers.anwcorpus;

import java.io.Reader;
import java.util.HashSet;
import java.util.Set;

import nl.inl.blacklab.filter.RemoveAllAccentsFilter;
import nl.inl.blacklab.index.DocIndexerXml;
import nl.inl.blacklab.index.Indexer;
import nl.inl.blacklab.index.complex.ComplexField;
import nl.inl.blacklab.index.complex.ComplexFieldImpl;
import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.index.complex.TokenFilterAdder;
import nl.inl.util.ExUtil;

import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.Field.TermVector;
import org.apache.lucene.document.NumericField;
import org.apache.lucene.util.Version;
import org.xml.sax.Attributes;

/**
 * Index an ANW corpus XML file.
 *
 * The ANW corpus XML format was created with a trivial transform of the Sketch Engine input file,
 * making the ANW corpus data well-formed XML. Using XML is useful because it allows us to later
 * easily display it using XSLT.
 */
public class DocIndexerXmlAnw extends DocIndexerXml {
	private static final String CONTENTS_FIELD = "contents";

	private String currentElementText = null;

	private boolean inWord = false;

	private boolean inPunctuation = false;

	private Document currentLuceneDoc;

	private String currentDocumentName;

	private ComplexField contentsField;

	/**
	 * Number of words processed.
	 */
	private int wordsDone = 0;

	private Set<String> indexSensitiveAndInsensitiveFields = new HashSet<String>();

	public DocIndexerXmlAnw(Indexer indexer, String fileName, Reader reader) {
		super(indexer, fileName, reader);

		// Adds a lower case filter to the property
		TokenFilterAdder lowerCaseFilterAdder = new TokenFilterAdder() {
			@Override
			public TokenStream addFilters(TokenStream input) {
				return new LowerCaseFilter(Version.LUCENE_36, input);
			}
		};

		// Adds lower case and accents filters to the property
		TokenFilterAdder desensitizeFilterAdder = new TokenFilterAdder() {
			@Override
			public TokenStream addFilters(TokenStream input) {
				return new RemoveAllAccentsFilter(new LowerCaseFilter(Version.LUCENE_36, input));
			}
		};

		// Define the properties that make up our complex field
		contentsField = new ComplexFieldImpl("contents", desensitizeFilterAdder); // actual text;
																					// this property
																					// will contain
																					// the offset
																					// information
		contentsField.addAlternative("s"); // sensitive version of main value
		contentsField.addProperty("pos", lowerCaseFilterAdder); // part of speech
		contentsField.addProperty("class"); // word class ("simple" version of part of speech)
		contentsField.addProperty("hw", desensitizeFilterAdder); // headword
		contentsField.addPropertyAlternative("hw", "s"); // sensitive version of above

		addIndexSensitiveAndInsensitiveField("bronentitel");
		addIndexSensitiveAndInsensitiveField("auteur");
	}

	private void addIndexSensitiveAndInsensitiveField(String fieldName) {
		indexSensitiveAndInsensitiveFields.add(fieldName);
	}

	@Override
	public void characters(char[] ch, int start, int length) {
		if (inWord || inPunctuation) {
			String w = new String(ch, start, length);
			if (currentElementText == null)
				currentElementText = w;
			else
				currentElementText += w;
		}
		super.characters(ch, start, length);
	}

	@Override
	public void endElement(String uri, String localName, String qName) {
		if (localName.equals("doc")) {
			// Document end; caught before the call to super() because we need to calculate document
			// length correctly
			// documentLength = getContentPosition() - documentStartOffset;
			endDoc();
		}

		super.endElement(uri, localName, qName);
		if (localName.equals("w")) {
			endWord();
		} else if (localName.equals("pu")) {
			endPunctuation();
		} else if (localName.equals("doc")) {
			// we've done this already; see above
		} else if (localName.equals("docs") || localName.equals("s") || localName.equals("g")) {
			// ignore
		} else {
			throw new RuntimeException("Unknown end tag: " + localName + " at "
					+ describePosition());
		}
	}

	@Override
	public void startElement(String uri, String localName, String qName, Attributes attributes) {
		if (localName.equals("doc")) {
			startDoc(attributes);
		} else if (localName.equals("w")) {
			startWord(attributes);
		} else if (localName.equals("pu")) {
			startPunctuation();
		} else if (localName.equals("docs") || localName.equals("s") || localName.equals("g")) {
			// ignore
		} else {
			throw new RuntimeException("Unknown start tag: " + localName + ", attr: " + attributes
					+ " at " + describePosition());
		}
		super.startElement(uri, localName, qName, attributes);
	}

	/**
	 * New <doc> tag found. Start a new document in Lucene.
	 *
	 * @param attributes
	 */
	private void startDoc(Attributes attributes) {
		startCaptureContent();
		currentLuceneDoc = new Document();
		currentLuceneDoc.add(new Field("fromInputFile", fileName, Store.YES, indexNotAnalyzed,
				TermVector.NO));

		// Store attribute values from the <doc> tag as fields
		for (int i = 0; i < attributes.getLength(); i++) {
			String attName = attributes.getLocalName(i);
			String value = attributes.getValue(i);

			currentLuceneDoc.add(new Field(attName, value, Store.YES, indexAnalyzed,
					TermVector.WITH_POSITIONS_OFFSETS));
			if (indexSensitiveAndInsensitiveFields.contains(attName)) {
				// Also index case-/accent-sensitively
				currentLuceneDoc.add(new Field(ComplexFieldUtil.fieldName(attName, null, "s"),
						value, Store.NO, indexAnalyzed, TermVector.WITH_POSITIONS));
			}
		}

		reportDocumentStarted(attributes);
	}

	private String getAttValue(Attributes attr, String name) {
		return getAttValue(attr, name, "?");
	}

	private String getAttValue(Attributes attr, String name, String defVal) {
		String rv = attr.getValue(name);
		if (rv == null)
			return defVal;
		return rv;
	}

	private void reportDocumentStarted(Attributes attributes) {
		String subcorpus = getAttValue(attributes, "subcorpus");
		String id = getAttValue(attributes, "id");
		String bronentitel = getAttValue(attributes, "bronentitel");
		String auteur = getAttValue(attributes, "auteur", getAttValue(attributes, "auteurwebtekst"));
		currentDocumentName = subcorpus + " " + id + " (\"" + bronentitel + "\", " + auteur + ")";
		indexer.getListener().documentStarted(currentDocumentName);
	}

	/**
	 * End <doc> tag found. Store the content and add the document to the index.
	 *
	 * @param attributes
	 */
	private void endDoc() {
		try {
			indexer.getListener().documentDone(currentDocumentName);

			addContentToLuceneDoc();
			if (!skippingCurrentDocument)
				indexer.add(currentLuceneDoc);
			reportCharsProcessed();
			reportTokensProcessed(wordsDone);
			wordsDone = 0;

			contentsField.clear();

			if (!indexer.continueIndexing())
				throw new MaxDocsReachedException();
		} catch (MaxDocsReachedException e) {
			// This is okay; just rethrow it
			throw e;
		} catch (Exception e) {
			throw ExUtil.wrapRuntimeException(e);
		}
	}

	private void addContentToLuceneDoc() {
		// Finish storing the document in the document store (parts of it may already have been
		// written because we write in chunks to save memory), retrieve the content id, and store
		// that in Lucene.
		int contentId = storeCapturedContent();
		// TODO: "cid" is not really a property here, but a subfield of contents with a single
		// value. Different naming scheme?
		currentLuceneDoc.add(new NumericField(ComplexFieldUtil.fieldName("contents", "cid"),
				Store.YES, false).setIntValue(contentId));

		// Store the different properties of the complex contents field that were gathered in
		// lists while parsing.
		contentsField.addToLuceneDoc(currentLuceneDoc);

		// Add contents field (case-insensitive tokens) to forward index
		int forwardId = indexer.addToForwardIndex(CONTENTS_FIELD, contentsField.getPropertyValues(""));
		currentLuceneDoc.add(new NumericField(ComplexFieldUtil.fieldName(CONTENTS_FIELD, "fiid"),
				Store.YES, false).setIntValue(forwardId));
	}

	private void startWord(Attributes attributes) {
		inWord = true;
		String lemma = attributes.getValue("l");
		contentsField.addPropertyValue("hw", lemma.substring(0, lemma.length() - 2));
		contentsField.addPropertyValue("pos", attributes.getValue("p"));
		contentsField.addPropertyValue("class", lemma.substring(lemma.length() - 1));
		contentsField.addStartChar(getContentPosition());
	}

	private void endWord() {
		inWord = false;
		contentsField.addEndChar(getContentPosition());
		contentsField.addValue(currentElementText);

		currentElementText = null;

		// Report progress regularly but not too often
		wordsDone++;
		if (wordsDone >= 5000) {
			reportCharsProcessed();
			reportTokensProcessed(wordsDone);
			wordsDone = 0;
		}
	}

	private void startPunctuation() {
		inPunctuation = true;
	}

	private void endPunctuation() {
		inPunctuation = false;
		currentElementText = null;
	}

}
