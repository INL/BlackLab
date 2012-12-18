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
package nl.inl.blacklab.indexers.alto;

import java.io.Reader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * Index an ALTO file.
 */
public class DocIndexerAlto extends DocIndexerXml {
	private static final String CONTENTS_FIELD = "contents";

	/** Pattern for getting DPO number and page number from image file name */
	private static Pattern pattDpoAndPage = Pattern.compile("^dpo_(\\d+)_(\\d+)_");

	/** Used to capture the content between some XML tags for indexing */
	private String characterContent = null;

	/**
	 * Should we capture character content for indexing as we encounter it? (i.e.
	 * "are we currently parsing between tags of interest to us?")
	 */
	private boolean captureCharacterContent = false;

	/**
	 * The Lucene Document we're currently constructing (corresponds to the document we're indexing)
	 */
	private Document currentLuceneDoc;

	/**
	 * Complex field where different aspects (word form, named entity status, etc.) of the main
	 * content of the document are captured for indexing.
	 */
	private ComplexField contentsField;

	/**
	 * Number of words processed.
	 */
	private int wordsDone = 0;

	/**
	 * Are we parsing the &lt;header&gt; tag right now?
	 */
	private boolean inHeader = false;

	private String imageFileName = "";

	private String documentTitle;

	private String pageNumber;

	private String author;

	private String date;

	public DocIndexerAlto(Indexer indexer, String fileName, Reader reader) {
		super(indexer, fileName, reader);

		// Adds lower case and accents filters to the property
		TokenFilterAdder desensitizeFilterAdder = new TokenFilterAdder() {
			@Override
			public TokenStream addFilters(TokenStream input) {
				return new RemoveAllAccentsFilter(new LowerCaseFilter(Version.LUCENE_36, input));
			}
		};

		// Define the properties that make up our complex field
		contentsField = new ComplexFieldImpl(CONTENTS_FIELD, desensitizeFilterAdder); // actual text;
																					// this property
																					// will contain
																					// the offset
																					// information
		contentsField.addAlternative("s"); // sensitive version of main value
	}

	/**
	 * Called when character content (not a tag) is encountered in the XML.
	 */
	@Override
	public void characters(char[] ch, int start, int length) {
		if (captureCharacterContent) {
			String w = new String(ch, start, length);
			if (characterContent == null)
				characterContent = w;
			else
				characterContent += w;
		}
		super.characters(ch, start, length);
	}

	/**
	 * Called when an end tag (element close tag) is encountered in the XML.
	 */
	@Override
	public void endElement(String uri, String localName, String qName) {
		super.endElement(uri, localName, qName);
		if (localName.equals("String")) {
			endWord();
		} else if (localName.equals("alto")) {
			endAlto();
		} else if (localName.equals("Page")) {
			endPage();
		} else if (localName.equals("Description")) {
			inHeader = false;
			captureCharacterContent = false;
		} else if (inHeader && localName.equals("fileName")) {
			// Store image file name
			characterContent = characterContent.trim();
			imageFileName = characterContent;
			Matcher m = pattDpoAndPage.matcher(imageFileName);
			String dpo;
			String page;
			if (m.find()) {
				dpo = m.group(1);
				page = m.group(2);
			} else {
				dpo = "?";
				page = "?";
				System.err.println("No DPO/page found: " + imageFileName);
			}
			documentTitle = AltoUtils.getTitleFromDpo(dpo);
			author = AltoUtils.getAuthorFromDpo(dpo);
			date = AltoUtils.getDateFromDpo(dpo);
			pageNumber = page;
		}

		if (inHeader) {
			characterContent = "";
		}
	}

	/**
	 * Called when an start tag (element open tag) is encountered in the XML.
	 */
	@Override
	public void startElement(String uri, String localName, String qName, Attributes attributes) {
		if (localName.equals("alto")) {
			startAlto(attributes);
		} else if (localName.equals("Page")) {
			startPage(attributes);
		} else if (localName.equals("String")) {
			startWord(attributes);
		} else if (localName.equals("Description")) {
			inHeader = true;
			captureCharacterContent = true;
			imageFileName = "";
			documentTitle = "";
			author = "";
			date = "";
			pageNumber = "";
		}
		super.startElement(uri, localName, qName, attributes);

		if (localName.equals("Page"))
			processContent("<imageFileName>" + imageFileName + "</imageFileName>");
	}

