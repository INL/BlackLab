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
package nl.inl.blacklab.example;

import java.io.Reader;

import nl.inl.blacklab.filter.RemoveAllAccentsFilter;
import nl.inl.blacklab.index.DocIndexerXml;
import nl.inl.blacklab.index.Indexer;
import nl.inl.blacklab.index.complex.ComplexField;
import nl.inl.blacklab.index.complex.ComplexFieldImpl;
import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.index.complex.TokenFilterAdder;
import nl.inl.blacklab.search.Searcher;
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
 * Index example XML from a Reader.
 *
 * Indexes lemmatized, PoS-tagged content.
 *
 * The XML format used is very basic: <code>
 * &lt;doc&gt;
 *    &lt;w l='lemma' p='pos' &gt;word&lt;/w&gt;
 *    &lt;w l='lemma' p='pos' &gt;word&lt;/w&gt;
 *    &lt;w l='lemma' p='pos' &gt;word&lt;/w&gt;
 *    ...
 * &lt;/doc&gt;
 * </code>
 */
public class DocIndexerXmlExample extends DocIndexerXml {

	static final String CONTENTS_FIELD = Searcher.DEFAULT_CONTENTS_FIELD_NAME;

	private static final String MAIN_PROPERTY = ComplexFieldUtil.getDefaultMainPropName();

	/**
	 * Text content for the element we're currently parsing
	 */
	private String currentElementText = null;

	/**
	 * Are we inside a &lt;w&gt; tag?
	 */
	private boolean inWord = false;

	/**
	 * The Lucene document we're building
	 */
	private Document currentLuceneDoc;

	/**
	 * Name of the current document (e.g. filename)
	 */
	private String currentDocumentName;

	/**
	 * The complex field used to store all information associated with the text. In our case, the
	 * word forms, the headwords and the part of speech information.
	 */
	private ComplexField contentsField;

	/**
	 * Build a DocIndexer for our example XML format. We build a new DocIndexer per indexed file.
	 *
	 * @param indexer
	 *            the indexer object
	 * @param fileName
	 *            name of the file we're indexing
	 * @param reader
	 *            the file contents
	 */
	public DocIndexerXmlExample(Indexer indexer, String fileName, Reader reader) {
		super(indexer, fileName, reader);

		// FilterAdders allow us to specify how the different properties are indexed.

		// Adds a lower case filter to the property, for case-insensitivity.
		TokenFilterAdder lowerCase = new TokenFilterAdder() {
			@Override
			public TokenStream addFilters(TokenStream input) {
				return new LowerCaseFilter(Version.LUCENE_36, input);
			}
		};

		// Adds lower case and accents filters to the property
		TokenFilterAdder desensitize = new TokenFilterAdder() {
			@Override
			public TokenStream addFilters(TokenStream input) {
				return new RemoveAllAccentsFilter(new LowerCaseFilter(Version.LUCENE_36, input));
			}
		};

		// Define the properties that make up our complex field

		contentsField = // main field: actual text; this property will contain the character offsets
		new ComplexFieldImpl(CONTENTS_FIELD, desensitize);
		contentsField.addAlternative("s"); // case-sensitive version of main value
		contentsField.addProperty("pos", lowerCase); // part of speech
		contentsField.addProperty("hw", desensitize); // headword
		contentsField.addPropertyAlternative("hw", "s"); // case-sensitive version of above
	}

	/**
	 * Text node in the XML. If we're inside word tags, collect the text.
	 */
	@Override
	public void characters(char[] ch, int start, int length) {
		if (inWord) {
			String w = new String(ch, start, length);
			if (currentElementText == null)
				currentElementText = w;
			else
				currentElementText += w;
		}
		super.characters(ch, start, length);
	}

	/**
	 * Element end tag found.
	 */
	@Override
	public void endElement(String uri, String localName, String qName) {

		if (localName.equals("doc")) {
			// Document end; caught before the call to super() because we need
			// to calculate document length correctly.
			endDoc();
		}

		super.endElement(uri, localName, qName);
		if (localName.equals("w")) {
			// End of a word.
			endWord();
		} else if (localName.equals("doc")) {
			// We've done this already; see above
		} else {
			// Huh?
			throw new RuntimeException("Unknown end tag: " + localName + " at "
					+ describePosition());
		}
	}

