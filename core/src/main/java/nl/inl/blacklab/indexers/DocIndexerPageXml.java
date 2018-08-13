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

import nl.inl.blacklab.index.DocIndexerXmlHandlers;
import nl.inl.blacklab.index.DocWriter;
import nl.inl.blacklab.index.HookableSaxHandler.ElementHandler;
import nl.inl.util.StringUtil;

/**
 * Index a PageXML (OCR'ed text) file.
 */
public class DocIndexerPageXml extends DocIndexerXmlHandlers {

    public static String getDisplayName() {
        return "PageXML, an OCR file format";
    }

    public static String getDescription() {
        return "";
    }

    public DocIndexerPageXml(DocWriter indexer, String fileName, Reader reader) {
        super(indexer, fileName, reader);
        
        registerContentsField();

        addNumericFields(Arrays.asList("yearFrom", "yearTo"));

        // Document element
        addHandler("/PcGts", new DocumentElementHandler());

        // Page element: store attributes as metadata fields
        addHandler("/PcGts/Page", new MetadataAttributesHandler());

        // Metadata elements in the header
        addHandler("/PcGts/Page/header/*", new MetadataElementHandler());

        // Clear character content after header, so it doesn't mess with punctuation
        addHandler("/PcGts/Page/header", new ElementHandler() {
            @Override
            public void endElement(String uri, String localName, String qName) {
                consumeCharacterContent();
            }
        });

        // Word elements: index as main contents
        addHandler("//Word", new DefaultWordHandler() {
            @Override
            protected String getWord() {
                // In PageXML, punctuation and/or whitespace may be part of the word token.
                // Strip it off before indexing the word.
                // (TODO: instead of stripping it off, better to add it to punctuation index..?)
                return StringUtil.trimWhitespaceAndPunctuation(super.getWord());
            }
        });

        // Named entity tags: index as tags in the content
        addHandler("//NE", new InlineTagHandler());

    }

}
