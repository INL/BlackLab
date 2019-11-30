package nl.inl.blacklab.indexers.config;

import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.TreeInfo;
import net.sf.saxon.trans.XPathException;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.InvalidConfiguration;
import nl.inl.blacklab.exceptions.MalformedInputFile;
import nl.inl.blacklab.exceptions.PluginException;
import org.xml.sax.SAXException;

import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An indexer configured using full XPath expressions.
 */
public class JaxpIndexer extends DocIndexerConfig {

    private SaxonicaHelper saxonicaHelper;
    private TreeInfo contents;

    @Override
    public void setDocument(Reader reader) {
        try {
            saxonicaHelper = new SaxonicaHelper(reader, config);
            contents = saxonicaHelper.getContents();
        } catch (IOException | XPathException | SAXException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    @Override
    public void index() throws MalformedInputFile, PluginException, IOException {
        super.index();

        try {
            XPathExpression documents = acquireXPathExpression(config.getDocumentPath());
            for (NodeInfo doc : (List<NodeInfo>) documents.evaluate(contents, XPathConstants.NODESET)) {
                indexDocument(doc);
            }
        } catch (XPathExpressionException e) {
            throw new InvalidConfiguration(e.getMessage() + String.format("; when indexing file: %s", documentName), e);
        }
    }

    private XPathExpression acquireXPathExpression(String path) {
        return saxonicaHelper.acquireXPathExpression(path);
    }

    /**
     * Index document from the current node.
     */
    protected void indexDocument(NodeInfo doc) throws XPathExpressionException {

        startDocument();

        // For each configured annotated field...
        for (ConfigAnnotatedField annotatedField : config.getAnnotatedFields().values()) {
            if (!annotatedField.isDummyForStoringLinkedDocuments())
                saxonicaHelper.test(doc, annotatedField);
        }

        // For each configured metadata block..
        for (ConfigMetadataBlock b : config.getMetadataBlocks()) {
//            processMetadataBlock(doc, b);
        }

        // For each linked document...
        for (ConfigLinkedDocument ld : config.getLinkedDocuments().values()) {
//            processLinkedDocument(doc, ld);
        }

        endDocument();
    }

    protected void processAnnotatedField(NodeInfo doc, ConfigAnnotatedField annotatedField)
            throws XPathExpressionException {

        Map<String, Integer> tokenPositionsMap = new HashMap<>();

        // Determine some useful stuff about the field we're processing
        // and store in instance variables so our methods can access them
//        setCurrentAnnotatedField(annotatedField);

        // Precompile XPaths for words, evalToString, inline tags, punct and (sub)annotations
        XPathExpression words = acquireXPathExpression(annotatedField.getWordsPath());
        XPathExpression apEvalToString = acquireXPathExpression(".");
        List<XPathExpression> apsInlineTag = new ArrayList<>();
        for (ConfigInlineTag inlineTag : annotatedField.getInlineTags()) {
            XPathExpression apInlineTag = acquireXPathExpression(inlineTag.getPath());
            apsInlineTag.add(apInlineTag);
        }
        XPathExpression apPunct = null;
        if (annotatedField.getPunctPath() != null)
            apPunct = acquireXPathExpression(annotatedField.getPunctPath());
        String tokenPositionIdPath = annotatedField.getTokenPositionIdPath();
        XPathExpression apTokenPositionId = null;
        if (tokenPositionIdPath != null) {
            apTokenPositionId = acquireXPathExpression(tokenPositionIdPath);
        }

//        // For each body element...
//        // (there's usually only one, but there's no reason to limit it)
//        XPathExpression bodies = acquireXPathExpression(annotatedField.getContainerPath());
//        AnnotatedFieldWriter annotatedFieldWriter = getAnnotatedField(annotatedField.getName());
//        List<NodeInfo> bodyList = (List<NodeInfo>) bodies.evaluate(doc,XPathConstants.NODESET);
//        for (NodeInfo body : bodyList) {
//
//            // First we find all inline elements (stuff like s, p, b, etc.) and store
//            // the locations of their start and end tags in a sorted list.
//            // This way, we can keep track of between which words these tags occur.
//            // For end tags, we will update the payload of the start tag when we encounter it,
//            // just like we do in our SAX parsers.
//            List<InlineObject> tagsAndPunct = new ArrayList<>();
//            for (XPathExpression apInlineTag : apsInlineTag) {
//                List<NodeInfo> inlineTags = (List<NodeInfo>) apInlineTag.evaluate(body,XPathConstants.NODESET);
//                for (NodeInfo inlineTag : inlineTags) {
//                    collectInlineTag(inlineTag, tagsAndPunct);
//                }
//            }
//            setAddDefaultPunctuation(true);
//            if (apPunct != null) {
//                // We have punctuation occurring between word tags (as opposed to
//                // punctuation that is tagged as a word itself). Collect this punctuation.
//                setAddDefaultPunctuation(false);
//                navpush();
//                List<NodeInfo> punctTags = (List<NodeInfo>) apPunct.evaluate(body,XPathConstants.NODESET);
//                for (NodeInfo p : punctTags) {
//                    String punct = String.valueOf(apEvalToString.evaluate(p,XPathConstants.STRING));
//                    // If punctPath matches an empty tag, replace it with a space.
//                    // Deals with e.g. <lb/> (line break) tags in TEI.
//                    if (punct.isEmpty())
//                        punct = " ";
//                    collectPunct(tagsAndPunct, punct);
//                }
//                navpop();
//            }
//            tagsAndPunct.sort(Comparator.naturalOrder());
//            Iterator<InlineObject> inlineObjectsIt = tagsAndPunct.iterator();
//            InlineObject nextInlineObject = inlineObjectsIt.hasNext() ? inlineObjectsIt.next() : null;
//
//            List<NodeInfo> ws = (List<NodeInfo>)words.evaluate(body,XPathConstants.NODESET);
//
//            for (NodeInfo w : ws) {
//                setCharPos(w);
//
//                // Capture tokenPositionId for this token position?
//                if (apTokenPositionId != null) {
//                    String tokenPositionId = apTokenPositionId.evaluate(w);
//                    tokenPositionsMap.put(tokenPositionId, getCurrentTokenPosition());
//                }
//
//                // Does an inline object occur before this word?
//                long wordFragment = nav.getContentFragment();
//                int wordOffset = (int) wordFragment;
//                while (nextInlineObject != null && wordOffset >= nextInlineObject.getOffset()) {
//                    // Yes. Handle it.
//                    if (nextInlineObject.type() == InlineObject.InlineObjectType.PUNCTUATION)
//                        punctuation(nextInlineObject.getText());
//                    else
//                        inlineTag(nextInlineObject.getText(), nextInlineObject.type() == InlineObject.InlineObjectType.OPEN_TAG,
//                                nextInlineObject.getAttributes());
//                    nextInlineObject = inlineObjectsIt.hasNext() ? inlineObjectsIt.next() : null;
//                }
//
//                fragPos = DocIndexerXPath.FragmentPosition.BEFORE_OPEN_TAG;
//                beginWord();
//
//                // For each configured annotation...
//                int lastValuePosition = -1; // keep track of last value position so we can update lagging annotations
//                for (ConfigAnnotation annotation : annotatedField.getAnnotations().values()) {
//                    processAnnotation(annotation, null);
//                    AnnotationWriter annotWriter = getAnnotation(annotation.getName());
//                    int lvp = annotWriter.lastValuePosition();
//                    if (lastValuePosition < lvp) {
//                        lastValuePosition = lvp;
//                    }
//                }
//
//                fragPos = DocIndexerXPath.FragmentPosition.AFTER_CLOSE_TAG;
//                endWord();
//
//                // Add empty values to all lagging annotations
//                for (AnnotationWriter prop: annotatedFieldWriter.annotationWriters()) {
//                    while (prop.lastValuePosition() < lastValuePosition) {
//                        prop.addValue("");
//                        if (prop.hasPayload())
//                            prop.addPayload(null);
//                    }
//                }
//            }
//            navpop();
//
//            // Handle any inline objects after the last word
//            while (nextInlineObject != null) {
//                if (nextInlineObject.type() == InlineObject.InlineObjectType.PUNCTUATION)
//                    punctuation(nextInlineObject.getText());
//                else
//                    inlineTag(nextInlineObject.getText(), nextInlineObject.type() == InlineObject.InlineObjectType.OPEN_TAG,
//                            nextInlineObject.getAttributes());
//                nextInlineObject = inlineObjectsIt.hasNext() ? inlineObjectsIt.next() : null;
//            }
//
//        }
//        navpop();
//
//        // For each configured standoff annotation...
//        for (ConfigStandoffAnnotations standoff : annotatedField.getStandoffAnnotations()) {
//            // For each instance of this standoff annotation..
//            navpush();
//            XPathExpression apStandoff = acquireXPathExpression(standoff.getPath());
//            XPathExpression apTokenPos = acquireXPathExpression(standoff.getRefTokenPositionIdPath());
//            while (apStandoff.evalXPath() != -1) {
//
//                // Determine what token positions to index these values at
//                navpush();
//                List<Integer> tokenPositions = new ArrayList<>();
//                apTokenPos.resetXPath();
//                while (apTokenPos.evalXPath() != -1) {
//                    apEvalToString.resetXPath();
//                    String tokenPositionId = apEvalToString.evalXPathToString();
//                    Integer integer = tokenPositionsMap.get(tokenPositionId);
//                    if (integer == null)
//                        warn("Unresolved reference to token position: '" + tokenPositionId + "'");
//                    else
//                        tokenPositions.add(integer);
//                }
//                navpop();
//
//                for (ConfigAnnotation annotation : standoff.getAnnotations().values()) {
//                    processAnnotation(annotation, tokenPositions);
//                }
//            }
//            releaseXPathExpression(apStandoff);
//            releaseXPathExpression(apTokenPos);
//            navpop();
//        }
//
//        releaseXPathExpression(words);
//        releaseXPathExpression(apEvalToString);
//        for (XPathExpression ap : apsInlineTag) {
//            releaseXPathExpression(ap);
//        }
//        if (apPunct != null)
//            releaseXPathExpression(apPunct);
//        if (apTokenPositionId != null)
//            releaseXPathExpression(apTokenPositionId);
//        releaseXPathExpression(bodies);
    }

    /**
     * Add open and close InlineObject objects for the current element to the list.
     *
     * @param inlineObject list to add the new open/close tag objects to
     */
    private void collectInlineTag(NodeInfo inlineTags, List<InlineObject> inlineObject) {
//        // Get the element and content fragments
//        // (element fragment = from start of start tag to end of end tag;
//        //  content fragment = from end of start tag to start of end tag)
//        long elementFragment = nav.getElementFragment();
//        int startTagOffset = (int) elementFragment;
//        int endTagOffset;
//        long contentFragment = nav.getContentFragment();
//        if (contentFragment == -1) {
//            // Empty (self-closing) element.
//            endTagOffset = startTagOffset;
//        } else {
//            // Regular element with separate open and close tags.
//            int contentOffset = (int) contentFragment;
//            int contentLength = (int) (contentFragment >> 32);
//            int contentEnd = contentOffset + contentLength;
//            endTagOffset = contentEnd;
//        }
//
//        // Find element name
//        int currentIndex = nav.getCurrentIndex();
//        String elementName = dedupe(nav.toString(currentIndex));
//
//        // Add the inline tags to the list
//        InlineObject openTag = new InlineObject(elementName, startTagOffset, InlineObject.InlineObjectType.OPEN_TAG,
//                getAttributes());
//        InlineObject closeTag = new InlineObject(elementName, endTagOffset, InlineObject.InlineObjectType.CLOSE_TAG, null);
//        openTag.setMatchingTag(closeTag);
//        closeTag.setMatchingTag(openTag);
//        inlineObject.add(openTag);
//        inlineObject.add(closeTag);
    }

    /**
     * Add InlineObject for a punctuation text node.
     *
     * @param inlineObjects list to add the punct object to
     * @param text
     */
    private void collectPunct(List<InlineObject> inlineObjects, String text) {
//        int i = nav.getCurrentIndex();
//        int offset = nav.getTokenOffset(i);
////		int length = nav.getTokenLength(i);
//
//        // Make sure we only keep 1 copy of identical punct texts in memory
//        text = dedupe(StringUtil.normalizeWhitespace(text));
//
//        // Add the punct to the list
//        inlineObjects.add(new InlineObject(text, offset, InlineObject.InlineObjectType.PUNCTUATION, null));
    }

    protected void navpush() {
//        fragPosStack.add(fragPos);
//        fragPos = FragmentPosition.BEFORE_OPEN_TAG;
    }

    protected void navpop() {
//        fragPos = fragPosStack.remove(fragPosStack.size() - 1);
    }

    protected void processMetadataBlock(NodeInfo meta, ConfigMetadataBlock b) {
//        // For each instance of this metadata block...
//        navpush();
//        XPathExpression apMetadataBlock = acquireXPathExpression(b.getContainerPath());
//        while (apMetadataBlock.evalXPath() != -1) {
//
//            // For each configured metadata field...
//            List<ConfigMetadataField> fields = b.getFields();
//            for (int i = 0; i < fields.size(); i++) { // NOTE: fields may be added during loop, so can't iterate
//                ConfigMetadataField f = fields.get(i);
//
//                // Metadata field configs without a valuePath are just for
//                // adding information about fields captured in forEach's,
//                // such as extra processing steps
//                if (f.getValuePath() == null || f.getValuePath().isEmpty())
//                    continue;
//
//                // Capture whatever this configured metadata field points to
//                XPathExpression apMetadata = acquireXPathExpression(f.getValuePath());
//                if (f.isForEach()) {
//                    // "forEach" metadata specification
//                    // (allows us to capture many metadata fields with 3 XPath expressions)
//                    navpush();
//                    XPathExpression apMetaForEach = acquireXPathExpression(f.getForEachPath());
//                    XPathExpression apFieldName = acquireXPathExpression(f.getName());
//                    while (apMetaForEach.evalXPath() != -1) {
//                        // Find the fieldName and value for this forEach match
//                        apFieldName.resetXPath();
//                        String origFieldName = apFieldName.evalXPathToString();
//                        String fieldName = AnnotatedFieldNameUtil.sanitizeXmlElementName(origFieldName);
//                        if (!origFieldName.equals(fieldName)) {
//                            warnSanitized(origFieldName, fieldName);
//                        }
//                        ConfigMetadataField metadataField = b.getOrCreateField(fieldName);
//
//                        apMetadata.resetXPath();
//
//                        // Multiple matches will be indexed at the same position.
//                        XPathExpression apEvalToString = acquireXPathExpression(".");
//                        while (apMetadata.evalXPath() != -1) {
//                            apEvalToString.resetXPath();
//                            String unprocessedValue = apEvalToString.evalXPathToString();
//                            for (String value : processStringMultipleValues(unprocessedValue, f.getProcess(), null)) {
//                                // Also execute process defined for named metadata field, if any
//                                for (String processedValue : processStringMultipleValues(value, metadataField.getProcess(), metadataField.getMapValues())) {
//                                    addMetadataField(fieldName, processedValue);
//                                }
//                            }
//                        }
//                        releaseXPathExpression(apEvalToString);
//                    }
//                    releaseXPathExpression(apMetaForEach);
//                    releaseXPathExpression(apFieldName);
//                    navpop();
//                } else {
//                    // Regular metadata field; just the fieldName and an XPath expression for the value
//                    // Multiple matches will be indexed at the same position.
//                    XPathExpression apEvalToString = acquireXPathExpression(".");
//                    try {
//                        while (apMetadata.evalXPath() != -1) {
//                            apEvalToString.resetXPath();
//                            String unprocessedValue = apEvalToString.evalXPathToString();
//                            for (String value : processStringMultipleValues(unprocessedValue, f.getProcess(), f.getMapValues())) {
//                                addMetadataField(f.getName(), value);
//                            }
//                        }
//                    } catch (XPathEvalException e) {
//                        /*
//                        An xpath like string(@value) will make evalXPath() fail.
//                        There is no good way to check wether this exception will occur
//                        When the exception occurs we try to evaluate the xpath as string
//                        NOTE: an xpath with dot like: string(.//tei:availability[1]/@status='free') may fail silently!!
//                         */
//                        if (logger.isDebugEnabled()) {
//                            logger.debug(String.format("An xpath with a dot like %s may fail silently and may have to be replaced by one like %s",
//                                    "string(.//tei:availability[1]/@status='free')",
//                                    "string(//tei:availability[1]/@status='free')"));
//                        }
//                        String metadataValue = apMetadata.evalXPathToString();
//                        metadataValue = processString(metadataValue, f.getProcess(), f.getMapValues());
//                        addMetadataField(f.getName(), metadataValue);
//                    }
//                    releaseXPathExpression(apEvalToString);
//                }
//                releaseXPathExpression(apMetadata);
//            }
//        }
    }


    protected void processLinkedDocument(NodeInfo doc, ConfigLinkedDocument ld) {
//        // Resolve linkPaths to get the information needed to fetch the document
//        List<String> results = new ArrayList<>();
//        for (ConfigLinkValue linkValue : ld.getLinkValues()) {
//            String result = "";
//            String valuePath = linkValue.getValuePath();
//            String valueField = linkValue.getValueField();
//            if (valuePath != null) {
//                // Resolve value using XPath
//                AutoPilot apLinkPath = acquireAutoPilot(valuePath);
//                result = apLinkPath.evalXPathToString();
//                if (result == null || result.isEmpty()) {
//                    switch (ld.getIfLinkPathMissing()) {
//                        case IGNORE:
//                            break;
//                        case WARN:
//                            docWriter.listener()
//                                    .warning("Link path " + valuePath + " not found in document " + documentName);
//                            break;
//                        case FAIL:
//                            throw new BlackLabRuntimeException("Link path " + valuePath + " not found in document " + documentName);
//                    }
//                }
//                releaseAutoPilot(apLinkPath);
//            } else if (valueField != null) {
//                // Fetch value from Lucene doc
//                result = getMetadataField(valueField).get(0);
//            }
//            result = processString(result, linkValue.getProcess(), null);
//            results.add(result);
//        }
//
//        // Substitute link path results in inputFile, pathInsideArchive and documentPath
//        String inputFile = replaceDollarRefs(ld.getInputFile(), results);
//        String pathInsideArchive = replaceDollarRefs(ld.getPathInsideArchive(), results);
//        String documentPath = replaceDollarRefs(ld.getDocumentPath(), results);
//
//        try {
//            // Fetch and index the linked document
//            indexLinkedDocument(inputFile, pathInsideArchive, documentPath, ld.getInputFormatIdentifier(),
//                    ld.shouldStore() ? ld.getName() : null);
//        } catch (Exception e) {
//            String moreInfo = "(inputFile = " + inputFile;
//            if (pathInsideArchive != null)
//                moreInfo += ", pathInsideArchive = " + pathInsideArchive;
//            if (documentPath != null)
//                moreInfo += ", documentPath = " + documentPath;
//            moreInfo += ")";
//            switch (ld.getIfLinkPathMissing()) {
//                case IGNORE:
//                case WARN:
//                    docWriter.listener().warning("Could not find or parse linked document for " + documentName + moreInfo
//                            + ": " + e.getMessage());
//                    break;
//                case FAIL:
//                    throw new BlackLabRuntimeException("Could not find or parse linked document for " + documentName + moreInfo, e);
//            }
//        }
    }
    /*
    Hoe willen we dit eigenlijk gaan doen?

    We hebben een config waarin xpath expressies staan waarmee waarden uit het xml gehaald kunnen worden.
    Dat gaat prima, maar....
    We de positie van het eerste karakter van elk "word" nodig
    - Een route via DOM Document biedt geen manier die posities te vinden
    - Een route via Reader biedt wel posities maar geen XML processing
    Xslt met embedded Java is misschien wel iets
    Sax ContentHandler heeft Locator, die zou ik wel willen gebruiken

    Saxonica heeft via NodeInfo lineNumber en columnNumber, zie:
    https://examples.javacodegeeks.com/core-java/xml/xpath/java-xpath-using-sax-example/

    Van lineNumber / columnNumber moeten we dan nog character position maken:
        - Map<Integer,Integer> met line/cumulative cols

     */

    @Override
    protected void storeDocument() {

    }

    @Override
    public void close() {

    }

    private int startPos = 0;

    @Override
    protected int getCharacterPosition() {
        return startPos;
    }
}
