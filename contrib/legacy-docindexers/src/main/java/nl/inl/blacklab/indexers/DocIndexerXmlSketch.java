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

import org.xml.sax.Attributes;

import nl.inl.blacklab.index.DocIndexerXmlHandlers;
import nl.inl.blacklab.index.DocWriter;
import nl.inl.blacklab.index.HookableSaxHandler.ContentCapturingHandler;
import nl.inl.blacklab.index.annotated.AnnotationWriter;

/**
 * Index a Sketch XML file.
 *
 * The Sketch XML format was created with a trivial transform of the Sketch
 * Engine word-per-line input file, converting the data to well-formed XML.
 * Using XML is useful because it allows us to later easily display it using
 * XSLT.
 *
 * For information about the original Sketch Engine word-per-line format, see
 * http://trac.sketchengine.co.uk/wiki/SkE/PrepareText .
 *
 * For the conversion tool, see nl.inl.blacklab.tools.SketchToXmlConverter.
 */
public class DocIndexerXmlSketch extends DocIndexerXmlHandlers {

    public static String getDisplayName() {
        return "Sketch Engine XML-from-WPL (deprecated)";
    }

    public static String getDescription() {
        return "Older way of indexing Sketch Engine files, after converting them to XML. " +
                "This is not necessary anymore, use sketch-wpl to index Sketch Engine WPL files directly.";
    }

    /** Captures the punctuation so we can add it to the next word. */
    StringBuilder punctuation = new StringBuilder();

    public DocIndexerXmlSketch(DocWriter indexer, String fileName, Reader reader) {
        super(indexer, fileName, reader);

        // Get handles to the default properties (the main one & punct)
        final AnnotationWriter propMain = mainAnnotation();
        final AnnotationWriter propPunct = punctAnnotation();

        // Add some extra properties
        final AnnotationWriter propLemma = addAnnotation("lemma");
        final AnnotationWriter propPartOfSpeech = addAnnotation("pos");
        final AnnotationWriter propWordClass = addAnnotation("class");

        registerContentsField();

        // Doc element: the individual documents to index (one or more per file)
        addHandler("/docs/doc", new DocumentElementHandler() {

            @Override
            public void startElement(String uri, String localName, String qName,
                    Attributes attributes) {
                super.startElement(uri, localName, qName, attributes);

                // Make sure the punctuation buffer is empty
                punctuation.setLength(0);
            }

            @Override
            public void endElement(String uri, String localName, String qName) {

                // Before ending the document, add the final bit of punctuation.
                propPunct.addValue(punctuation.toString());

                super.endElement(uri, localName, qName);
            }

        });

        // Word elements: index as main contents
        addHandler("//w", new WordHandlerBase() {

            @Override
            public void startElement(String uri, String localName, String qName,
                    Attributes attributes) {
                super.startElement(uri, localName, qName, attributes);

                // Determine lemma, word class and part of speech from the attributes
                String lemma = attributes.getValue("l");
                if (lemma == null || lemma.length() < 2) {
                    lemma = "???-?";
                }
                propLemma.addValue(lemma.substring(0, lemma.length() - 2));
                propWordClass.addValue(lemma.substring(lemma.length() - 1));
                String pos = attributes.getValue("p");
                if (pos == null)
                    pos = "?";
                propPartOfSpeech.addValue(pos);

                // Add punctuation value
                propPunct.addValue(punctuation.toString());
                punctuation.setLength(0); // reset for next word
                punctuation.append(' '); // will be deleted if we find a glue tag

                consumeCharacterContent(); // empty content buffer so we catch only element content (i.e., the word)
            }

            @Override
            public void endElement(String uri, String localName, String qName) {
                super.endElement(uri, localName, qName);
                propMain.addValue(consumeCharacterContent());
            }

        });

        // Punctuation elements: keep track of them so we can add them to the next word
        addHandler("//pu", new ContentCapturingHandler() {
            @Override
            public void endElement(String uri, String localName, String qName) {
                super.endElement(uri, localName, qName);

                // Capture punctuation and add space by default (space will be deleted
                // if we encounter a glue tag)
                punctuation.append(getElementContent()).append(' ');
            }
        });

        // Glue tag: delete last space from punctuation capturing buffer
        addHandler("//g", new ContentCapturingHandler() {
            @Override
            public void endElement(String uri, String localName, String qName) {
                super.endElement(uri, localName, qName);

                // If last character in punctuation buffer is a space, delete it
                if (punctuation.length() > 0 && punctuation.charAt(punctuation.length() - 1) == ' ')
                    punctuation.setLength(punctuation.length() - 1);
            }
        });

        // Sentence tags: index as tags in the content
        addHandler("//s", new InlineTagHandler());

    }

}
