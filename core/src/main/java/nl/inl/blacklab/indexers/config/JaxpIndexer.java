package nl.inl.blacklab.indexers.config;

import net.sf.saxon.Configuration;
import net.sf.saxon.om.TreeInfo;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.xpath.XPathFactoryImpl;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.MalformedInputFile;
import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.index.Indexer;
import nl.inl.blacklab.index.annotated.AnnotatedFieldWriter;
import org.apache.commons.io.IOUtils;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.namespace.NamespaceContext;
import javax.xml.transform.sax.SAXSource;
import javax.xml.xpath.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An indexer configured using full XPath expressions.
 */
public class JaxpIndexer extends DocIndexerConfig {

    private ThreadLocal<XPathFactory> X_PATH_FACTORY_THREAD_LOCAL = new InheritableThreadLocal<XPathFactory>() {
        @Override
        protected XPathFactory initialValue() {
            return new XPathFactoryImpl();
        }
    };


    private TreeInfo contents = null;
    private Map<Integer, Integer> cumulativeColsPerLine = new HashMap<>();

    @Override
    public void setDocument(Reader reader) {
        try {
            byte[] bytes = IOUtils.toByteArray(reader, Indexer.DEFAULT_INPUT_ENCODING);
            ByteArrayInputStream stream = new ByteArrayInputStream(bytes);
            AtomicInteger line = new AtomicInteger();
            AtomicInteger cols = new AtomicInteger();
            IOUtils.lineIterator(stream, Indexer.DEFAULT_INPUT_ENCODING).forEachRemaining(l ->
                    cumulativeColsPerLine.put(line.getAndIncrement(), cols.addAndGet(l.length())));
            stream.reset();
            InputSource inputSrc = new InputSource(stream);
            SAXSource saxSrc = new SAXSource(inputSrc);
            Configuration config = ((XPathFactoryImpl) X_PATH_FACTORY_THREAD_LOCAL.get()).getConfiguration();
            contents = config.buildDocumentTree(saxSrc);
        } catch (IOException | XPathException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    /** Map from XPath expression to compiled XPath. */
    private Map<String, XPathExpression> compiledXPaths = new HashMap<>();

    /**
     * XPathExpressions that are currently being used. We need to keep track of this to be
     * able to re-add them to compiledXpath with the correct XPath expression later.
     */
    private Map<XPathExpression, String> XPathExpressionsInUse = new HashMap<>();

    private final NSCTX namespaces = new NSCTX();

    private static class NSCTX implements NamespaceContext {
        private final Map<String,String> ns = new HashMap<>(3);;

        void add(String prefix, String uri) {
            if (uri.equals(ns.get(prefix))) return;
            ns.put(prefix, uri);
        }

        @Override
        public String getNamespaceURI(String prefix) {
            return ns.get(prefix);
        }

        @Override
        public String getPrefix(String namespaceURI) {
            return ns.entrySet().stream().filter(e -> e.getValue().equals(namespaceURI)).map(e->e.getKey()).findFirst().orElse(null);
        }

        @Override
        public Iterator getPrefixes(String namespaceURI) {
            return ns.keySet().iterator();
        }
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
                namespaces.add("xml", "http://www.w3.org/XML/1998/namespace");
                for (Map.Entry<String, String> e : config.getNamespaces().entrySet()) {
                    namespaces.add(e.getKey(), e.getValue());
                }
                xPath.setNamespaceContext(namespaces);
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
            List docs = (List) documents.evaluate(contents, XPathConstants.NODESET);
            for (Object doc : docs) {
                indexDocument((Node)doc);
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
//        Map<String, Integer> tokenPositionsMap = new HashMap<>();
//
//        // Determine some useful stuff about the field we're processing
//        // and store in instance variables so our methods can access them
////        setCurrentAnnotatedField(annotatedField);
//
//        // Precompile XPaths for words, evalToString, inline tags, punct and (sub)annotations
//        XPathExpression words = acquireXPathExpression(annotatedField.getWordsPath());
//        XPathExpression apEvalToString = acquireXPathExpression(".");
//        List<XPathExpression> apsInlineTag = new ArrayList<>();
//        for (ConfigInlineTag inlineTag : annotatedField.getInlineTags()) {
//            XPathExpression apInlineTag = acquireXPathExpression(inlineTag.getPath());
//            apsInlineTag.add(apInlineTag);
//        }
//        XPathExpression apPunct = null;
//        if (annotatedField.getPunctPath() != null)
//            apPunct = acquireXPathExpression(annotatedField.getPunctPath());
//        String tokenPositionIdPath = annotatedField.getTokenPositionIdPath();
//        XPathExpression apTokenPositionId = null;
//        if (tokenPositionIdPath != null) {
//            apTokenPositionId = acquireXPathExpression(tokenPositionIdPath);
//        }
//
//        // For each body element...
//        // (there's usually only one, but there's no reason to limit it)
//        XPathExpression bodies = acquireXPathExpression(annotatedField.getContainerPath());
//        AnnotatedFieldWriter annotatedFieldWriter = getAnnotatedField(annotatedField.getName());
//        NodeList bodyList = (NodeList) bodies.evaluate(doc,XPathConstants.NODESET);
//        for (int i = 0; i < bodyList.getLength(); i++) {
//            Node body = bodyList.item(i);
//
//            // First we find all inline elements (stuff like s, p, b, etc.) and store
//            // the locations of their start and end tags in a sorted list.
//            // This way, we can keep track of between which words these tags occur.
//            // For end tags, we will update the payload of the start tag when we encounter it,
//            // just like we do in our SAX parsers.
//            List<InlineObject> tagsAndPunct = new ArrayList<>();
//            for (XPathExpression apInlineTag : apsInlineTag) {
//                NodeList inlineTags = (NodeList) apInlineTag.evaluate(body,XPathConstants.NODESET);
//                for (i = 0; i < inlineTags.getLength(); i++) {
//                    Node inlineTag = inlineTags.item(i);
//                    collectInlineTag(inlineTag, tagsAndPunct);
//                }
//            }
//            setAddDefaultPunctuation(true);
//            if (apPunct != null) {
//                // We have punctuation occurring between word tags (as opposed to
//                // punctuation that is tagged as a word itself). Collect this punctuation.
//                setAddDefaultPunctuation(false);
//                navpush();
//                NodeList punctTags = (NodeList) apPunct.evaluate(body,XPathConstants.NODESET);
//                for (i = 0; i < punctTags.getLength(); i++) {
//                    Node p = punctTags.item(i);
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
//            // Now, find all words, keeping track of what inline objects occur in between.
//            navpush();
//
//            // first find all words and sort the list -- words are returned out of order when they are at different nesting levels
//            // since the xpath spec doesn't enforce any order, there's nothing we can do
//            // so record their positions, sort the list, then restore the position and carry on
//            List<Pair<Integer, BookMark>> wordPositions = new ArrayList<>();
//            NodeList ws = (NodeList)words.evaluate(body,XPathConstants.NODESET);
//            for (i =0; i< ws.getLength();i++) {
//                Node w = ws.item(i);
//                BookMark b = new BookMark(nav);
//                b.setCursorPosition();
//                wordPositions.add(Pair.of(nav.getCurrentIndex(), b));
//            }
//            wordPositions.sort((a, b) -> a.getKey().compareTo(b.getKey()));
//
//            for (Pair<Integer, BookMark> wordPosition : wordPositions) {
//                wordPosition.getValue().setCursorPosition();
//
//                // Capture tokenPositionId for this token position?
//                if (apTokenPositionId != null) {
//                    apTokenPositionId.resetXPath();
//                    String tokenPositionId = apTokenPositionId.evalXPathToString();
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

    protected void navpush() {
//        fragPosStack.add(fragPos);
//        fragPos = FragmentPosition.BEFORE_OPEN_TAG;
    }

    protected void navpop() {
//        fragPos = fragPosStack.remove(fragPosStack.size() - 1);
    }

    protected void processMetadataBlock(ConfigMetadataBlock b) {
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

    @Override
    protected int getCharacterPosition() {
        return 0;
    }
}
