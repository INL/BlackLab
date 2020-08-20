package nl.inl.blacklab.indexers.config;

import java.io.CharArrayReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.io.IOUtils;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.DTDHandler;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;

import net.sf.saxon.Configuration;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.TreeInfo;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.xpath.XPathFactoryImpl;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.InvalidConfiguration;

/**
 * A helper for indexing using saxon
 */
class SaxonHelper {

    public static final int MAXDOCSIZEINMEMORY = 4_096_000;
    private static final ThreadLocal<XPathFactory> X_PATH_FACTORY_THREAD_LOCAL = new InheritableThreadLocal<XPathFactory>() {
        @Override
        protected XPathFactory initialValue() {
            return new XPathFactoryImpl();
        }
    };


    /**
     * The document tree needed for processing xpath's
     */
    private TreeInfo contents;

    /**
     * cumulative number of columns per line, used to translate line/column into character position
     */
    private Map<Integer, Integer> cumulativeColsPerLine = new HashMap<>();

    private class StartEndPos {
        private final int startPos;
        private int endPos;

        public StartEndPos(int realStart) {
            startPos = realStart;
        }

        public void setEndPos(int endPos) {
            this.endPos = endPos;
        }
        
    }

    /**
     * connects recorded sax positions (just after start and end tags) to the calculated
     * positions (the first char of start and end tags).
     */
    private Map<Integer, StartEndPos> startEndPosMap = new HashMap<>(50 * 300);

    private int getCharPos(NodeInfo nodeInfo) {
        return getCharPos(nodeInfo.getLineNumber(), nodeInfo.getColumnNumber());
    }

    /**
     * translation of recorded line and column number to character position in the document
     * @param lineNumber
     * @param columnNumber
     * @return
     */
    private int getCharPos(int lineNumber, int columnNumber) {
        int charsOnline = cumulativeColsPerLine.get(lineNumber) -
                (lineNumber == 1 ? 0 : cumulativeColsPerLine.get(lineNumber - 1));
        return cumulativeColsPerLine.get(lineNumber)
                - (charsOnline - columnNumber)
                + (lineNumber - 1) * lineEnd
                - 1; // saxon notifies columnNumber just after tag
    }

    int lineEnd = 1;

    private File documentDiskCache;

    /**
     * The document as a string, will be used for storing document and position calculation
     */
    private char[] document;

    SaxonHelper(Reader reader, ConfigInputFormat blConfig) throws IOException, SAXException, XPathException, ParserConfigurationException {
        // characters needed for calculating positions
        document = IOUtils.toCharArray(reader);
        CharArrayReader stream = new CharArrayReader(document);
        AtomicInteger line = new AtomicInteger();
        AtomicInteger cols = new AtomicInteger();
        // determine cumulative number of chars per line
        IOUtils.lineIterator(stream).forEachRemaining(l ->
                cumulativeColsPerLine.put(line.incrementAndGet(), cols.addAndGet(l.length())));
        stream.reset();
        if (cumulativeColsPerLine.size() > 1) {
            int i = -1;
            while ((i = stream.read()) != -1) {
                if (i == '\r') {
                    // windows file
                    lineEnd = 2;
                    break;
                }
            }
            stream.reset();
        }
        // make sure our content handler doesn't get overwritten by saxon
        MyContentHandler myContentHandler = new MyContentHandler();
        SAXParserFactory parserFactory = SAXParserFactory.newInstance();
        SAXParser parser = parserFactory.newSAXParser();
        XMLReader xmlReader = parser.getXMLReader(); //XMLReaderFactory.createXMLReader();
        xmlReader.setContentHandler(myContentHandler);
        XMLReader wrapper = new MyXMLReader(xmlReader);
        // regular parsing with line numbering enabled
        InputSource inputSrc = new InputSource(stream);
        Source source = new SAXSource(wrapper, inputSrc);
        Configuration config = ((XPathFactoryImpl) X_PATH_FACTORY_THREAD_LOCAL.get()).getConfiguration();
        config.setLineNumbering(true);
        contents = config.buildDocumentTree(source);
        if (document.length / 2 > MAXDOCSIZEINMEMORY) {
//            System.out.println("Disk cache for document, length " + (chars.length / 2));
            documentDiskCache = File.createTempFile("blDocToIndex",null);
            documentDiskCache.deleteOnExit();
            IOUtils.write(document, new FileWriter(documentDiskCache));
            document = null;
        }
        // setup namespace aware xpath that will compile xpath expressions
        xPath = X_PATH_FACTORY_THREAD_LOCAL.get().newXPath();
        if (blConfig.isNamespaceAware()) {
            namespaces.add("xml", "http://www.w3.org/XML/1998/namespace");
            for (Map.Entry<String, String> e : blConfig.getNamespaces().entrySet()) {
                namespaces.add(e.getKey(), e.getValue());
            }
            xPath.setNamespaceContext(namespaces);
        }
    }