	/**
	 * New <Page> tag found. Start a new document in Lucene.
	 *
	 * @param attributes
	 */
	private void startPage(Attributes attributes) {
		// We've seen the metadata by now; store it in a new Lucene document
		currentLuceneDoc = new Document();
		currentLuceneDoc.add(new Field("fromInputFile", fileName, Store.YES, indexNotAnalyzed,
				TermVector.NO));
		currentLuceneDoc.add(new Field("imageFileName", imageFileName, Store.YES, indexAnalyzed,
				TermVector.NO));
		currentLuceneDoc.add(new Field("title", documentTitle, Store.YES, indexAnalyzed,
				TermVector.NO));
		currentLuceneDoc
				.add(new Field("page", pageNumber, Store.YES, indexAnalyzed, TermVector.NO));
		currentLuceneDoc.add(new Field("author", author, Store.YES, indexAnalyzed, TermVector.NO));
		currentLuceneDoc.add(new Field("year", date, Store.YES, indexAnalyzed, TermVector.NO));

	}

	/**
	 * New <alto> tag found. Start a new document in Lucene.
	 *
	 * @param attributes
	 */
	private void startAlto(Attributes attributes) {
		startCaptureContent();
		reportDocumentStarted(attributes);
	}

	// private String getAttValue(Attributes attr, String name)
	// {
	// return getAttValue(attr, name, "?");
	// }

	private String getAttValue(Attributes attr, String name, String defVal) {
		String rv = attr.getValue(name);
		if (rv == null)
			return defVal;
		return rv;
	}

	private void reportDocumentStarted(Attributes attributes) {
		// currentDocumentName = getAttValue(attributes, "ID");
		indexer.getListener().documentStarted(documentTitle);
	}

	/**
	 * End <Page> tag found.
	 *
	 * @param attributes
	 */
	private void endPage() {
		// [moved to endAlto]
	}

	/**
	 * End <alto> tag found. Store the content and add the document to the index.
	 *
	 * @param attributes
	 */
	private void endAlto() {
		try {
			indexer.getListener().documentDone(documentTitle);

			addContentToLuceneDoc();
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
		currentLuceneDoc.add(new NumericField(ComplexFieldUtil.fieldName(CONTENTS_FIELD, "cid"),
				Store.YES, false).setIntValue(contentId));

		// Store the different properties of the complex contents field that were gathered in
		// lists while parsing.
		contentsField.addToLuceneDoc(currentLuceneDoc);

		// Add contents field (case-insensitive tokens) to forward index
		int forwardId = indexer.addToForwardIndex(CONTENTS_FIELD, contentsField.getPropertyValues(""));
		currentLuceneDoc.add(new NumericField(CONTENTS_FIELD + "__fiid",
				Store.YES, false).setIntValue(forwardId));
	}

	private void startWord(Attributes attributes) {
		String word = getAttValue(attributes, "CONTENT", "");
		contentsField.addValue(word);
		contentsField.addStartChar(getContentPosition());
	}

	private void endWord() {
		contentsField.addEndChar(getContentPosition());

		// Report progress regularly but not too often
		wordsDone++;
		if (wordsDone >= 5000) {
			reportCharsProcessed();
			reportTokensProcessed(wordsDone);
			wordsDone = 0;
		}
	}

	static Pattern punctuationAtEnd = Pattern.compile("\\p{P}+$"); // remove non-word chars. Note:
																	// pattern is greedy, so will
																	// match as much as possible

	static Pattern punctuationAtStart = Pattern.compile("^\\p{P}+"); // remove non-word chars. Note:
																		// pattern is greedy, so
																		// will match as much as
																		// possible

	/**
	 * Words may still include leading/trailing whitespace and punctuation in this format. Remove
	 * that before indexing.
	 *
	 * @param word
	 *            unsanitized word
	 * @return sanitized word
	 */
	public static String preprocessWord(String word) {
		word = word.trim(); // remove leading/trailing whitespace before indexing
		word = punctuationAtEnd.matcher(word).replaceAll("");
		word = punctuationAtStart.matcher(word).replaceAll("");
		return word;
	}

}