	/**
	 * Element start tag found.
	 */
	@Override
	public void startElement(String uri, String localName, String qName, Attributes attributes) {
		if (localName.equals("doc")) {
			// Start of a document.
			startDoc(attributes);
		} else if (localName.equals("w")) {
			// Start of a word element.
			startWord(attributes);
		} else {
			// Huh?
			throw new RuntimeException("Unknown start tag: " + localName + ", attr: " + attributes
					+ " at " + describePosition());
		}
		super.startElement(uri, localName, qName, attributes);
	}

	/**
	 * New &lt;doc&gt; tag found. Start a new document in Lucene.
	 */
	private void startDoc(Attributes attributes) {

		// Start storing the document in the content store
		startCaptureContent(CONTENTS_FIELD);

		// Create a new Lucene document
		currentLuceneDoc = new Document();
		currentLuceneDoc.add(new Field("fromInputFile", fileName, Store.YES, indexNotAnalyzed,
				TermVector.NO));

		// Store attribute values from the <doc> tag as fields
		for (int i = 0; i < attributes.getLength(); i++) {
			String attName = attributes.getLocalName(i);
			String value = attributes.getValue(i);

			currentLuceneDoc.add(new Field(attName, value, Store.YES, indexAnalyzed,
					TermVector.WITH_POSITIONS_OFFSETS));
		}

		// Report indexing progress
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

	/**
	 * Report indexing progress to the IndexListener, so we can show feedback to the user.
	 */
	private void reportDocumentStarted(Attributes attributes) {
		String subcorpus = getAttValue(attributes, "subcorpus");
		String id = getAttValue(attributes, "id");
		String bronentitel = getAttValue(attributes, "bronentitel");
		String auteur = getAttValue(attributes, "auteur", getAttValue(attributes, "auteurwebtekst"));
		currentDocumentName = subcorpus + " " + id + " (\"" + bronentitel + "\", " + auteur + ")";
		indexer.getListener().documentStarted(currentDocumentName);
	}

	/**
	 * End &lt;doc&gt; tag found. Store the content and add the document to the index.
	 */
	private void endDoc() {
		try {
			// Report the end of the document to the IndexListener
			indexer.getListener().documentDone(currentDocumentName);

			// Store the captured document content in the content store
			// and add the contents of the complex field to the Lucene document
			addContentToLuceneDoc();

			// Add the Lucene document to the index
			indexer.add(currentLuceneDoc);

			// Report character progress
			reportCharsProcessed();

			// Reset the contents field for the next document
			contentsField.clear();

			// Should we continue or are we done?
			if (!indexer.continueIndexing())
				throw new MaxDocsReachedException();

		} catch (MaxDocsReachedException e) {

			// We've reached our maximum number of documents we'd like to index.
			// This is okay; just rethrow it.
			throw e;

		} catch (Exception e) {

			// Some error occurred.
			throw ExUtil.wrapRuntimeException(e);

		}
	}

	/**
	 * Store the document in the content store and add the contents of the complex field to the
	 * Lucene doc.
	 */
	private void addContentToLuceneDoc() {
		// Finish storing the document in the document store (parts of it may already have been
		// written because we write in chunks to save memory), retrieve the content id, and store
		// that in Lucene.
		int contentId = storeCapturedContent();
		currentLuceneDoc.add(new NumericField(ComplexFieldUtil.contentIdField(CONTENTS_FIELD),
				Store.YES, true).setIntValue(contentId));

		// Store the different properties of the complex contents field that were gathered in
		// lists while parsing.
		contentsField.addToLuceneDoc(currentLuceneDoc);

		// Add contents field (case-sensitive tokens) to forward index
		String mainPropName = ComplexFieldUtil.propertyField(CONTENTS_FIELD, MAIN_PROPERTY);
		int forwardId = indexer.addToForwardIndex(mainPropName, contentsField.getMainProperty());
		currentLuceneDoc.add(new NumericField(ComplexFieldUtil.forwardIndexIdField(mainPropName),
				Store.YES, true).setIntValue(forwardId));
	}

	/**
	 * Found the start of a word. Add the values of the attributes (headword, part of speech) to the
	 * complex field. Add the character position to the complex field.
	 */
	private void startWord(Attributes attributes) {
		inWord = true;
		contentsField.addPropertyValue("hw", attributes.getValue("l"));
		contentsField.addPropertyValue("pos", attributes.getValue("p"));
		contentsField.addStartChar(getContentPosition());
	}

	/**
	 * Found the end of a word. Add the word form and the character position to the complex field.
	 */
	private void endWord() {
		inWord = false;
		contentsField.addEndChar(getContentPosition());
		contentsField.addValue(currentElementText);

		// Reset element text for the next word.
		currentElementText = null;
	}

}
