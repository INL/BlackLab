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

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Constructor;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.IntField;
import org.apache.lucene.util.BytesRef;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import nl.inl.blacklab.index.HookableSaxHandler.ContentCapturingHandler;
import nl.inl.blacklab.index.HookableSaxHandler.ElementHandler;
import nl.inl.blacklab.index.annotated.AnnotatedFieldWriter;
import nl.inl.blacklab.index.annotated.AnnotationWriter;
import nl.inl.blacklab.index.annotated.AnnotationWriter.SensitivitySetting;
import nl.inl.blacklab.search.BlackLabException;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.IndexMetadataImpl;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.indexmetadata.UnknownCondition;
import nl.inl.util.ExUtil;
import nl.inl.util.StringUtil;

/**
 * Abstract base class for a DocIndexer processing XML files using the hookable
 * SAX parser.
 */
public abstract class DocIndexerXmlHandlers extends DocIndexerAbstract {

    /**
     * Max. length of captured character content. Should only be used for short
     * strings, such as a word, or the value of a metadata field.
     */
    private static final int MAX_CHARACTER_CONTENT_CAPTURE_LENGTH = 4000;

    private HookableSaxHandler hookableHandler = new HookableSaxHandler();

    private SaxParseHandler saxParseHandler = new SaxParseHandler();

    /**
     * What namespace prefix mappings have we encountered but not output in a start
     * tag yet? (used to make sure the stored XML contains all the required
     * mappings)
     */
    final private static Map<String, String> outputPrefixMapping = new HashMap<>();

    /**
     * Handle Document element. Starts a new Lucene document and adds the attributes
     * of this element (if any) as metadata fields.
     */
    public class DocumentElementHandler extends ElementHandler {

        /** Open tag: start indexing this document */
        @Override
        public void startElement(String uri, String localName, String qName,
                Attributes attributes) {
            startCaptureContent(contentsField.getName());

            currentLuceneDoc = new Document();
            // Store attribute values from the tag as metadata fields
            for (int i = 0; i < attributes.getLength(); i++) {
                addMetadataField(attributes.getLocalName(i),
                        attributes.getValue(i));
            }
            currentLuceneDoc.add(new Field("fromInputFile", documentName, indexer.getMetadataFieldType(false)));
            addMetadataFieldsFromParameters();
            indexer.getListener().documentStarted(documentName);
        }

