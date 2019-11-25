package nl.inl.blacklab.indexers.config;

import com.ximpleware.*;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.MalformedInputFile;
import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.index.Indexer;
import nl.inl.blacklab.index.annotated.AnnotatedFieldWriter;
import nl.inl.blacklab.index.annotated.AnnotationWriter;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Reader;
import java.util.*;
import java.util.Map.Entry;

/**
 * An indexer configured using XPath version depending on the library provided at runtime.
 */
public class RegularIndexerXPath extends DocIndexerConfig {

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
            processMetadataBlock(doc, b);
        }

        // For each linked document...
        for (ConfigLinkedDocument ld : config.getLinkedDocuments().values()) {
            processLinkedDocument(doc, ld);
        }

        endDocument();
    }


    protected void processAnnotatedField(Node doc, ConfigAnnotatedField annotatedField)
            throws XPathExpressionException {
        Map<String, Integer> tokenPositionsMap = new HashMap<>();

        // Determine some useful stuff about the field we're processing
        // and store in instance variables so our methods can access them
        setCurrentAnnotatedField(annotatedField);

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
                apPunct.resetXPath();
                while (apPunct.evalXPath() != -1) {
                    apEvalToString.resetXPath();
                    String punct = apEvalToString.evalXPathToString();
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
            words.resetXPath();

            // first find all words and sort the list -- words are returned out of order when they are at different nesting levels
            // since the xpath spec doesn't enforce any order, there's nothing we can do
            // so record their positions, sort the list, then restore the position and carry on
            List<Pair<Integer, BookMark>> wordPositions = new ArrayList<>();
            while (words.evalXPath() != -1) {
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

    /**
     * Add open and close InlineObject objects for the current element to the list.
     *
     * @param inlineObject list to add the new open/close tag objects to
     * @throws NavException
     */
    private void collectInlineTag(Node tag, List<InlineObject> inlineObject) throws NavException {
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

    @Override
    protected void storeDocument() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void close() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setDocument(Reader reader) {
        try {
            contents = IOUtils.toByteArray(reader, Indexer.DEFAULT_INPUT_ENCODING);
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    @Override
    protected int getCharacterPosition() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }


}