    private XPath xPath;

    /**
     * Needed to not loose our contenthandler, which would otherwise be overridden by saxon.
     */
    private static class MyXMLReader implements XMLReader {
        private final XMLReader wrappedReader;
        private final MyContentHandler handler;

        public MyXMLReader(XMLReader wrappedReader) {
            this.wrappedReader = wrappedReader;
            handler = (MyContentHandler) wrappedReader.getContentHandler();
        }

        @Override
        public boolean getFeature(String name) throws SAXNotRecognizedException, SAXNotSupportedException {
            return wrappedReader.getFeature(name);
        }

        @Override
        public void setFeature(String name, boolean value) throws SAXNotRecognizedException, SAXNotSupportedException {
            wrappedReader.setFeature(name, value);
        }

        @Override
        public Object getProperty(String name) throws SAXNotRecognizedException, SAXNotSupportedException {
            return wrappedReader.getProperty(name);
        }

        @Override
        public void setProperty(String name, Object value) throws SAXNotRecognizedException, SAXNotSupportedException {
            wrappedReader.setProperty(name, value);
        }

        @Override
        public void setEntityResolver(EntityResolver resolver) {
            wrappedReader.setEntityResolver(resolver);
        }

        @Override
        public EntityResolver getEntityResolver() {
            return wrappedReader.getEntityResolver();
        }

        @Override
        public void setDTDHandler(DTDHandler handler) {
            wrappedReader.setDTDHandler(handler);
        }

        @Override
        public DTDHandler getDTDHandler() {
            return wrappedReader.getDTDHandler();
        }

        /**
         * instead of silently replacing the handler we set it in our wrapping handler
         * that we need for positions.
         *
         * @param handler
         */
        @Override
        public void setContentHandler(ContentHandler handler) {
            this.handler.setSaxonHandler(handler);
        }

        @Override
        public ContentHandler getContentHandler() {
            return this.handler;
        }

        @Override
        public void setErrorHandler(ErrorHandler handler) {
            wrappedReader.setErrorHandler(handler);
        }

        @Override
        public ErrorHandler getErrorHandler() {
            return wrappedReader.getErrorHandler();
        }

        @Override
        public void parse(InputSource input) throws IOException, SAXException {
            wrappedReader.parse(input);
        }

        @Override
        public void parse(String systemId) throws IOException, SAXException {
            wrappedReader.parse(systemId);
        }
    }

    /**
     * handler that can access the Locator to determine positions in the source.
     */
    private class MyContentHandler implements ContentHandler {

        private ContentHandler saxonHandler = this;
        private Locator locator;

        private void setSaxonHandler(ContentHandler saxonHandler) {
            this.saxonHandler = saxonHandler;
        }

        @Override
        public void setDocumentLocator(Locator locator) {
            saxonHandler.setDocumentLocator(locator);
            this.locator = locator;
        }

        @Override
        public void startDocument() throws SAXException {
            saxonHandler.startDocument();
        }