        /** Open tag: end indexing the document */
        @Override
        public void endElement(String uri, String localName, String qName) {
            // Make sure all the properties have an equal number of values.
            // See what annotation has the highest position
            // (in practice, only starttags and endtags should be able to have
            // a position one higher than the rest)
            int lastValuePos = 0;
            for (AnnotationWriter prop: contentsField.getAnnotations()) {
                if (prop.lastValuePosition() > lastValuePos)
                    lastValuePos = prop.lastValuePosition();
            }

            // Make sure we always have one more token than the number of
            // words, so there's room for any tags after the last word, and we
            // know we should always skip the last token when matching.
            if (propMain.lastValuePosition() == lastValuePos)
                lastValuePos++;

            // Add empty values to all lagging properties
            for (AnnotationWriter prop: contentsField.getAnnotations()) {
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
            currentLuceneDoc.add(new IntField(AnnotatedFieldNameUtil
                    .contentIdField(contentsField.getName()), contentId,
                    Store.YES));

            // Store the different properties of the annotated contents field that
            // were gathered in
            // lists while parsing.
            contentsField.addToLuceneDoc(currentLuceneDoc);

            // Add all properties to forward index
            for (AnnotationWriter prop: contentsField.getAnnotations()) {
                if (!prop.hasForwardIndex())
                    continue;

                // Add annotation (case-sensitive tokens) to forward index and add
                // id to Lucene doc
                String propName = prop.getName();
                String fieldName = AnnotatedFieldNameUtil.annotationField(
                        contentsField.getName(), propName);
                int fiid = indexer.addToForwardIndex(prop);
                currentLuceneDoc
                        .add(new IntField(AnnotatedFieldNameUtil.forwardIndexIdField(fieldName), fiid, Store.YES));
            }

            // If there's an external metadata fetcher, call it now so it can
            // add the metadata for this document and (optionally) store the
            // metadata
            // document in the content store (and the corresponding id in the
            // Lucene doc)
            MetadataFetcher m = getMetadataFetcher();
            if (m != null) {
                m.addMetadata();
            }

            // See what metadatafields are missing or empty and add unknown value
            // if desired.
            IndexMetadataImpl indexMetadata = (IndexMetadataImpl) indexer.getSearcher().metadataWriter();
            for (MetadataField fd: indexMetadata.metadataFields()) {
                boolean missing = false, empty = false;
                String currentValue = currentLuceneDoc.get(fd.name());
                if (currentValue == null)
                    missing = true;
                else if (currentValue.length() == 0)
                    empty = true;
                UnknownCondition cond = fd.unknownCondition();
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
                    addMetadataField(fd.name(), fd.unknownValue());
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
    }

    /**
     * Stores metadata field with element name as name and element content as value.
     */
    public class MetadataElementHandler extends ContentCapturingHandler {
        /** Close tag: store the value of this metadata field */
        @Override
        public void endElement(String uri, String localName, String qName) {
            super.endElement(uri, localName, qName);

            // Header element ended; index the element with the character
            // content captured
            // (this is stuff like title, yearFrom, yearTo, etc.)
            addMetadataField(localName, getElementContent().trim());
        }
    }

    /** Add element attributes as metadata. */
    public class MetadataAttributesHandler extends ContentCapturingHandler {

        /** Open tag: add attributes as metadata */
        @Override
        public void startElement(String uri, String localName, String qName,
                Attributes attributes) {
            super.startElement(uri, localName, qName, attributes);

            // Store attribute values from the tag as fields
            for (int i = 0; i < attributes.getLength(); i++) {
                addMetadataField(attributes.getLocalName(i),
                        attributes.getValue(i));
            }
        }
    }

    /**
     * Add a metadatafield based on two attributes of an element, a name attribute
     * (giving the field name) and a value attribute (giving the field value).
     */
    public class MetadataNameValueAttributeHandler extends
            ContentCapturingHandler {

        private String nameAttribute;

        private String valueAttribute;

        public MetadataNameValueAttributeHandler(String nameAttribute,
                String valueAttribute) {
            this.nameAttribute = nameAttribute;
            this.valueAttribute = valueAttribute;
        }

        public MetadataNameValueAttributeHandler() {
            this("name", "value");
        }

        /** Open tag: add metadata field */
        @Override
        public void startElement(String uri, String localName, String qName,
                Attributes attributes) {
            super.startElement(uri, localName, qName, attributes);
            String name = attributes.getValue(nameAttribute);
            String value = attributes.getValue(valueAttribute);
            if (name != null && value != null)
                addMetadataField(name, value);
        }
    }

    List<Integer> openTagIndexes = new ArrayList<>();

    /** Handle tags. */
    public class InlineTagHandler extends ElementHandler {

        /** Open tag: store the start tag location and the attribute values */
        @Override
        public void startElement(String uri, String localName, String qName,
                Attributes attributes) {
            int lastStartTagPos = propStartTag.lastValuePosition();
            int currentPos = propMain.lastValuePosition() + 1;
            int posIncrement = currentPos - lastStartTagPos;
            propStartTag.addValue(localName, posIncrement);
            propStartTag.addPayload(null);
            int startTagIndex = propStartTag.getLastValueIndex();
            openTagIndexes.add(startTagIndex);
            for (int i = 0; i < attributes.getLength(); i++) {
                // Index element attribute values
                String name = attributes.getLocalName(i);
                String value = attributes.getValue(i);
                propStartTag.addValue("@" + name.toLowerCase() + "__" + value.toLowerCase(), 0);
                propStartTag.addPayload(null);
            }
        }

        /** Close tag: store the end tag location */
        @Override
        public void endElement(String uri, String localName, String qName) {
            int currentPos = propMain.lastValuePosition() + 1;

            // Add payload to start tag annotation indicating end position
            Integer openTagIndex = openTagIndexes.remove(openTagIndexes.size() - 1);
            byte[] payload = ByteBuffer.allocate(4).putInt(currentPos).array();
            propStartTag.setPayloadAtIndex(openTagIndex, new BytesRef(payload));
        }
    }

    /**
     * Base handler for word tags: adds start and end positions around the element.
     */
    public class WordHandlerBase extends ElementHandler {

        /** Open tag: save start character position */
        @Override
        public void startElement(String uri, String localName, String qName,
                Attributes attributes) {
            super.startElement(uri, localName, qName, attributes);
            contentsField.addStartChar(getCharacterPosition());
        }

        /**
         * Close tag: save end character position, add token to contents field and
         * report progress.
         */
        @Override
        public void endElement(String uri, String localName, String qName) {
            super.endElement(uri, localName, qName);
            contentsField.addEndChar(getCharacterPosition());

            // Report progress regularly but not too often
            wordsDone++;
            if (wordsDone != 0 && wordsDone % 5000 == 0) {
                reportCharsProcessed();
                reportTokensProcessed();
            }
        }
    }

    /** Handle &lt;Word&gt; tags (word tokens). */
    public class DefaultWordHandler extends WordHandlerBase {

        /** Open tag: save start character position */
        @Override
        public void startElement(String uri, String localName, String qName,
                Attributes attributes) {
            super.startElement(uri, localName, qName, attributes);
            propPunct.addValue(StringUtil
                    .normalizeWhitespace(consumeCharacterContent()));
        }

        /**
         * Close tag: save end character position, add token to contents field and
         * report progress.
         */
        @Override
        public void endElement(String uri, String localName, String qName) {
            super.endElement(uri, localName, qName);
            propMain.addValue(getWord());
        }

        protected String getWord() {
            return consumeCharacterContent();
        }

    }

    /** Handle &lt;Word&gt; tags (word tokens). */
    public class WordInAttributeHandler extends DefaultWordHandler {

        private String attName;

        protected String currentWord;

        public WordInAttributeHandler(String attName) {
            this.attName = attName;
        }

        /** Open tag: get word from attribute value */
        @Override
        public void startElement(String uri, String localName, String qName,
                Attributes attributes) {
            super.startElement(uri, localName, qName, attributes);
            currentWord = attributes.getValue(attName);
            if (currentWord == null)
                currentWord = "";
        }

        @Override
        protected String getWord() {
            return currentWord;
        }

    }

    /**
     * Encountered a prefix to namespace mapping; now in effect.
     *
     * @param prefix the prefix that is now in effect
     * @param uri the namespace the prefix refers to
     */
    public void startPrefixMapping(String prefix, String uri) {
        outputPrefixMapping.put(prefix, uri);
    }

    /**
     * A previously encountered namespace prefix mapping is no longer in effect.
     *
     * @param prefix the prefix that's no longer in effect.
     */
    public void endPrefixMapping(String prefix) {
        // System.out.println("END PREFIX MAPPING: " + prefix);
    }

    protected AnnotationWriter addProperty(String propName) {
        return addProperty(propName, false);
    }

    @SuppressWarnings("deprecation")
    protected AnnotationWriter addProperty(String propName, boolean includePayloads) {
        return contentsField.addAnnotation(propName, getSensitivitySetting(propName), includePayloads);
    }

    public AnnotationWriter addProperty(String propName, SensitivitySetting sensitivity) {
        return contentsField.addProperty(propName, sensitivity);
    }

    @SuppressWarnings("deprecation")
    public DocIndexerXmlHandlers(Indexer indexer, String fileName, Reader reader) {
        super(indexer, fileName, reader);

        // Define the properties that make up our annotated field
        String mainPropName = AnnotatedFieldNameUtil.getDefaultMainAnnotationName();
        contentsField = new AnnotatedFieldWriter(BlackLabIndex.DEFAULT_CONTENTS_FIELD_NAME, mainPropName,
                getSensitivitySetting(mainPropName), false);
        propMain = contentsField.getMainAnnotation();
        propPunct = addProperty(AnnotatedFieldNameUtil.PUNCTUATION_ANNOT_NAME);
        propStartTag = addProperty(AnnotatedFieldNameUtil.START_TAG_ANNOT_NAME, true); // start tag
        // positions
        propStartTag.setForwardIndex(false);
        IndexMetadataImpl indexMetadata = (IndexMetadataImpl) indexer.getSearcher().metadataWriter();
        AnnotatedField f = indexMetadata.registerAnnotatedField(contentsField);
        contentsField.setAnnotatedField(f);
    }

    /**
     * StringBuffer re-used for building start/end tags and processing instructions.
     */
    StringBuilder elementBuilder = new StringBuilder();

    public void startElement(String uri, String localName, String qName,
            Attributes attributes) {
        // Call any hooks associated with this element
        hookableHandler.startElement(uri, localName, qName, attributes);

        elementBuilder.setLength(0); // clear
        elementBuilder.append("<").append(qName);
        for (int i = 0; i < attributes.getLength(); i++) {
            String value = StringEscapeUtils.escapeXml10(attributes.getValue(i));
            elementBuilder.append(" ").append(attributes.getQName(i))
                    .append("=\"").append(value).append("\"");
        }
        // Append any namespace mapping not yet outputted
        if (!outputPrefixMapping.isEmpty()) {
            for (Map.Entry<String, String> e: outputPrefixMapping.entrySet()) {
                if (e.getKey().length() == 0)
                    elementBuilder.append(" xmlns=\"").append(e.getValue())
                            .append("\"");
                else
                    elementBuilder.append(" xmlns:").append(e.getKey())
                            .append("=\"").append(e.getValue()).append("\"");
            }
            outputPrefixMapping.clear(); // outputted all prefix mappings for
                                         // now
        }
        elementBuilder.append(">");
        processContent(elementBuilder.toString());
    }

    /**
     * Character content encountered in the XML document since the last call to
     * consumeCharacterContent().
     */
    StringBuilder characterContent = new StringBuilder();

    /**
     * Returns and resets the character content captured since the last call to this
     * method.
     *
     * @return the captured character content.
     */
    public String consumeCharacterContent() {
        String content = characterContent.toString();
        characterContent.setLength(0);
        return content;
    }

    public void characters(char[] buffer, int start, int length) {
        // Capture character content in string builder
        if (characterContent.length() < MAX_CHARACTER_CONTENT_CAPTURE_LENGTH)
            characterContent.append(buffer, start, length);

        String s = StringEscapeUtils.escapeXml10(new String(buffer, start, length));
        processContent(s);

        // Call any hooks associated with this element
        hookableHandler.characters(buffer, start, length);
    }

    /**
     * Annotated field where different aspects (word form, named entity status, etc.)
     * of the main content of the document are captured for indexing.
     */
    AnnotatedFieldWriter contentsField;

    /** The main annotation (usually "word") */
    AnnotationWriter propMain;

    /** The punctuation annotation */
    AnnotationWriter propPunct;

    /** The start tag annotation. Also contains tag length in payload. */
    AnnotationWriter propStartTag;

    /**
     * Our external metadata fetcher (if any), responsible for looking up the
     * metadata and adding it to the Lucene document.
     */
    MetadataFetcher metadataFetcher;

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
                    throw new BlackLabException(e);
                }
            }
        }
        return metadataFetcher;
    }

    public AnnotationWriter getPropPunct() {
        return propPunct;
    }

    public AnnotationWriter getPropStartTag() {
        return propStartTag;
    }

    public AnnotationWriter getMainProperty() {
        return propMain;
    }

    public AnnotatedFieldWriter getContentsField() {
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

    public void endElement(String uri, String localName, String qName) {
        elementBuilder.setLength(0); // clear
        elementBuilder.append("</").append(qName).append(">");
        processContent(elementBuilder.toString());

        // Call any hooks associated with this element
        hookableHandler.endElement(uri, localName, qName);
    }

    public void processingInstruction(String target, String data) {
        elementBuilder.setLength(0); // clear
        elementBuilder.append("<?").append(target).append(" ").append(data).append("?>");
        processContent(elementBuilder.toString());
    }

    public ElementHandler addHandler(String condition,
            boolean callHandlerForAllDescendants, ElementHandler handler) {
        hookableHandler.addHook(condition, handler, callHandlerForAllDescendants);
        return handler;
    }

    public ElementHandler addHandler(String condition, ElementHandler handler) {
        hookableHandler.addHook(condition, handler);
        return handler;
    }

    @Override
    public void index() throws IOException, MalformedInputFileException {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        SAXParser parser;
        try {
            parser = factory.newSAXParser();
        } catch (Exception e1) {
            // Unrecoverable error, throw runtime exception
            throw new BlackLabException(e1);
        }
        try {
            InputSource is = new InputSource(reader);
            XMLReader xmlReader = parser.getXMLReader();
            xmlReader.setEntityResolver(new DummyEntityResolver());
            xmlReader.setContentHandler(saxParseHandler);
            xmlReader.parse(is);
        } catch (SAXException e) {
            throw new MalformedInputFileException(e);
        } catch (DocIndexer.MaxDocsReachedException e) {
            // OK; just stop indexing prematurely
        }

        if (nDocumentsSkipped > 0)
            System.err.println("Skipped " + nDocumentsSkipped + " large documents");
    }

    public String describePosition() {
        return saxParseHandler.describePosition();
    }

    class SaxParseHandler extends DefaultHandler {
        /** to keep track of the position within the document */
        protected Locator locator;

        @Override
        public void setDocumentLocator(Locator locator) {
            this.locator = locator;
        }

        @Override
        public void characters(char[] buffer, int start, int length)
                throws SAXException {
            super.characters(buffer, start, length);
            DocIndexerXmlHandlers.this.characters(buffer, start, length);
        }

        @Override
        public void endElement(String uri, String localName, String qName)
                throws SAXException {
            super.endElement(uri, localName, qName);
            DocIndexerXmlHandlers.this.endElement(uri, localName, qName);
        }

        @Override
        public void startElement(String uri, String localName, String qName,
                Attributes attributes) throws SAXException {
            super.startElement(uri, localName, qName, attributes);
            DocIndexerXmlHandlers.this.startElement(uri, localName, qName,
                    attributes);
        }

        @Override
        public void processingInstruction(String target, String data)
                throws SAXException {
            super.processingInstruction(target, data);
            DocIndexerXmlHandlers.this.processingInstruction(target, data);
        }

        @Override
        public void startPrefixMapping(String prefix, String uri)
                throws SAXException {
            super.startPrefixMapping(prefix, uri);
            DocIndexerXmlHandlers.this.startPrefixMapping(prefix, uri);
        }

        @Override
        public void endPrefixMapping(String prefix) throws SAXException {
            super.endPrefixMapping(prefix);
            DocIndexerXmlHandlers.this.endPrefixMapping(prefix);
        }

        public String describePosition() {
            return "line " + locator.getLineNumber() + ", position " + locator.getColumnNumber();
        }

    }

}
