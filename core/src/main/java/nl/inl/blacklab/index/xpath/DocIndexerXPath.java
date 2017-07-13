package nl.inl.blacklab.index.xpath;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;

import com.ximpleware.AutoPilot;
import com.ximpleware.NavException;
import com.ximpleware.VTDGen;
import com.ximpleware.VTDNav;

import nl.inl.blacklab.index.DocIndexer;
import nl.inl.blacklab.index.complex.ComplexField;

public abstract class DocIndexerXPath extends DocIndexer {

    /** Our input document */
    private byte[] inputDocument;

    /** VTD parser (generator?) */
    private VTDGen vg;

    /** VTD navigator */
    private VTDNav nav;

    /** Our input format */
    private ConfigInputFormat config;

    private Map<String, ComplexField> complexFields = new HashMap<>();

    public DocIndexerXPath() {
        config = new ConfigInputFormat();
        configure(config);
    }

    protected abstract void configure(ConfigInputFormat config);

    @Override
    public void setDocument(File file, Charset charset) throws FileNotFoundException {
        try {
            inputDocument = FileUtils.readFileToByteArray(file);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setDocument(byte[] contents, Charset cs) {
        this.inputDocument = contents;
    }

    @Override
    public void setDocument(InputStream is, Charset cs) {
        try {
            this.inputDocument = IOUtils.toByteArray(is);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setDocument(Reader reader) {
        try {
            this.inputDocument = IOUtils.toString(reader).getBytes("utf-8");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Create AutoPilot and declare namespaces on it.
     * @return the AutoPilot
     */
    AutoPilot createAutoPilot() {
        AutoPilot ap = new AutoPilot(nav);
        if (config.isNamespaceAware()) {
            ap.declareXPathNameSpace("xml", "http://www.w3.org/XML/1998/namespace"); // builtin
            for (Entry<String, String> e: config.getNamespaces().entrySet()) {
                ap.declareXPathNameSpace(e.getKey(), e.getValue());
            }
        }
        return ap;
    }

    @Override
    public void index() throws Exception {

        // Parse use VTD-XML
        vg = new VTDGen();
        vg.setDoc(inputDocument);
        vg.parse(config.isNamespaceAware());

        nav = vg.getNav();

        // Find all documents
        AutoPilot documents = createAutoPilot();
        documents.selectXPath(config.getXPathDocuments());
        while(documents.evalXPath() != -1) {

            long documentFragment = nav.getContentFragment();
            int documentOffset = (int)documentFragment;
            int documentLength = (int)(documentFragment >> 32);

            startDocument(documentOffset, documentLength);

            // For each configured annotated field...
            for (ConfigAnnotatedField annotatedField: config.getAnnotatedFields()) {

                AutoPilot words = createAutoPilot();
                words.selectXPath(annotatedField.getXPathWords());

                // For each body element...
                nav.push();
                AutoPilot bodies = createAutoPilot();
                bodies.selectXPath(annotatedField.getXPathBody());
                while (bodies.evalXPath() != -1) {

                    // First we find all inline elements (stuff like s, p, b, etc.) and store
                    // the locations of their start and end tags in a sorted list.
                    // This way, we can keep track of between which words these tags occur.
                    // For end tags, we will update the payload of the start tag when we encounter it,
                    // just like we do in our SAX parsers.
                    AutoPilot apTags = createAutoPilot();
                    List<InlineTag> inlineTags = new ArrayList<>();
                    for (String xpInlineTag: annotatedField.getXPathsInlineTag()) {
                        nav.push();
                        apTags.selectXPath(xpInlineTag);
                        while (apTags.evalXPath() != -1) {
                            // Get the element and content fragments
                            // (element fragment = from start of start tag to end of end tag;
                            //  content fragment = from end of start tag to start of end tag)
                            long contentFragment = nav.getContentFragment();
                            int contentOffset = (int)contentFragment;
                            int contentLength = (int)(contentFragment >> 32);
                            int contentEnd = contentOffset + contentLength;
                            long elementFragment = nav.getElementFragment();
                            int elementOffset = (int)elementFragment;
                            int elementLength = (int)(elementFragment >> 32);
                            int elementEnd = elementOffset + elementLength;

                            // Calculate start/end tag offset and length
                            int startTagOffset = elementOffset;
                            int startTagLength = contentOffset - elementOffset;
                            int endTagOffset = contentEnd;
                            int endTagLength = elementEnd - contentEnd;

                            // Find element name
                            int currentIndex = nav.getCurrentIndex();
                            String elementName = nav.toString(currentIndex);

                            // Add the inline tags to the list
                            InlineTag openTag = new InlineTag(elementName, startTagOffset, startTagLength, true);
                            InlineTag closeTag = new InlineTag(elementName, endTagOffset, endTagLength, false);
                            openTag.setMatchingTag(closeTag);
                            closeTag.setMatchingTag(openTag);
                            inlineTags.add(openTag);
                            inlineTags.add(closeTag);
                        }
                        nav.pop();
                    }
                    Collections.sort(inlineTags);
                    Iterator<InlineTag> inlineTagsIt = inlineTags.iterator();
                    InlineTag nextInlineTag = inlineTagsIt.hasNext() ? inlineTagsIt.next() : null;

                    // Now, find all words, keeping track of what inline tags occur in between.
                    nav.push();
                    words.resetXPath();
                    while (words.evalXPath() != -1) {

                        long wordFragment = nav.getContentFragment();
                        int wordOffset = (int)wordFragment;

                        // Does an inline tag occur before this word?
                        while (wordOffset >= nextInlineTag.getOffset()) {
                            // Yes. Handle it.
                            inlineTag(nextInlineTag);
                            nextInlineTag = inlineTagsIt.next();
                        }

                        beginWord();

                        // Evaluate annotations for this word
                        AutoPilot apAnnot = createAutoPilot();
                        for (ConfigAnnotation annotation: annotatedField.getAnnotations()) {
                            nav.push();
                            apAnnot.selectXPath(annotation.getXPathValue());
                            String annotationValue = apAnnot.evalXPathToString();
                            annotation(annotation.getName(), annotationValue);
                            nav.pop();
                        }

                        endWord();
                    }
                    nav.pop();

                    // Handle any inline tags after the last word
                    while (nextInlineTag != null) {
                        inlineTag(nextInlineTag);
                        nextInlineTag = inlineTagsIt.hasNext() ? inlineTagsIt.next() : null;
                    }

                }
                nav.pop();
            }

            // For each metadata block..
            nav.push();
            AutoPilot apMetadataBlock = createAutoPilot();
            apMetadataBlock.selectXPath(config.getXPathMetadata());
            while (apMetadataBlock.evalXPath() != -1) {

                // For each configured metadata field...
                AutoPilot apMetadata = createAutoPilot();
                AutoPilot apFieldName = createAutoPilot();
                AutoPilot apMetaForEach = createAutoPilot();
                for (ConfigMetadataField f: config.getMetadataFields()) {

                    // Capture whatever this configured metadata field points to
                    nav.push();
                    if (f.isForEach()) {
                        // "forEach" metadata specification
                        // (allows us to capture many metadata fields with 3 XPath expressions)
                        apMetaForEach.selectXPath(f.getXPathForEach());
                        apFieldName.selectXPath(f.getFieldName());
                        apMetadata.selectXPath(f.getXPathValue());
                        while (apMetaForEach.evalXPath() != -1) {
                            // Find the fieldName and value for this forEach match
                            apFieldName.resetXPath();
                            String fieldName = apFieldName.evalXPathToString();
                            apMetadata.resetXPath();
                            String metadataValue = apMetadata.evalXPathToString();
                            metadata(fieldName, metadataValue);
                        }
                    } else {
                        // Regular metadata field; just the fieldName and an XPath expression for the value
                        apMetadata.selectXPath(f.getXPathValue());
                        String metadataValue = apMetadata.evalXPathToString();
                        metadata(f.getFieldName(), metadataValue);
                    }
                    nav.pop();
                }

            }
            nav.pop();

            endDocument(documentOffset, documentLength);
        }

    }

    /** Return the exact original code for an inline start or end tag. */
    String getInlineTagCode(InlineTag tag) {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try {
            nav.dumpFragment(tag.fragment(), os);
            return new String(os.toByteArray(), StandardCharsets.UTF_8);
        } catch (NavException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    // HANDLERS

    protected void startDocument(int offset, int length) {
        currentLuceneDoc = new Document();

        /* TODO: gather attributes, store as metadata fields

        // Store attribute values from the tag as metadata fields
        for (int i = 0; i < attributes.getLength(); i++) {
            addMetadataField(attributes.getLocalName(i),
                    attributes.getValue(i));
        }
        */

        currentLuceneDoc.add(new Field("fromInputFile", documentName, indexer.getMetadataFieldType(false)));
        addMetadataFieldsFromParameters();
        indexer.getListener().documentStarted(documentName);
    }

    protected void endDocument(int offset, int length) {

//        for (ComplexField contentsField: complexFields.values()) {
//            propMain = contentsField.getMainProperty();
//
//            // Make sure all the properties have an equal number of values.
//            // See what property has the highest position
//            // (in practice, only starttags and endtags should be able to have
//            // a position one higher than the rest)
//            int lastValuePos = 0;
//            for (ComplexFieldProperty prop: contentsField.getProperties()) {
//                if (prop.lastValuePosition() > lastValuePos)
//                    lastValuePos = prop.lastValuePosition();
//            }
//
//            // Make sure we always have one more token than the number of
//            // words, so there's room for any tags after the last word, and we
//            // know we should always skip the last token when matching.
//            if (propMain.lastValuePosition() == lastValuePos)
//                lastValuePos++;
//
//            // Add empty values to all lagging properties
//            for (ComplexFieldProperty prop: contentsField.getProperties()) {
//                while (prop.lastValuePosition() < lastValuePos) {
//                    prop.addValue("");
//                    if (prop.hasPayload())
//                        prop.addPayload(null);
//                    if (prop == propMain) {
//                        contentsField.addStartChar(getCharacterPosition());
//                        contentsField.addEndChar(getCharacterPosition());
//                    }
//                }
//            }
//            // Store the different properties of the complex contents field that
//            // were gathered in
//            // lists while parsing.
//            contentsField.addToLuceneDoc(currentLuceneDoc);
//
//            // Add all properties to forward index
//            for (ComplexFieldProperty prop: contentsField.getProperties()) {
//                if (!prop.hasForwardIndex())
//                    continue;
//
//                // Add property (case-sensitive tokens) to forward index and add
//                // id to Lucene doc
//                String propName = prop.getName();
//                String fieldName = ComplexFieldUtil.propertyField(
//                        contentsField.getName(), propName);
//                int fiid = indexer.addToForwardIndex(fieldName, prop);
//                currentLuceneDoc.add(new IntField(ComplexFieldUtil
//                        .forwardIndexIdField(fieldName), fiid, Store.YES));
//            }
//
//        }
//
////        // Finish storing the document in the document store (parts of it
////        // may already have been written because we write in chunks to save memory),
////        // retrieve the content id, and store that in Lucene.
////        // (Note that we do this after adding the dummy token, so the character
////        // positions for the dummy token still make (some) sense)
////        int contentId = storeCapturedContent();
////        currentLuceneDoc.add(new IntField(ComplexFieldUtil
////                .contentIdField(contentsField.getName()), contentId,
////                Store.YES));
//
//        // If there's an external metadata fetcher, call it now so it can
//        // add the metadata for this document and (optionally) store the
//        // metadata
//        // document in the content store (and the corresponding id in the
//        // Lucene doc)
//        MetadataFetcher m = getMetadataFetcher();
//        if (m != null) {
//            m.addMetadata();
//        }
//
//        // See what metadatafields are missing or empty and add unknown value
//        // if desired.
//        IndexStructure struct = indexer.getSearcher().getIndexStructure();
//        for (String fieldName: struct.getMetadataFields()) {
//            MetadataFieldDesc fd = struct.getMetadataFieldDesc(fieldName);
//            boolean missing = false, empty = false;
//            String currentValue = currentLuceneDoc.get(fieldName);
//            if (currentValue == null)
//                missing = true;
//            else if (currentValue.length() == 0)
//                empty = true;
//            UnknownCondition cond = fd.getUnknownCondition();
//            boolean useUnknownValue = false;
//            switch (cond) {
//            case EMPTY:
//                useUnknownValue = empty;
//                break;
//            case MISSING:
//                useUnknownValue = missing;
//                break;
//            case MISSING_OR_EMPTY:
//                useUnknownValue = missing | empty;
//                break;
//            case NEVER:
//                useUnknownValue = false;
//                break;
//            }
//            if (useUnknownValue)
//                addMetadataField(fieldName, fd.getUnknownValue());
//        }
//
//        try {
//            // Add Lucene doc to indexer
//            indexer.add(currentLuceneDoc);
//        } catch (Exception e) {
//            throw ExUtil.wrapRuntimeException(e);
//        }
//
//        // Report progress
//        reportCharsProcessed();
//        reportTokensProcessed(wordsDone);
//        wordsDone = 0;
//        indexer.getListener().documentDone(documentName);
//
//        // Reset contents field for next document
//        contentsField.clear();
//        currentLuceneDoc = null;
//
//        // Stop if required
//        if (!indexer.continueIndexing())
//            throw new MaxDocsReachedException();
    }

    protected void inlineTag(InlineTag tag) {
        System.out.print(getInlineTagCode(tag) + " ");
        if (!tag.isStartTag())
            System.out.print("\n");
    }

    protected void beginWord() {
//        System.out.println("[");
    }

    protected void annotation(String name, String value) {
    //        System.out.println(name + "=" + value + " ");
            if (name.equals("word"))
                System.out.print(value + " ");
        }

    protected void endWord() {
    //        System.out.println("] ");
        }

    protected void metadata(String fieldName, String value) {
        System.out.println("METADATA " + fieldName + "=" + value);
    }

}
