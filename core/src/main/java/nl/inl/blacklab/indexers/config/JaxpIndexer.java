package nl.inl.blacklab.indexers.config;

import com.ximpleware.NavException;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.MalformedInputFile;
import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.index.Indexer;
import nl.inl.blacklab.index.annotated.AnnotatedFieldWriter;
import nl.inl.blacklab.index.annotated.AnnotationWriter;
import nl.inl.blacklab.indexers.config.InlineObject.InlineObjectType;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.util.StringUtil;
import nl.inl.util.XmlUtil;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.*;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.Map.Entry;

/**
 * An indexer configured using full XPath 1.0 expressions.
 */
public class JaxpIndexer extends DocIndexerConfig {

    private enum FragmentPosition {
        BEFORE_OPEN_TAG,
        AFTER_OPEN_TAG,
        BEFORE_CLOSE_TAG,
        AFTER_CLOSE_TAG
    }

    /** What was the byte offset of the last char position we determined? */
    private int lastCharPositionByteOffset;

    /** What was the last character position we determined? */
    private int lastCharPosition;

    /** Byte position at which the document started */
    private int documentByteOffset;

    /** Length of the document in bytes */
    private int documentLengthBytes;

    /** Where the current position is relative to the current fragment */
    private FragmentPosition fragPos = FragmentPosition.BEFORE_OPEN_TAG;

    /** Fragment positions in ancestors */
    private List<FragmentPosition> fragPosStack = new ArrayList<>();

    /** The config for the annotated field we're currently processing. */
    private ConfigAnnotatedField currentAnnotatedFieldConfig;

    @Override
    public void close() {
        // NOP, we already closed our input after we read it
    }

