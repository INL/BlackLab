package nl.inl.blacklab.indexers.config;

import net.sf.saxon.Configuration;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.TreeInfo;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.xpath.XPathFactoryImpl;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.InvalidConfiguration;
import nl.inl.blacklab.exceptions.MalformedInputFile;
import org.apache.commons.io.IOUtils;
import org.xml.sax.*;
import org.xml.sax.helpers.XMLReaderFactory;

import javax.xml.namespace.NamespaceContext;
import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;
import javax.xml.xpath.*;
import java.io.CharArrayReader;
import java.io.IOException;
import java.io.Reader;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A helper for indexing using saxonica
 */
public class SaxonicaHelper {

    private static final ThreadLocal<XPathFactory> X_PATH_FACTORY_THREAD_LOCAL = new InheritableThreadLocal<XPathFactory>() {
        @Override
        protected XPathFactory initialValue() {
            return new XPathFactoryImpl();
        }
    };


    private final TreeInfo contents;

    private Map<Integer, Integer> cumulativeColsPerLine = new HashMap<>();

    private class EndPos {
        private final String qName;
        private final int charPos;

        public EndPos(String qName, int line, int col) {
            this.qName = qName;
            charPos = getCharPos(line, col);
        }
    }

    /**
     * collects recorded (during parse) sax end position (the > of the end tag)
     */
    private final List<EndPos> endPosList = new ArrayList<>(50 * 300);
    /**
     * connects recorded (during parse) sax start position (the > of the start tag) to the calculated start pos (the &lt; at the beginning of the start tag)
     */
    private final Map<Integer, Integer> startPosMap = new HashMap<>(50 * 300);

    private int charPos = 0;

    private void setCharPos(NodeInfo nodeInfo) {
        charPos = getCharPos(nodeInfo);
    }

    private int getCharPos(NodeInfo nodeInfo) {
        return getCharPos(nodeInfo.getLineNumber(), nodeInfo.getColumnNumber());
    }

    private int getCharPos(int lineNumber, int columnNumber) {
        int charsOnline = cumulativeColsPerLine.get(lineNumber) -
                (lineNumber == 1 ? 0 : cumulativeColsPerLine.get(lineNumber - 1));
        return cumulativeColsPerLine.get(lineNumber)
                - (charsOnline - columnNumber)
                + (lineNumber - 1) * lineEnd
                - 1; // saxonica notifies columnNumber just after tag
    }

    short lineEnd = 1;

    private char[] chars;

    public SaxonicaHelper(Reader reader, ConfigInputFormat blConfig) throws IOException, SAXException, XPathException {
        chars = IOUtils.toCharArray(reader);
        CharArrayReader stream = new CharArrayReader(chars);
        AtomicInteger line = new AtomicInteger();
        AtomicInteger cols = new AtomicInteger();
        IOUtils.lineIterator(stream).forEachRemaining(l ->
                cumulativeColsPerLine.put(line.incrementAndGet(), cols.addAndGet(l.length())));
        stream.reset();
        if (cumulativeColsPerLine.size() > 1) {
            int i = -1;
            while ((i = stream.read()) != -1) {
                if (i == '\r') {
                    lineEnd = 2;
                    break;
                }
            }
            stream.reset();
        }
        MyContentHandler myContentHandler = new MyContentHandler();
        XMLReader xmlReader = XMLReaderFactory.createXMLReader();
        xmlReader.setContentHandler(myContentHandler);
        XMLReader wrapper = new MyXMLReader(xmlReader);
        InputSource inputSrc = new InputSource(stream);
        Source source = new SAXSource(wrapper, inputSrc);
        Configuration config = ((XPathFactoryImpl) X_PATH_FACTORY_THREAD_LOCAL.get()).getConfiguration();
        config.setLineNumbering(true);
        contents = config.buildDocumentTree(source);
//        chars = null;
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
         * instead of silently replacing the handler we set it in our wrapping handler,
         * {@link MyContentHandler#setSaxonHandler(ContentHandler)}.
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

        @Override
        public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
            int end = getCharPos(locator.getLineNumber(), locator.getColumnNumber());
            int begin = end;
            boolean selfClosing=false;
            for (int i = end - 1; i > 0; i--) {
                if ('<' == chars[i]) {
                    begin = i;
                    selfClosing=begin==end;
                    break;
                }
            }
            if (begin==end && !selfClosing) {
                throw new BlackLabRuntimeException(String.format("No '<' found for %s at line %d, col %d, charpos %d",
                        qName,locator.getLineNumber(),locator.getColumnNumber(),end));
            }
            startPosMap.put(end,begin);
            saxonHandler.startElement(uri, localName, qName, atts);
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            endPosList.add(new EndPos(qName, locator.getLineNumber(), locator.getColumnNumber()));
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
     * Map from XPath expression to compiled XPath.
     */
    private Map<String, XPathExpression> compiledXPaths = new HashMap<>();

    private final NSCTX namespaces = new NSCTX();

    private static class NSCTX implements NamespaceContext {
        private final Map<String, String> ns = new HashMap<>(3);
        ;

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
    XPathExpression acquireXPathExpression(String xpathExpr) {
        XPathExpression xPathExpression = compiledXPaths.get(xpathExpr);
        if (xPathExpression == null) {
            try {
                xPathExpression = xPath.compile(xpathExpr);
                compiledXPaths.put(xpathExpr,xPathExpression);
            } catch (XPathExpressionException e) {
                throw new BlackLabRuntimeException("Error in XPath expression " + xpathExpr + " : " + e.getMessage(), e);
            }
        }
        return xPathExpression;
    }

    /**
     * find where in the character[] of the source the closing tag ends
     * @param nodeInfo the node to find the endtag for
     * @param num the occurrence of the node in the source
     * @return
     */
    public int findClosingTagPosition(NodeInfo nodeInfo, int num) {
        return endPosList.stream().filter(ep -> ep.qName.equals(nodeInfo.getDisplayName())).skip(num - 1)
                .findFirst().orElseThrow(() -> new BlackLabRuntimeException("No end position for " + nodeInfo)).charPos;
    }

    protected void test(NodeInfo doc, ConfigAnnotatedField annotatedField)
            throws XPathExpressionException {
        XPathExpression wordpath = acquireXPathExpression(annotatedField.getWordsPath());
        AtomicInteger wNum = new AtomicInteger();
        ((List<NodeInfo>) wordpath.evaluate(contents, XPathConstants.NODESET))
                .forEach(word -> {
                        setCharPos(word);
                        int endPos = findClosingTagPosition(word,wNum.incrementAndGet());
//            System.out.println(new String(Arrays.copyOfRange(chars, startPosMap.get(charPos), endPos)) +
//                    ": " + startPosMap.get(charPos) + " - " + endPos);
                        annotatedField.getAnnotations().entrySet().forEach(an -> {
                            ConfigAnnotation annotation = an.getValue();
                            XPathExpression annXPathExpression = acquireXPathExpression(annotation.getValuePath());
                            try {
                            ((List) annXPathExpression.evaluate(word, XPathConstants.NODESET))
                                    .forEach(o -> {
                                        if (o instanceof NodeInfo) {
                                            NodeInfo text = (NodeInfo) o;
                                        } else {
                                            System.out.println(o.getClass() + ": " + o);
                                        }
                                    });
                            } catch (XPathExpressionException e) {
                                throw new InvalidConfiguration(e.getMessage(), e);
                            }
                        });
                });
    }

    int getCharPos() {
        return charPos;
    }

    TreeInfo getContents() {
        return contents;
    }
}