        @Override
        public void endDocument() throws SAXException {
            saxonHandler.endDocument();
        }

        @Override
        public void startPrefixMapping(String prefix, String uri) throws SAXException {
            saxonHandler.startPrefixMapping(prefix, uri);
        }

        @Override
        public void endPrefixMapping(String prefix) throws SAXException {
            saxonHandler.endPrefixMapping(prefix);
        }
        
        private Deque<StartEndPos> elStack = new ArrayDeque<>();

        @Override
        public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
            int end = getCharPos(locator.getLineNumber(), locator.getColumnNumber());
            int begin = end;
            // now look back for the < character
            for (int i = end - 1; i > 0; i--) {
                if ('<' == document[i]) {
                    begin = i;
                    break;
                }
            }
            // NOTE more testing needed for self closing tags
            if (begin == end) {
                throw new BlackLabRuntimeException(String.format("No '<' found for %s at line %d, col %d, charpos %d",
                        qName, locator.getLineNumber(), locator.getColumnNumber(), end));
            }
            StartEndPos startEndPos = new StartEndPos(begin);
            elStack.push(startEndPos);
            startEndPosMap.put(end, startEndPos);
            saxonHandler.startElement(uri, localName, qName, atts);
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            elStack.pop().setEndPos(getCharPos(locator.getLineNumber(), locator.getColumnNumber()));
            saxonHandler.endElement(uri, localName, qName);
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            saxonHandler.characters(ch, start, length);
        }

