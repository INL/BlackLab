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
public class DocIndexerPageXml extends DocIndexerXml {
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
	 * Name of the document currently being indexed
	 */
	private String currentDocumentName;

	/**
	 * Complex field where different aspects (word form, named entity status, etc.) of the main
	 * content of the document are captured for indexing.
	 */
	private ComplexField contentsField;

	/**
	 * Number of words processed.
	 */
	private int wordsDone;

	/**
	 * Current named entity type. Only valid when insideNE == true.
	 */
	private String neType = "NONE";

	/**
	 * Current named entity id. Only valid when insideNE == true.
	 */
	private int neGid = 0;

	/**
	 * Current word offset within named entity. Only valid when insideNE == true.
	 */
	private int neOffset = 0;

	/**
	 * Have we found a &lt;NE&gt; start tag but no &lt;/NE&gt; end tag yet? In other words:
	 * "are we currently parsing a named entity?"
	 */
	private boolean insideNE = false;

	/**
	 * Are we parsing the &lt;header&gt; tag right now?
	 */
	private boolean inHeader = false;

	/**
	 * Was a &lt;/NE&gt; close tag just found? Used to index it (at the first token position AFTER
	 * the close tag!)
	 */
	private boolean neJustClosed = false;

	public DocIndexerPageXml(Indexer indexer, String fileName, Reader reader) {
		super(indexer, fileName, reader);

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
		contentsField.addProperty("ne"); // named entity type (NONE/PER/LOC/ORG)
		contentsField.addProperty("negid"); // named entity gid
		contentsField.addProperty("neoffset"); // named entity offset (word number inside ne)
		contentsField.addProperty("starttag"); // start tag positions (just NE tags for now)
		contentsField.addProperty("endtag"); // end tag positions (just NE tags for now)
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
		if (localName.equals("Word")) {
			endWord();
		} else if (localName.equals("Page")) {
			endPage();
		} else if (localName.equals("NE")) {
			neType = "none";
			insideNE = false;
			neJustClosed = true;
			neOffset = 0;
		} else if (localName.equals("header")) {
			inHeader = false;
			captureCharacterContent = false;
		} else if (inHeader) {
			// TODO: Field and NumericField can be re-used between documents. Might be faster/more
			// mem. efficient.

			// Header element ended; index the element with the character content captured
			// (this is stuff like title, yearFrom, yearTo, etc.)
			characterContent = characterContent.trim();
			currentLuceneDoc.add(new Field(localName, characterContent, Store.YES, indexAnalyzed,
					TermVector.NO));
			if (localName.equals("yearFrom") || localName.equals("yearTo")) {
				// Index these fields as numeric too, for faster range queries
				NumericField nf = new NumericField(ComplexFieldUtil.fieldName(localName, null,
						"numeric"));
				nf.setIntValue(Integer.parseInt(characterContent));
				currentLuceneDoc.add(nf);
			}
			characterContent = null;
		}
	}

	/**
	 * Called when an start tag (element open tag) is encountered in the XML.
	 */
	@Override
	public void startElement(String uri, String localName, String qName, Attributes attributes) {
		if (localName.equals("Page")) {
			startPage(attributes);
		} else if (localName.equals("Word")) {
			startWord(attributes);
		} else if (localName.equals("NE")) {
			insideNE = true;
			getNamedEntityType(attributes);
			getNamedEntityGid(attributes);
			neOffset = 0; // word position inside the NE
		} else if (localName.equals("header")) {
			inHeader = true;
			captureCharacterContent = true;
		}
		super.startElement(uri, localName, qName, attributes);
	}

	private void getNamedEntityType(Attributes attributes) {
		neType = attributes.getValue("type").replaceAll("^\\-", "").toLowerCase();
		if (neType.equals("pers"))
			neType = "per";
		if (!neType.matches("^(loc|per|org)$"))
			System.err.println("Unknown ne type: " + neType);
	}

	private void getNamedEntityGid(Attributes attributes) {
		String gid = attributes.getValue("gid");
		neGid = 0; // GID of this NE
		if (gid != null) {
			try {
				neGid = Integer.parseInt(gid);
			} catch (NumberFormatException e) {
				// too bad...
				System.err.println("Error parsing NE gid: " + gid);
			}
		}
	}

	/**
	 * New <doc> tag found. Start a new document in Lucene.
	 *
	 * @param attributes
	 */
	private void startPage(Attributes attributes) {
		startCaptureContent();
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
		currentDocumentName = getAttValue(attributes, "id");
		indexer.getListener().documentStarted(currentDocumentName);
	}

	/**
	 * End <doc> tag found. Store the content and add the document to the index.
	 *
	 * @param attributes
	 */
	private void endPage() {
		try {
			indexer.getListener().documentDone(currentDocumentName);

			if (neJustClosed) {
				// We didn't record the closing of the NE-tag yet (because there is no
				// next word, we're at the end of the document). Record it now.
				contentsField.addStartChar(getContentPosition());
				contentsField.addEndChar(getContentPosition());
				contentsField.addValue("");
				contentsField.addPropertyValue("ne", neType);
				contentsField.addPropertyValue("negid", neGid + "");
				contentsField.addPropertyValue("neoffset", neOffset + "");
				contentsField.addPropertyValue("starttag", insideNE && neOffset == 0 ? "ne" : "");
				contentsField.addPropertyValue("endtag", neJustClosed ? "ne" : "");
			}

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
		currentLuceneDoc.add(new NumericField(ComplexFieldUtil.fieldName("contents", "cid"),
				Store.YES, false).setIntValue(contentId));

		// Store the different properties of the complex contents field that were gathered in
		// lists while parsing.
		contentsField.addToLuceneDoc(currentLuceneDoc);

		// Add contents field (case-insensitive tokens) to forward index
		int forwardId = indexer.addToForwardIndex(contentsField.getPropertyValues(""));
		currentLuceneDoc.add(new NumericField(ComplexFieldUtil.fieldName("contents", "fiid"),
				Store.YES, false).setIntValue(forwardId));
	}

	private void startWord(Attributes attributes) {
		captureCharacterContent = true;
		contentsField.addStartChar(getContentPosition());
	}

	private void endWord() {
		captureCharacterContent = false;
		contentsField.addEndChar(getContentPosition());
		if (characterContent == null)
			characterContent = "";
		contentsField.addValue(preprocessWord(characterContent));
		contentsField.addPropertyValue("ne", neType);
		contentsField.addPropertyValue("negid", neGid + "");
		contentsField.addPropertyValue("neoffset", neOffset + "");

		// Keep track of NE start and end tags
		contentsField.addPropertyValue("starttag", insideNE && neOffset == 0 ? "ne" : "");
		contentsField.addPropertyValue("endtag", neJustClosed ? "ne" : "");
		if (neJustClosed)
			neJustClosed = false;

		characterContent = null;

		// Report progress regularly but not too often
		wordsDone++;
		if (wordsDone >= 5000) {
			reportCharsProcessed();
			reportTokensProcessed(wordsDone);
			wordsDone = 0;
		}

		if (insideNE)
			neOffset++; // if we're inside an NE, keep track of the word position in this NE
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
