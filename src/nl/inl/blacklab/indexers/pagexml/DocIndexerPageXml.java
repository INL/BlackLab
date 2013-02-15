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
 * Index an ANW corpus XML file.
 *
 * The ANW corpus XML format was created with a trivial transform of the Sketch Engine input file,
 * making the ANW corpus data well-formed XML. Using XML is useful because it allows us to later
 * easily display it using XSLT.
 */
public class DocIndexerPageXml extends DocIndexerXml {
	private static final String CONTENTS_FIELD = Searcher.DEFAULT_CONTENTS_FIELD;

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

//	/**
//	 * Current named entity id. Only valid when insideNE == true.
//	 */
//	private int neGid = 0;

//	/**
//	 * Current word offset within named entity. Only valid when insideNE == true.
//	 */
//	private int neOffset = 0;

//	/**
//	 * Have we found a &lt;NE&gt; start tag but no &lt;/NE&gt; end tag yet? In other words:
//	 * "are we currently parsing a named entity?"
//	 */
//	private boolean insideNE = false;

	/**
	 * Have we added a start tag at the current token position yet?
	 * Used to make sure the starttag property stays in synch.
	 */
	private boolean startTagAdded;

	/**
	 * Have we added an end tag at the current token position yet?
	 * Used to make sure the endtag property stays in synch.
	 */
	private boolean endTagAdded;

	/**
	 * Are we parsing the &lt;header&gt; tag right now?
	 */
	private boolean inHeader = false;

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
		contentsField = new ComplexFieldImpl(CONTENTS_FIELD, desensitizeFilterAdder); // actual text;
																					// this property
																					// will contain
																					// the offset
																					// information
		contentsField.addAlternative("s"); // sensitive version of main value

		// Named entity fields (experimental version for IMPACT retrieval demonstrator)
		//---------------------

		// Named entity type. Used in retr. demonstrator to search for word as NE type,
		// but BlackLab now uses a different approach to this:
		// we just search for "element CONTAINING search string" or "search string WITHIN element".
		// So this extra field is no longer necessary for future applications
		// (starttag and endtag are enough) and will be removed when we update the retrieval
		//  demonstrator.
		contentsField.addProperty("ne"); // named entity type (NONE/PER/LOC/ORG)

		// Named entity global id (not used, but could be used for linking to a database or something)
		//contentsField.addProperty("negid");

		// Named entity offset (word number inside ne)
		// (not used, commented out)
		//contentsField.addProperty("neoffset");

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
		} else if (localName.equals("PcGts")) {
			endPageXML();
		} else if (localName.equals("Page")) {
			endPage();
		} else if (localName.equals("NE")) {
			neType = "none";

			contentsField.addPropertyValue("endtag", "ne", endTagAdded ? 0 : 1);
			endTagAdded = true;

//			neOffset = 0;
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
				// (we do both because yearFrom/yearTo aren't always clean numeric fields)
				NumericField nf = new NumericField(ComplexFieldUtil.fieldName(localName, null,
						"numeric"), Store.YES, true);
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
		if (localName.equals("PcGts")) {
			startPageXML(attributes);
		} else if (localName.equals("Page")) {
			startPage(attributes);
		} else if (localName.equals("Word")) {
			startWord(attributes);
		} else if (localName.equals("NE")) {
//			insideNE = true;
			getNamedEntityType(attributes);
//			getNamedEntityGid(attributes);
//			neOffset = 0; // word position inside the NE

			contentsField.addPropertyValue("starttag", "ne", startTagAdded ? 0 : 1);
			for (int i = 0; i < attributes.getLength(); i++) {
				// Index element attribute values
				String name = attributes.getLocalName(i);
				String value = attributes.getValue(i);
				contentsField.addPropertyValue("starttag", "@" + name.toLowerCase() + "__" + value.toLowerCase(), 0);
			}
			startTagAdded = true; // to keep the property in synch

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

//	private void getNamedEntityGid(Attributes attributes) {
//		String gid = attributes.getValue("gid");
//		neGid = 0; // GID of this NE
//		if (gid != null) {
//			try {
//				neGid = Integer.parseInt(gid);
//			} catch (NumberFormatException e) {
//				// too bad...
//				System.err.println("Error parsing NE gid: " + gid);
//			}
//		}
//	}

	private void startPageXML(Attributes attributes) {
		startCaptureContent();
	}

	private void endPageXML() {
		try {
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

	/**
	 * New <doc> tag found. Start a new document in Lucene.
	 *
	 * @param attributes
	 */
	private void startPage(Attributes attributes) {
		// Initialise these, so we don't go out of synch because these contain old values
		startTagAdded = false;
		endTagAdded = false;

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
			if (startTagAdded || endTagAdded) {
				// Start and/or end tag(s) were found after the last token. They were
				// added to the "next token", so in order to maintain synch, we have to
				// add dummy values for all the other properties now.
				contentsField.addStartChar(getContentPosition());
				contentsField.addEndChar(getContentPosition());
				contentsField.addValue("");
				contentsField.addPropertyValue("ne", neType);
				if (!startTagAdded)
					contentsField.addPropertyValue("starttag", "");
				startTagAdded = false; // reset for next time
				if (!endTagAdded)
					contentsField.addPropertyValue("endtag", "");
				endTagAdded = false; // reset for next time
			}

			indexer.getListener().documentDone(currentDocumentName);

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
				Store.YES, true).setIntValue(contentId));

		// Store the different properties of the complex contents field that were gathered in
		// lists while parsing.
		contentsField.addToLuceneDoc(currentLuceneDoc);

		// Add contents field (case-insensitive tokens) to forward index
		int fiidContents = indexer.addToForwardIndex(CONTENTS_FIELD, contentsField.getPropertyValues(""));

		currentLuceneDoc.add(new NumericField(ComplexFieldUtil.fieldName(CONTENTS_FIELD, "fiid"),
				Store.YES, true).setIntValue(fiidContents));
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
		//contentsField.addPropertyValue("negid", neGid + "");
		//contentsField.addPropertyValue("neoffset", neOffset + "");

		// Keep track of NE start and end tags
		if (!startTagAdded) {
			// No start tags found; add empty token to keep the property in synch
			contentsField.addPropertyValue("starttag", ""); //insideNE && neOffset == 0 ? "ne" : "");
		}
		startTagAdded = false; // reset for next token positon
		if (!endTagAdded) {
			// No end tags found; add empty token to keep the property in synch
			contentsField.addPropertyValue("endtag", "");
		}
		endTagAdded = false; // reset for next token positon

		characterContent = null;

		// Report progress regularly but not too often
		wordsDone++;
		if (wordsDone >= 5000) {
			reportCharsProcessed();
			reportTokensProcessed(wordsDone);
			wordsDone = 0;
		}

//		if (insideNE)
//			neOffset++; // if we're inside an NE, keep track of the word position in this NE
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