        @Override
        public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
            saxonHandler.ignorableWhitespace(ch, start, length);
        }

        @Override
        public void processingInstruction(String target, String data) throws SAXException {
            saxonHandler.processingInstruction(target, data);
        }

        @Override
        public void skippedEntity(String name) throws SAXException {
            saxonHandler.skippedEntity(name);
        }


    }

    /**
     * Compiled XPaths for use in one thread.
     */
    private Map<String, XPathExpression> compiledXPaths = new HashMap<>();

    private final NSCTX namespaces = new NSCTX();

    private static class NSCTX implements NamespaceContext {
        private final Map<String, String> ns = new HashMap<>(3);

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
            return ns.entrySet().stream().filter(e -> e.getValue().equals(namespaceURI)).map(e -> e.getKey()).findFirst().orElse(null);
        }

        @Override
        public Iterator<String> getPrefixes(String namespaceURI) {
            return ns.keySet().iterator();
        }
    }

    /**
     * Create XPathExpression and cache.
     *
     * @param xpathExpr the xpath expression
     * @return the XPathExpression
     */
    private XPathExpression acquireXPathExpression(String xpathExpr) {
        XPathExpression xPathExpression = compiledXPaths.get(xpathExpr);
        if (xPathExpression == null) {
            try {
                xPathExpression = xPath.compile(xpathExpr);
                compiledXPaths.put(xpathExpr, xPathExpression);
            } catch (XPathExpressionException e) {
                throw new BlackLabRuntimeException("Error in XPath expression " + xpathExpr + " : " + e.getMessage(), e);
            }
        }
        return xPathExpression;
    }

    /**
     * Find results in a context, return a list of Objects. This approach is usefull if you don't know
     * the return type(s) in advance. This works for all return types of an xPath, also the ones that
     * return for example one boolean. Often a List&lt;NodeInfo> will be returned.
     *
     * @param xPath
     * @param context
     * @return
     */
    List<?> find(String xPath, Object context) throws XPathExpressionException {
        return (List<?>) acquireXPathExpression(xPath).evaluate(context,XPathConstants.NODESET);
    }

    /**
     * Calls {@link #find(String, Object)} and casts the result to List&lt;NodeInfo>, NOTE that the
     * resulting list may still contain Objects that are no NodeInfo, due to the way collections work,
     * Collections.checkedList won't help here.
     * @param xPath
     * @param context
     * @return
     * @throws XPathExpressionException
     */
    @SuppressWarnings("unchecked")
    List<NodeInfo> findNodes(String xPath, Object context) throws XPathExpressionException {
        return (List<NodeInfo>) find(xPath,context);
    }

    /**
     * return a string representation of an xpath result, using {@link NodeInfo#getStringValue()} or
     * String.valueOf. Handling multiple results should be done in xPath, for example concat.
     * @param xPath
     * @param context
     * @return
     * @throws XPathExpressionException
     * @throws InvalidConfiguration when the xpath returns multiple results
     */
    String getValue(String xPath, Object context) throws XPathExpressionException {
        List<?> list = find(xPath, context);
        if (list.size()==1) {
            Object o = list.get(0);
            if (o instanceof NodeInfo) {
                return ((NodeInfo) o).getStringValue();
            } else {
                return String.valueOf(o);
            }
        } else {
            if (list.isEmpty())
                return "";
            else
                    throw new InvalidConfiguration(
                            String.format("list %s contains multiple values, change your xpath %s to return one result or concatenate",
                                    list.stream().map(o -> o instanceof NodeInfo ? ((NodeInfo) o).toShortString() : String.valueOf(o))
                                            .collect(Collectors.toList()),xPath));
        }
    }

    /**
     * find where in the character[] of the source the closing tag ends
     *
     * @param nodeInfo the node to find the endtag for
     * @return
     */
    int findClosingTagPosition(NodeInfo nodeInfo) {
        return startEndPosMap.get(getCharPos(nodeInfo)).endPos;
    }

    void test(NodeInfo doc, ConfigAnnotatedField annotatedField)
            throws XPathExpressionException {
        for (NodeInfo word : findNodes(annotatedField.getWordsPath(),contents)) {
//            int start = getStartPos(word);
//            int endPos = getEndPos(word);
//            System.out.println(new String(Arrays.copyOfRange(chars, start, endPos)) +
//                    ": " + start + " - " + endPos);
            for (Map.Entry<String, ConfigAnnotation> an : annotatedField.getAnnotations().entrySet()) {
                ConfigAnnotation annotation = an.getValue();
                getValue(annotation.getValuePath(),word);
            }
        }
    }

    public static class NodeInfoComparator implements Comparator<NodeInfo> {

        @Override
        public int compare(NodeInfo o1, NodeInfo o2) {
            return o1.compareOrder(o2);
        }
    }

    public static final NodeInfoComparator NODINFO_COMPARATOR = new NodeInfoComparator();

    public static void documentOrder(List<NodeInfo> toOrder) {
        toOrder.sort(NODINFO_COMPARATOR);
    }

    /**
     * find the position of the starting character (&lt;) of a node in the characters of a document.
     * Note that CR and LF are included in the count. It is recomended to cache this number for use in
     * clients.
     * @param node
     * @return
     */
    int getStartPos(NodeInfo node) {
        return startEndPosMap.get(getCharPos(node)).startPos;
    }

    /**
     * find the position of the end character (>) of a node in the characters of a document.
     * Note that CR and LF are included in the count. It is recomended to cache this number for use in
     * clients.
     * @param node
     * @return
     */
    int getEndPos(NodeInfo node) {
        return findClosingTagPosition(node);
    }

    /**
     * The parsed tree of the document, can be used as context for xpaths.
     * @return
     */
    TreeInfo getContents() {
        return contents;
    }

    /**
     * returns the document as a string, sets all state in the helper to null
     * @return
     */
    String getDocument(boolean clean) {
        char[] rv = document;
        try {
            if (document ==null) {
                rv = IOUtils.toCharArray(new FileReader(documentDiskCache));
            }
        } catch (IOException e) {
            throw new BlackLabRuntimeException("unable to read document cache from disk");
        }
        if (clean) clean();
        return new String(rv);
    }

    void clean() {
        startEndPosMap=null;
        cumulativeColsPerLine=null;
        contents=null;
        document = null;
        compiledXPaths=null;
        if (documentDiskCache!=null) documentDiskCache.delete();
    }

}