    @Override
    public void setDocument(File file, Charset defaultCharset) throws FileNotFoundException {
        try {
            setDocument(FileUtils.readFileToByteArray(file), defaultCharset);
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    @Override
    public void setDocument(byte[] contents, Charset defaultCharset) {
        this.contents = contents;
    }

    @Override
    public void setDocument(InputStream is, Charset defaultCharset) {
        try {
            setDocument(IOUtils.toByteArray(is), defaultCharset);
            is.close();
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }
    private static final ThreadLocal<XPathFactory> X_PATH_FACTORY_THREAD_LOCAL = new InheritableThreadLocal<XPathFactory>() {
        @Override
        protected XPathFactory initialValue() {
            return XPathFactory.newInstance();
        }
    };

    /**
     * Map from XPath expression to compiled XPath.
     */
    private Map<String, XPathExpression> compiledXPaths = new HashMap<>();

    private byte[] contents;

    /**
     * XPathExpressions that are currently being used. We need to keep track of this to be
     * able to re-add them to compiledXpath with the correct XPath expression later.
     */
    private Map<XPathExpression, String> XPathExpressionsInUse = new HashMap<>();

    private Map<String, NamespaceContext> namespaces = new HashMap<>(3);

    private NamespaceContext getNamespace(String prefix, String uri) {
        String key = prefix + ":" + uri;
        if (!namespaces.containsKey(key)) {
            namespaces.put(key, new NamespaceContext() {
                @Override
                public String getNamespaceURI(String prefix) {
                    return prefix;
                }

                @Override
                public String getPrefix(String namespaceURI) {
                    return uri;
                }

                @Override
                public Iterator getPrefixes(String namespaceURI) {
                    return null;
                }
            });
        }
        return namespaces.get(key);
    }

    /**
     * Create XPathExpression and declare namespaces on it.
     *
     * @param xpathExpr xpath expression for the XPathExpression
     * @return the XPathExpression
     */
    private XPathExpression acquireXPathExpression(String xpathExpr) {
        XPathExpression xPathExpression = compiledXPaths.remove(xpathExpr);
        if (xPathExpression == null) {
            XPath xPath = X_PATH_FACTORY_THREAD_LOCAL.get().newXPath();
            if (config.isNamespaceAware()) {
                xPath.setNamespaceContext(getNamespace("xml", "http://www.w3.org/XML/1998/namespace"));
                for (Entry<String, String> e : config.getNamespaces().entrySet()) {
                    xPath.setNamespaceContext(getNamespace(e.getKey(), e.getValue()));
                }
            }
            try {
                xPathExpression = xPath.compile(xpathExpr);
            } catch (XPathExpressionException e) {
                throw new BlackLabRuntimeException("Error in XPath expression " + xpathExpr + " : " + e.getMessage(), e);
            }
        }
        XPathExpressionsInUse.put(xPathExpression, xpathExpr);
        return xPathExpression;
    }

    private void releaseXPathExpression(XPathExpression ap) {
        String xpathExpr = XPathExpressionsInUse.remove(ap);
        compiledXPaths.put(xpathExpr, ap);
    }


    @Override
    public void index() throws MalformedInputFile, PluginException, IOException {
        super.index();

        try {
            XPathExpression documents = acquireXPathExpression(config.getDocumentPath());
            NodeList docs = (NodeList) documents.evaluate(new InputSource(new ByteArrayInputStream(contents)), XPathConstants.NODESET);
            for (int i = 0; i < docs.getLength(); i++) {
                indexDocument(docs.item(i));
            }
        } catch (XPathExpressionException e) {
            throw new MalformedInputFile("Error indexing file: " + documentName, e);
        }
    }

    /**
     * Index document from the current node.
     */
    protected void indexDocument(Node doc) throws XPathExpressionException {

        startDocument();

        // For each configured annotated field...
        for (ConfigAnnotatedField annotatedField : config.getAnnotatedFields().values()) {
            if (!annotatedField.isDummyForStoringLinkedDocuments())
                processAnnotatedField(doc, annotatedField);
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

    protected void processAnnotatedField(Node doc, ConfigAnnotatedField annotatedField)
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

        // For each body element...
        // (there's usually only one, but there's no reason to limit it)
        XPathExpression bodies = acquireXPathExpression(annotatedField.getContainerPath());
        AnnotatedFieldWriter annotatedFieldWriter = getAnnotatedField(annotatedField.getName());
        NodeList bodyList = (NodeList) bodies.evaluate(doc,XPathConstants.NODESET);
        for (int i = 0; i < bodyList.getLength(); i++) {
            Node body = bodyList.item(i);

            // First we find all inline elements (stuff like s, p, b, etc.) and store
            // the locations of their start and end tags in a sorted list.
            // This way, we can keep track of between which words these tags occur.
            // For end tags, we will update the payload of the start tag when we encounter it,
            // just like we do in our SAX parsers.
            List<InlineObject> tagsAndPunct = new ArrayList<>();
            for (XPathExpression apInlineTag : apsInlineTag) {
                NodeList inlineTags = (NodeList) apInlineTag.evaluate(body,XPathConstants.NODESET);
                for (i = 0; i < inlineTags.getLength(); i++) {
                    Node inlineTag = inlineTags.item(i);
                    collectInlineTag(inlineTag, tagsAndPunct);
                }
            }
            setAddDefaultPunctuation(true);
            if (apPunct != null) {
                // We have punctuation occurring between word tags (as opposed to
                // punctuation that is tagged as a word itself). Collect this punctuation.
                setAddDefaultPunctuation(false);
                navpush();
                NodeList punctTags = (NodeList) apPunct.evaluate(body,XPathConstants.NODESET);
                for (i = 0; i < punctTags.getLength(); i++) {
                    Node p = punctTags.item(i);
                    String punct = String.valueOf(apEvalToString.evaluate(p,XPathConstants.STRING));
                    // If punctPath matches an empty tag, replace it with a space.
                    // Deals with e.g. <lb/> (line break) tags in TEI.
                    if (punct.isEmpty())
                        punct = " ";
                    collectPunct(tagsAndPunct, punct);
                }
                navpop();
            }
            tagsAndPunct.sort(Comparator.naturalOrder());
            Iterator<InlineObject> inlineObjectsIt = tagsAndPunct.iterator();
            InlineObject nextInlineObject = inlineObjectsIt.hasNext() ? inlineObjectsIt.next() : null;

            // Now, find all words, keeping track of what inline objects occur in between.
            navpush();

            // first find all words and sort the list -- words are returned out of order when they are at different nesting levels
            // since the xpath spec doesn't enforce any order, there's nothing we can do
            // so record their positions, sort the list, then restore the position and carry on
            List<Pair<Integer, BookMark>> wordPositions = new ArrayList<>();
            NodeList ws = (NodeList)words.evaluate(body,XPathConstants.NODESET);
            for (i =0; i< ws.getLength();i++) {
                Node w = ws.item(i);
                BookMark b = new BookMark(nav);
                b.setCursorPosition();
                wordPositions.add(Pair.of(nav.getCurrentIndex(), b));
            }
            wordPositions.sort((a, b) -> a.getKey().compareTo(b.getKey()));

            for (Pair<Integer, BookMark> wordPosition : wordPositions) {
                wordPosition.getValue().setCursorPosition();

                // Capture tokenPositionId for this token position?
                if (apTokenPositionId != null) {
                    apTokenPositionId.resetXPath();
                    String tokenPositionId = apTokenPositionId.evalXPathToString();
                    tokenPositionsMap.put(tokenPositionId, getCurrentTokenPosition());
                }

                // Does an inline object occur before this word?
                long wordFragment = nav.getContentFragment();
                int wordOffset = (int) wordFragment;
                while (nextInlineObject != null && wordOffset >= nextInlineObject.getOffset()) {
                    // Yes. Handle it.
                    if (nextInlineObject.type() == InlineObject.InlineObjectType.PUNCTUATION)
                        punctuation(nextInlineObject.getText());
                    else
                        inlineTag(nextInlineObject.getText(), nextInlineObject.type() == InlineObject.InlineObjectType.OPEN_TAG,
                                nextInlineObject.getAttributes());
                    nextInlineObject = inlineObjectsIt.hasNext() ? inlineObjectsIt.next() : null;
                }

                fragPos = DocIndexerXPath.FragmentPosition.BEFORE_OPEN_TAG;
                beginWord();

                // For each configured annotation...
                int lastValuePosition = -1; // keep track of last value position so we can update lagging annotations
                for (ConfigAnnotation annotation : annotatedField.getAnnotations().values()) {
                    processAnnotation(annotation, null);
                    AnnotationWriter annotWriter = getAnnotation(annotation.getName());
                    int lvp = annotWriter.lastValuePosition();
                    if (lastValuePosition < lvp) {
                        lastValuePosition = lvp;
                    }
                }

                fragPos = DocIndexerXPath.FragmentPosition.AFTER_CLOSE_TAG;
                endWord();

                // Add empty values to all lagging annotations
                for (AnnotationWriter prop: annotatedFieldWriter.annotationWriters()) {
                    while (prop.lastValuePosition() < lastValuePosition) {
                        prop.addValue("");
                        if (prop.hasPayload())
                            prop.addPayload(null);
                    }
                }
            }
            navpop();

            // Handle any inline objects after the last word
            while (nextInlineObject != null) {
                if (nextInlineObject.type() == InlineObject.InlineObjectType.PUNCTUATION)
                    punctuation(nextInlineObject.getText());
                else
                    inlineTag(nextInlineObject.getText(), nextInlineObject.type() == InlineObject.InlineObjectType.OPEN_TAG,
                            nextInlineObject.getAttributes());
                nextInlineObject = inlineObjectsIt.hasNext() ? inlineObjectsIt.next() : null;
            }

        }
        navpop();

        // For each configured standoff annotation...
        for (ConfigStandoffAnnotations standoff : annotatedField.getStandoffAnnotations()) {
            // For each instance of this standoff annotation..
            navpush();
            XPathExpression apStandoff = acquireXPathExpression(standoff.getPath());
            XPathExpression apTokenPos = acquireXPathExpression(standoff.getRefTokenPositionIdPath());
            while (apStandoff.evalXPath() != -1) {

                // Determine what token positions to index these values at
                navpush();
                List<Integer> tokenPositions = new ArrayList<>();
                apTokenPos.resetXPath();
                while (apTokenPos.evalXPath() != -1) {
                    apEvalToString.resetXPath();
                    String tokenPositionId = apEvalToString.evalXPathToString();
                    Integer integer = tokenPositionsMap.get(tokenPositionId);
                    if (integer == null)
                        warn("Unresolved reference to token position: '" + tokenPositionId + "'");
                    else
                        tokenPositions.add(integer);
                }
                navpop();

                for (ConfigAnnotation annotation : standoff.getAnnotations().values()) {
                    processAnnotation(annotation, tokenPositions);
                }
            }
            releaseXPathExpression(apStandoff);
            releaseXPathExpression(apTokenPos);
            navpop();
        }

        releaseXPathExpression(words);
        releaseXPathExpression(apEvalToString);
        for (XPathExpression ap : apsInlineTag) {
            releaseXPathExpression(ap);
        }
        if (apPunct != null)
            releaseXPathExpression(apPunct);
        if (apTokenPositionId != null)
            releaseXPathExpression(apTokenPositionId);
        releaseXPathExpression(bodies);
    }

    protected void navpush() {
        fragPosStack.add(fragPos);
        fragPos = FragmentPosition.BEFORE_OPEN_TAG;
    }

    protected void navpop() {
        fragPos = fragPosStack.remove(fragPosStack.size() - 1);
    }

    protected void processMetadataBlock(ConfigMetadataBlock b)
            throws XPathParseException, XPathEvalException, NavException {
        // For each instance of this metadata block...
        navpush();
        XPathExpression apMetadataBlock = acquireXPathExpression(b.getContainerPath());
        while (apMetadataBlock.evalXPath() != -1) {

            // For each configured metadata field...
            List<ConfigMetadataField> fields = b.getFields();
            for (int i = 0; i < fields.size(); i++) { // NOTE: fields may be added during loop, so can't iterate
                ConfigMetadataField f = fields.get(i);

                // Metadata field configs without a valuePath are just for
                // adding information about fields captured in forEach's,
                // such as extra processing steps
                if (f.getValuePath() == null || f.getValuePath().isEmpty())
                    continue;

                // Capture whatever this configured metadata field points to
                XPathExpression apMetadata = acquireXPathExpression(f.getValuePath());
                if (f.isForEach()) {
                    // "forEach" metadata specification
                    // (allows us to capture many metadata fields with 3 XPath expressions)
                    navpush();
                    XPathExpression apMetaForEach = acquireXPathExpression(f.getForEachPath());
                    XPathExpression apFieldName = acquireXPathExpression(f.getName());
                    while (apMetaForEach.evalXPath() != -1) {
                        // Find the fieldName and value for this forEach match
                        apFieldName.resetXPath();
                        String origFieldName = apFieldName.evalXPathToString();
                        String fieldName = AnnotatedFieldNameUtil.sanitizeXmlElementName(origFieldName);
                        if (!origFieldName.equals(fieldName)) {
                            warnSanitized(origFieldName, fieldName);
                        }
                        ConfigMetadataField metadataField = b.getOrCreateField(fieldName);

                        apMetadata.resetXPath();

                        // Multiple matches will be indexed at the same position.
                        XPathExpression apEvalToString = acquireXPathExpression(".");
                        while (apMetadata.evalXPath() != -1) {
                            apEvalToString.resetXPath();
                            String unprocessedValue = apEvalToString.evalXPathToString();
                            for (String value : processStringMultipleValues(unprocessedValue, f.getProcess(), null)) {
                                // Also execute process defined for named metadata field, if any
                                for (String processedValue : processStringMultipleValues(value, metadataField.getProcess(), metadataField.getMapValues())) {
                                    addMetadataField(fieldName, processedValue);
                                }
                            }
                        }
                        releaseXPathExpression(apEvalToString);
                    }
                    releaseXPathExpression(apMetaForEach);
                    releaseXPathExpression(apFieldName);
                    navpop();
                } else {
                    // Regular metadata field; just the fieldName and an XPath expression for the value
                    // Multiple matches will be indexed at the same position.
                    XPathExpression apEvalToString = acquireXPathExpression(".");
                    try {
                        while (apMetadata.evalXPath() != -1) {
                            apEvalToString.resetXPath();
                            String unprocessedValue = apEvalToString.evalXPathToString();
                            for (String value : processStringMultipleValues(unprocessedValue, f.getProcess(), f.getMapValues())) {
                                addMetadataField(f.getName(), value);
                            }
                        }
                    } catch(XPathEvalException e) {
                        /*
                        An xpath like string(@value) will make evalXPath() fail.
                        There is no good way to check wether this exception will occur
                        When the exception occurs we try to evaluate the xpath as string
                        NOTE: an xpath with dot like: string(.//tei:availability[1]/@status='free') may fail silently!!
                         */
                        if (logger.isDebugEnabled()) {
                            logger.debug(String.format("An xpath with a dot like %s may fail silently and may have to be replaced by one like %s",
                                    "string(.//tei:availability[1]/@status='free')",
                                    "string(//tei:availability[1]/@status='free')"));
                        }
                        String metadataValue = apMetadata.evalXPathToString();
                        metadataValue = processString(metadataValue, f.getProcess(), f.getMapValues());
                        addMetadataField(f.getName(), metadataValue);
                    }                    releaseXPathExpression(apEvalToString);
                }
                releaseXPathExpression(apMetadata);
            }

        }
        releaseXPathExpression(apMetadataBlock);
        navpop();
    }

    private static Set<String> reportedSanitizedNames = new HashSet<>();

    private synchronized static void warnSanitized(String origFieldName, String fieldName) {
        if (!reportedSanitizedNames.contains(origFieldName)) {
            logger.warn("Name '" + origFieldName + "' is not a valid XML element name; sanitized to '" + fieldName + "'");
            reportedSanitizedNames.add(origFieldName);
        }
    }

    protected void processLinkedDocument(ConfigLinkedDocument ld) throws XPathParseException {
        // Resolve linkPaths to get the information needed to fetch the document
        List<String> results = new ArrayList<>();
        for (ConfigLinkValue linkValue : ld.getLinkValues()) {
            String result = "";
            String valuePath = linkValue.getValuePath();
            String valueField = linkValue.getValueField();
            if (valuePath != null) {
                // Resolve value using XPath
                XPathExpression apLinkPath = acquireXPathExpression(valuePath);
                result = apLinkPath.evalXPathToString();
                if (result == null || result.isEmpty()) {
                    switch (ld.getIfLinkPathMissing()) {
                    case IGNORE:
                        break;
                    case WARN:
                        docWriter.listener()
                                .warning("Link path " + valuePath + " not found in document " + documentName);
                        break;
                    case FAIL:
                        throw new BlackLabRuntimeException("Link path " + valuePath + " not found in document " + documentName);
                    }
                }
                releaseXPathExpression(apLinkPath);
            } else if (valueField != null) {
                // Fetch value from Lucene doc
                result = getMetadataField(valueField).get(0);
            }
            result = processString(result, linkValue.getProcess(), null);
            results.add(result);
        }

        // Substitute link path results in inputFile, pathInsideArchive and documentPath
        String inputFile = replaceDollarRefs(ld.getInputFile(), results);
        String pathInsideArchive = replaceDollarRefs(ld.getPathInsideArchive(), results);
        String documentPath = replaceDollarRefs(ld.getDocumentPath(), results);

        try {
            // Fetch and index the linked document
            indexLinkedDocument(inputFile, pathInsideArchive, documentPath, ld.getInputFormatIdentifier(),
                    ld.shouldStore() ? ld.getName() : null);
        } catch (Exception e) {
            String moreInfo = "(inputFile = " + inputFile;
            if (pathInsideArchive != null)
                moreInfo += ", pathInsideArchive = " + pathInsideArchive;
            if (documentPath != null)
                moreInfo += ", documentPath = " + documentPath;
            moreInfo += ")";
            switch (ld.getIfLinkPathMissing()) {
            case IGNORE:
            case WARN:
                docWriter.listener().warning("Could not find or parse linked document for " + documentName + moreInfo
                        + ": " + e.getMessage());
                break;
            case FAIL:
                throw new BlackLabRuntimeException("Could not find or parse linked document for " + documentName + moreInfo, e);
            }
        }
    }

    /**
     * Process an annotation at the current position.
     *
     * @param annotation annotation to process
     * @param indexAtPositions if null: index at the current position; otherwise,
     *            index at all these positions
     * @throws VTDException on XPath error
     */
    protected void processAnnotation(ConfigAnnotation annotation, List<Integer> indexAtPositions) throws VTDException {
        String basePath = annotation.getBasePath();
        if (basePath != null) {
            // Basepath given. Navigate to the (first) matching element and evaluate the other XPaths from there.
            navpush();
            XPathExpression apBase = acquireXPathExpression(basePath);
            apBase.evalXPath();
            releaseXPathExpression(apBase);
        }
        try {
            String valuePath = annotation.getValuePath();
            if (valuePath == null) {
                // No valuePath given. Assume this will be captures using forEach.
                return;
            }

            // See if we want to capture any values and substitute them into the XPath
            int i = 1;
            for (String captureValuePath : annotation.getCaptureValuePaths()) {
                XPathExpression apCaptureValuePath = acquireXPathExpression(captureValuePath);
                String value = apCaptureValuePath.evalXPathToString();
                releaseXPathExpression(apCaptureValuePath);
                valuePath = valuePath.replace("$" + i, value);
                i++;
            }

            // Find matches for this annotation.
            String annotValue = findAnnotationMatches(annotation, valuePath, indexAtPositions, null);

            // For each configured subannotation...
            Set<String> alreadySeen = new HashSet<>(); // keep track of which annotation have multiple values so we can use the correct position increment 
            for (ConfigAnnotation subAnnot : annotation.getSubAnnotations()) {
                // Subannotation configs without a valuePath are just for
                // adding information about subannotations captured in forEach's,
                // such as extra processing steps
                if (subAnnot.getValuePath() == null || subAnnot.getValuePath().isEmpty())
                    continue;

                // Capture this subannotation value
                XPathExpression apValue = acquireXPathExpression(subAnnot.getValuePath());
                if (subAnnot.isForEach()) {
                    // "forEach" subannotation specification
                    // (allows us to capture multiple subannotations with 3 XPath expressions)
                    navpush();
                    XPathExpression apForEach = acquireXPathExpression(subAnnot.getForEachPath());
                    XPathExpression apName = acquireXPathExpression(subAnnot.getName());
                    alreadySeen.clear();
                    while (apForEach.evalXPath() != -1) {
                        // Find the name and value for this forEach match
                        apName.resetXPath();
                        apValue.resetXPath();

                        String name = apName.evalXPathToString();
                        String subannotationName = annotation.getName() + AnnotatedFieldNameUtil.SUBANNOTATION_FIELD_PREFIX_SEPARATOR + name;
                        ConfigAnnotation actualSubAnnot = annotation.getSubAnnotation(subannotationName);

                        String value = null;
                        if (actualSubAnnot != null) {
                            value = actualSubAnnot.isCaptureXml()? apValue.evalXPath() != -1 ? getXml(apValue) : "" : apValue.evalXPathToString(); 
                            value = processString(value, subAnnot.getProcess(), null);
                            // Also apply process defined in named subannotation, if any
                            value = processString(value, actualSubAnnot.getProcess(), null);
                        } else {
                            value = subAnnot.isCaptureXml()? apValue.evalXPath() != -1 ? getXml(apValue) : "" : apValue.evalXPathToString();
                            value = processString(value, subAnnot.getProcess(), null);
                        }
                        if (!alreadySeen.contains(subannotationName)) {
                            // First occurrence of this annotation
                            annotation(subannotationName, value, 1, indexAtPositions);
                            alreadySeen.add(subannotationName);
                        } else {
                            // Subsequent occurrence of this annotation
                            annotation(subannotationName, value, 0, indexAtPositions);
                        }
                    }
                    releaseXPathExpression(apForEach);
                    releaseXPathExpression(apName);
                    navpop();
                } else {
                    // Regular subannotation; just the fieldName and an XPath expression for the value
                    String subValuePath = subAnnot.getValuePath();
                    String reuseValue = subValuePath.equals(valuePath) && subAnnot.isCaptureXml() == annotation.isCaptureXml() ? annotValue : null;
                    findAnnotationMatches(subAnnot, subValuePath, indexAtPositions, reuseValue);
                }
                releaseXPathExpression(apValue);
            }

        } finally {
            if (basePath != null) {
                // We pushed when we navigated to the base element; pop now.
                navpop();
            }
        }
    }

    protected String findAnnotationMatches(ConfigAnnotation annotation, String valuePath,
            List<Integer> indexAtPositions, String reuseValueFromParentAnnot)
            throws XPathParseException, XPathEvalException, NavException {
        String annotValueForReuse = null;
        boolean evalXml = annotation.isCaptureXml();

        if (reuseValueFromParentAnnot == null) {
            navpush();
            XPathExpression apValuePath = acquireXPathExpression(valuePath);
            if (annotation.isMultipleValues()) {
                // If we don't want duplicates, keep track of the values we've indexed
                Set<String> valuesAlreadyIndexed = null;
                if (!annotation.isAllowDuplicateValues())
                    valuesAlreadyIndexed = new HashSet<>();
                
                // Multiple matches will be indexed at the same position.
                XPathExpression apValue = acquireXPathExpression(".");
                boolean firstValue = true;
                while (apValuePath.evalXPath() != -1) {
                    apValue.resetXPath();
                    String unprocessedValue = evalXml ? apValue.evalXPath() != -1 ? getXml(apValue) : "" : apValue.evalXPathToString();
                    for (String value : processStringMultipleValues(unprocessedValue, annotation.getProcess(), null)) {
                        if (valuesAlreadyIndexed == null || !valuesAlreadyIndexed.contains(value.toLowerCase())) {
                            int increment = firstValue ? 1 : 0;
                            annotation(annotation.getName(), value, increment, indexAtPositions);
                            firstValue = false;
                            if (valuesAlreadyIndexed != null) {
                                // Keep track of values seen so we can discard duplicates
                                valuesAlreadyIndexed.add(value.toLowerCase());
                            }
                        }
                    }
                }
                releaseXPathExpression(apValue);

                // No annotations have been added, the result of the xPath query must have been empty.
                if (firstValue) {
                    // Add default value(s)
                    for (String value : processStringMultipleValues("", annotation.getProcess(), null)) {
                        int increment = firstValue ? 1 : 0;
                        annotation(annotation.getName(), value, increment, indexAtPositions);
                    }
                }
            } else {
                // Single value expected
                annotValueForReuse = evalXml ? apValuePath.evalXPath() != -1 ? getXml(apValuePath) : "" : apValuePath.evalXPathToString();
                String annotValue = processString(annotValueForReuse, annotation.getProcess(), null);
                annotation(annotation.getName(), annotValue, 1, indexAtPositions);
            }
            releaseXPathExpression(apValuePath);
            navpop();
        } else {
            // We can reuse the value from the parent annotation, with different processing
            annotValueForReuse = reuseValueFromParentAnnot;
            String annotValue = processString(annotValueForReuse, annotation.getProcess(), null);
            annotation(annotation.getName(), annotValue, 1, indexAtPositions);
        }
        return annotValueForReuse; // so subannotations can reuse it if they use the same valuePath
    }

    @Override
    public void indexSpecificDocument(String documentXPath) {
        super.indexSpecificDocument(documentXPath);

        try {
            // Parse use VTD-XML
            vg = new VTDGen();
            vg.setDoc(inputDocument);
            vg.parse(config.isNamespaceAware());

            nav = vg.getNav();

            boolean docDone = false;
            if (documentXPath != null) {
                // Find our specific document
                XPathExpression documents = acquireXPathExpression(documentXPath);
                while (documents.evalXPath() != -1) {
                    if (docDone)
                        throw new BlackLabRuntimeException(
                                "Document link " + documentXPath + " matched multiple documents in " + documentName);
                    indexDocument();
                    docDone = true;
                }
                releaseXPathExpression(documents);
            } else {
                // Process whole file; must be 1 document
                XPathExpression documents = acquireXPathExpression(config.getDocumentPath());
                while (documents.evalXPath() != -1) {
                    if (docDone)
                        throw new BlackLabRuntimeException(
                                "Linked file contains multiple documents (and no document path given) in "
                                        + documentName);
                    indexDocument();
                    docDone = true;
                }
                releaseXPathExpression(documents);
            }
        } catch (Exception e1) {
            throw BlackLabRuntimeException.wrap(e1);
        }
    }

    /**
     * Add open and close InlineObject objects for the current element to the list.
     *
     * @param inlineObject list to add the new open/close tag objects to
     * @throws NavException
     */
    private void collectInlineTag(Node tag, List<InlineObject> inlineObject) {
        // Get the element and content fragments
        // (element fragment = from start of start tag to end of end tag;
        //  content fragment = from end of start tag to start of end tag)
        long elementFragment = nav.getElementFragment();
        int startTagOffset = (int) elementFragment;
        int endTagOffset;
        long contentFragment = nav.getContentFragment();
        if (contentFragment == -1) {
            // Empty (self-closing) element.
            endTagOffset = startTagOffset;
        } else {
            // Regular element with separate open and close tags.
            int contentOffset = (int) contentFragment;
            int contentLength = (int) (contentFragment >> 32);
            int contentEnd = contentOffset + contentLength;
            endTagOffset = contentEnd;
        }

        // Find element name
        int currentIndex = nav.getCurrentIndex();
        String elementName = tag.getNodeName();
        NamedNodeMap attributes = tag.getAttributes();
        Map<String,String> atts = new HashMap<>(attributes.getLength());
        for (int i = 0;  i < attributes.getLength();i++) {
            atts.put(attributes.item(i).getNodeName(),attributes.item(i).getNodeValue());
        }

        // Add the inline tags to the list
        InlineObject openTag = new InlineObject(elementName, startTagOffset, InlineObject.InlineObjectType.OPEN_TAG,atts);
        InlineObject closeTag = new InlineObject(elementName, endTagOffset, InlineObject.InlineObjectType.CLOSE_TAG, null);
        openTag.setMatchingTag(closeTag);
        closeTag.setMatchingTag(openTag);
        inlineObject.add(openTag);
        inlineObject.add(closeTag);
    }

    /**
     * Add InlineObject for a punctuation text node.
     *
     * @param inlineObjects list to add the punct object to
     * @param text
     * @throws NavException
     */
    private void collectPunct(List<InlineObject> inlineObjects, String text) {
        int i = nav.getCurrentIndex();
        int offset = nav.getTokenOffset(i);
//		int length = nav.getTokenLength(i);

        // Make sure we only keep 1 copy of identical punct texts in memory
        text = dedupe(StringUtil.normalizeWhitespace(text));

        // Add the punct to the list
        inlineObjects.add(new InlineObject(text, offset, InlineObjectType.PUNCTUATION, null));
    }

    /**
     * Gets attribute map for current element
     */
    private Map<String, String> getAttributes() {
        navpush();
        XPathExpression apAttr = new XPathExpression(nav);
        apAttr.selectAttr("*");
        int i = -1;
        Map<String, String> attr = new HashMap<>();
        try {
            while ((i = apAttr.iterateAttr()) != -1) {
                String name = nav.toString(i);
                String value = nav.toString(i + 1);
                attr.put(name, value);
            }
        } catch (NavException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
        navpop();
        return attr;
    }

    @Override
    protected void startDocument() {
        super.startDocument();

        try {
            long fragment = nav.getElementFragment();
            documentByteOffset = (int) fragment;
            documentLengthBytes = (int) (fragment >> 32);
        } catch (NavException e) {
            throw BlackLabRuntimeException.wrap(e);
        }

        lastCharPosition = 0;
        lastCharPositionByteOffset = documentByteOffset;
    }

    @Override
    protected void storeDocument() {
        storeWholeDocument(new String(inputDocument, documentByteOffset, documentLengthBytes, StandardCharsets.UTF_8));
    }

    @Override
    protected int getCharacterPosition() {
        // VTD-XML provides no way of getting the current character position,
        // only the byte position.
        // In order to keep track of character position (which we need for Lucene's term vector),
        // we fetch the bytes processed since this method was last called, convert them to a String,
        // and use the string length to adjust the character position.
        // Note that this only works if this method is called for increasing byte positions,
        // which is true because we only use it for word tags.
        try {
            int currentByteOffset = getCurrentByteOffset();
            if (currentByteOffset > lastCharPositionByteOffset) {
                int length = currentByteOffset - lastCharPositionByteOffset;
                String str = new String(inputDocument, lastCharPositionByteOffset, length, StandardCharsets.UTF_8);
                lastCharPosition += str.length();
                lastCharPositionByteOffset = currentByteOffset;
            }
            return lastCharPosition;
        } catch (NavException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    protected int getCurrentByteOffset() throws NavException {
        if (fragPos == FragmentPosition.BEFORE_OPEN_TAG || fragPos == FragmentPosition.AFTER_CLOSE_TAG) {
            long elFrag = nav.getElementFragment();
            int elOffset = (int) elFrag;
            if (fragPos == FragmentPosition.AFTER_CLOSE_TAG) {
                int elLength = (int) (elFrag >> 32);
                return elOffset + elLength;
            }
            return elOffset;
        }
        long contFrag = nav.getContentFragment();
        int contOffset = (int) contFrag;
        if (fragPos == FragmentPosition.BEFORE_CLOSE_TAG) {
            int contLength = (int) (contFrag >> 32);
            return contOffset + contLength;
        }
        return contOffset;
    }

    protected void setCurrentAnnotatedField(ConfigAnnotatedField annotatedField) {
        currentAnnotatedFieldConfig = annotatedField;
        setCurrentAnnotatedFieldName(currentAnnotatedFieldConfig.getName());
    }

    /** Get the raw xml from the document at the current position 
     * @throws NavException */
    private static String getXml(XPathExpression ap) throws NavException {
        long frag = ap.getNav().getContentFragment();
        if (frag == -1) {
            return "";
        }

        int offset = (int) frag;
        int length = (int) (frag >> 32);

        return ap.getNav().toRawString(offset, length);
    }
}
