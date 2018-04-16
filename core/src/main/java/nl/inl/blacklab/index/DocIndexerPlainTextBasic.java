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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Constructor;
import java.util.Set;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.IntField;

import nl.inl.blacklab.index.complex.ComplexField;
import nl.inl.blacklab.index.complex.ComplexFieldProperty;
import nl.inl.blacklab.index.complex.ComplexFieldProperty.SensitivitySetting;
import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.search.Searcher;
import nl.inl.blacklab.search.indexstructure.IndexStructure;
import nl.inl.blacklab.search.indexstructure.MetadataFieldDesc;
import nl.inl.blacklab.search.indexstructure.MetadataFieldDesc.UnknownCondition;
import nl.inl.util.ExUtil;

/**
 * Simple example indexer for plain text files. Reads a line, chops it into
 * words to index and keeps track of word positions.
 */
public class DocIndexerPlainTextBasic extends DocIndexerAbstract {

	/**
	 * Complex field where different aspects (word form, named entity status,
	 * etc.) of the main content of the document are captured for indexing.
	 */
	ComplexField contentsField;

	/** The main property (usually "word") */
	ComplexFieldProperty propMain;

	/** The punctuation property */
	ComplexFieldProperty propPunct;

	/**
	 * Our external metadata fetcher (if any), responsible for looking up the
	 * metadata and adding it to the Lucene document.
	 */
	MetadataFetcher metadataFetcher;

	@SuppressWarnings("deprecation")
    public DocIndexerPlainTextBasic(Indexer indexer, String fileName, Reader reader) {
    	super(indexer, fileName, reader);

    	// Define the properties that make up our complex field
    	String mainPropName = ComplexFieldUtil.getDefaultMainPropName();
    	contentsField = new ComplexField(Searcher.DEFAULT_CONTENTS_FIELD_NAME, mainPropName, getSensitivitySetting(mainPropName), false);
    	propMain = contentsField.getMainProperty();
        String propName = ComplexFieldUtil.PUNCTUATION_PROP_NAME;
    	propPunct = contentsField.addProperty(propName, getSensitivitySetting(propName), false);
    	IndexStructure indexStructure = indexer.getSearcher().getIndexStructure();
    	indexStructure.registerComplexField(contentsField.getName(), propMain.getName());

    	// If the indexmetadata file specified a list of properties that shouldn't get a forward
    	// index,
    	// make the new complex field aware of this.
    	Set<String> noForwardIndexProps = indexStructure.getComplexFieldDesc(Searcher.DEFAULT_CONTENTS_FIELD_NAME).getNoForwardIndexProps();
    	contentsField.setNoForwardIndexProps(noForwardIndexProps);
    }

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
			@SuppressWarnings("deprecation")
            String metadataFetcherClassName = getParameter("metadataFetcherClass");
			if (metadataFetcherClassName != null) {
				try {
					Class<? extends MetadataFetcher> metadataFetcherClass = Class.forName(metadataFetcherClassName)
							.asSubclass(MetadataFetcher.class);
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

	public ComplexFieldProperty addProperty(String propName, SensitivitySetting sensitivity) {
		return contentsField.addProperty(propName, sensitivity);
	}

	@Override
	public void index() throws IOException, MalformedInputFileException {
		BufferedReader r = new BufferedReader(reader);
		boolean firstWord = true;

		// Start a new Lucene document
		currentLuceneDoc = new Document();
		currentLuceneDoc.add(new Field("fromInputFile", documentName, indexer.getMetadataFieldType(false)));
		addMetadataFieldsFromParameters();
		indexer.getListener().documentStarted(documentName);

		while (true) {
			// For each line, split on whitespace and index each word.
			String line = r.readLine();
			if (line == null)
				break;
			String[] words = line.trim().split("\\s+");
			for (int i = 0; i < words.length; i++) {
				// Handle space and punctuation between words. Instead of always using a hardcoded
				// space,
				// you would want to use smarter tokenization and actually capture the space and
				// punctuation between words (right now the punctuation becomes part of the word,
				// because
				// we naively split on whitespace).
				String punctuation = "";
				if (!firstWord) {
					punctuation = " ";
					processContent(punctuation);
				}
				firstWord = false;
				propPunct.addValue(punctuation);

				// Handle the word itself, including character positions.
				contentsField.addStartChar(getCharacterPosition());
				processContent(words[i]); // add word to content store
				contentsField.addEndChar(getCharacterPosition());
				propMain.addValue(words[i]); // add word to index

				// Report progress regularly but not too often
				wordsDone++;
				if (wordsDone != 0 && wordsDone % 5000 == 0) {
					reportCharsProcessed();
					reportTokensProcessed();
				}
			}

			// Make sure all the properties have an equal number of values.
			// See what property has the highest position
			// (in practice, only starttags and endtags should be able to have
			// a position one higher than the rest)
			int lastValuePos = 0;
			for (ComplexFieldProperty prop: contentsField.getProperties()) {
				if (prop.lastValuePosition() > lastValuePos)
					lastValuePos = prop.lastValuePosition();
			}

			// Make sure we always have one more token than the number of
			// words, so there's room for any tags after the last word, and we
			// know we should always skip the last token when matching.
			if (propMain.lastValuePosition() == lastValuePos)
				lastValuePos++;

			// Add empty values to all lagging properties
			for (ComplexFieldProperty prop: contentsField.getProperties()) {
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
			// positions for the dummy token still make (some) sense)
			int contentId = storeCapturedContent();
			currentLuceneDoc.add(new IntField(ComplexFieldUtil.contentIdField(contentsField.getName()), contentId, Store.YES));

			// Store the different properties of the complex contents field that
			// were gathered in lists while parsing.
			contentsField.addToLuceneDoc(currentLuceneDoc);

			// Add all properties to forward index
			for (ComplexFieldProperty prop: contentsField.getProperties()) {
				if (!prop.hasForwardIndex())
					continue;

				// Add property (case-sensitive tokens) to forward index and add
				// id to Lucene doc
				String propName = prop.getName();
				String fieldName = ComplexFieldUtil.propertyField(contentsField.getName(), propName);
				int fiid = indexer.addToForwardIndex(fieldName, prop);
				currentLuceneDoc.add(new IntField(ComplexFieldUtil.forwardIndexIdField(fieldName), fiid, Store.YES));
			}

			// If there's an external metadata fetcher, call it now so it can
			// add the metadata for this document and (optionally) store the
			// metadata document in the content store (and the corresponding id in the
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
				switch (cond) {
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
			reportTokensProcessed();

			indexer.getListener().documentDone(documentName);

			// Reset contents field for next document
			contentsField.clear(true);
			currentLuceneDoc = null;

			// Stop if required
			if (!indexer.continueIndexing())
				throw new MaxDocsReachedException();

		}

		if (nDocumentsSkipped > 0)
			System.err.println("Skipped " + nDocumentsSkipped + " large documents");
	}

}
