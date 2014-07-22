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
import java.util.Arrays;
import java.util.List;

import nl.inl.blacklab.index.DocIndexerXmlHandlers;
import nl.inl.blacklab.index.HookableSaxHandler.ContentCapturingHandler;
import nl.inl.blacklab.index.HookableSaxHandler.ElementHandler;
import nl.inl.blacklab.index.Indexer;
import nl.inl.blacklab.index.complex.ComplexFieldProperty;

import org.xml.sax.Attributes;

/**
 * Index a FoLiA file.
 * For information about FoLiA, see http://proycon.github.io/folia/
 */
public class DocIndexerOpenSonar extends DocIndexerXmlHandlers {

	String wordform;

	String pos;

	String lemma;

	boolean lemPosProblemReported = false;

	public DocIndexerOpenSonar(Indexer indexer, String fileName, Reader reader) {
		super(indexer, fileName, reader);

		// Get handles to the default properties (the main one & punct)
		final ComplexFieldProperty propMain = getMainProperty();
		final ComplexFieldProperty propPunct = getPropPunct();

		// Add some extra properties
		final ComplexFieldProperty propLemma = addProperty("lemma");
		final ComplexFieldProperty propPartOfSpeech = addProperty("pos");

		// Doc element: the individual documents to index
		addHandler("/FoLiA", new DocumentElementHandler());

		// Word elements: index as main contents
		addHandler("w", new WordHandlerBase() {

			@Override
			public void startElement(String uri, String localName, String qName,
					Attributes attributes) {
				super.startElement(uri, localName, qName, attributes);
				wordform = "";
				pos = "";
				lemma = "";
			}

			@Override
			public void endElement(String uri, String localName, String qName) {
				super.endElement(uri, localName, qName);
				if (wordform.length() > 0) {
					propMain.addValue(wordform);
					propPartOfSpeech.addValue(pos);
					propLemma.addValue(lemma);
					if (pos.length() == 0 || lemma.length() == 0) {
						if (!lemPosProblemReported) {
							lemPosProblemReported = true;
							System.err.println(
								"Word without Pos (set=http://ilk.uvt.nl/folia/sets/frog-mbpos-cgn) and/or lemma (set=http://ilk.uvt.nl/folia/sets/frog-mblem-nl "
								+ "at " + describePosition());
						}
					}
					propPunct.addValue(" ");
				}
			}
		});

		// lemma element: contains lemma
		addHandler("lemma", new ElementHandler() {
			@Override
			public void startElement(String uri, String localName, String qName,
					Attributes attributes) {
				super.startElement(uri, localName, qName, attributes);
				String set = attributes.getValue("set");
				if (set != null && set.equals("http://ilk.uvt.nl/folia/sets/frog-mblem-nl")) {
					// Correct set attribute; use this lemma.
					lemma = attributes.getValue("class");
					if (lemma == null)
						lemma = "";
				}
			}
		});

		// pos element: contains part of speech
		addHandler("pos", new ElementHandler() {
			@Override
			public void startElement(String uri, String localName, String qName,
					Attributes attributes) {
				super.startElement(uri, localName, qName, attributes);
				String set = attributes.getValue("set");
				if (set != null && set.equals("http://ilk.uvt.nl/folia/sets/frog-mbpos-cgn")) {
					// Correct set attribute; use this pos.
					pos = attributes.getValue("class");
					if (pos == null)
						pos = "";
				}
			}
		});

		// t (token) element directly under w (word) element: contains the word form
		addHandler("w/t", new ContentCapturingHandler() {

			/** Tokens with a class attribute are (usually?) the original scanned token before correction,
			 *  so we skip them */
			boolean isOcr;

			@Override
			public void startElement(String uri, String localName, String qName,
					Attributes attributes) {
				super.startElement(uri, localName, qName, attributes);
				isOcr = attributes.getValue("class") != null;
			}

			@Override
			public void endElement(String uri, String localName, String qName) {
				super.endElement(uri, localName, qName);
				if (!isOcr)
					wordform = getElementContent();
			}
		});

		// Sentence tags: index as tags in the content
		addHandler("s", new InlineTagHandler());

		// Paragraph tags: index as tags in the content
		addHandler("p", new InlineTagHandler());

		// <event/> tags: index as tags in the content
		addHandler("event", new InlineTagHandler());

		// meta elements: metadata fields
		// [NOT USED FOR OPENSONAR..?]
		addHandler("meta", new ContentCapturingHandler() {


			private String metadataFieldName;

			/** Open tag: add metadata field */
			@Override
			public void startElement(String uri, String localName, String qName, Attributes attributes) {
				super.startElement(uri, localName, qName, attributes);
				metadataFieldName = attributes.getValue("id");
			}

			@Override
			public void endElement(String uri, String localName, String qName) {
				super.endElement(uri, localName, qName);
				String elementContent = getElementContent();
				addMetadataField(metadataFieldName, elementContent);
			}
		});
	}

	List<String> untokenizedFields = Arrays.asList("Country", "LicentieCode", "CollectionName");

	@Override
	public void addMetadataField(String name, String value) {

		// FIXME HACK: See if we need to substitute token-ending characters
		if (untokenizedFields.contains(name)) {
			// Yes; substitute token-ending characters for underscore in these fields!
			value = value.replaceAll("[\\s\\./]", "_");
		}

		super.addMetadataField(name, value);
	}

	public static void main(String[] args) {
		System.out.println("NL B".replaceAll("[\\s\\./]", "_"));
		System.out.println("NL/B".replaceAll("[\\s\\./]", "_"));
		System.out.println("a.b.c.d".replaceAll("[\\s\\./]", "_"));
	}

}
