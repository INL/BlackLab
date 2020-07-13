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
package nl.inl.blacklab.mocks;

import java.io.Reader;

import org.xml.sax.Attributes;

import nl.inl.blacklab.index.DocIndexerXmlHandlers;
import nl.inl.blacklab.index.DocWriter;
import nl.inl.blacklab.index.annotated.AnnotationWriter;
import nl.inl.blacklab.index.annotated.AnnotationWriter.SensitivitySetting;

/**
 * Example indexer. See Example for the file format.
 */
public class DocIndexerExample extends DocIndexerXmlHandlers {
    public DocIndexerExample(DocWriter indexer, String fileName, Reader reader) {
        super(indexer, fileName, reader);

        // Get handles to the default properties (the main one & punct)
        final AnnotationWriter propMain = mainAnnotation();
        final AnnotationWriter propPunct = punctAnnotation();

        // Add some extra properties
        final AnnotationWriter propLemma = addAnnotation("lemma", SensitivitySetting.SENSITIVE_AND_INSENSITIVE);
        final AnnotationWriter propPartOfSpeech = addAnnotation("pos", SensitivitySetting.ONLY_INSENSITIVE);
        
        registerContentsField();

        // Doc element: the individual documents to index
        addHandler("/doc", new DocumentElementHandler());

        // Word elements: index as main contents
        addHandler("w", new WordHandlerBase() {

            @Override
            public void startElement(String uri, String localName, String qName,
                    Attributes attributes) {
                super.startElement(uri, localName, qName, attributes);
                String lemma = attributes.getValue("l");
                if (lemma == null)
                    lemma = "";
                propLemma.addValue(lemma);
                String pos = attributes.getValue("p");
                if (pos == null)
                    pos = "";
                propPartOfSpeech.addValue(pos);
                propPunct.addValue(consumeCharacterContent());
            }

            @Override
            public void endElement(String uri, String localName, String qName) {
                super.endElement(uri, localName, qName);
                propMain.addValue(consumeCharacterContent());
            }
        });

        // Sentence and entity tags: index as inline tags (used in tests)
        addHandler("s", new InlineTagHandler());
        addHandler("entity", new InlineTagHandler());

    }

}
