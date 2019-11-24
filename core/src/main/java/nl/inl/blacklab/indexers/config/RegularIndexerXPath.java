package nl.inl.blacklab.indexers.config;

import java.io.Reader;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;

import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.*;
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

    /** Map from XPath expression to compiled XPath. */
    private Map<String, XPathExpression> compiledXPaths = new HashMap<>();

    /**
     * XPathExpressions that are currently being used. We need to keep track of this to be
     * able to re-add them to compiledXpath with the correct XPath expression later.
     */
    private Map<XPathExpression, String> XPathExpressionsInUse = new HashMap<>();

    private Map<String, NamespaceContext> namespaces = new HashMap<>(3);

    private NamespaceContext getNamespace(String prefix, String uri) {
        String key = prefix+":"+uri;
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
        XPathExpression ap = compiledXPaths.remove(xpathExpr);
        if (ap == null) {
            XPath xPath  = X_PATH_FACTORY_THREAD_LOCAL.get().newXPath();
            if (config.isNamespaceAware()) {
                xPath.setNamespaceContext(getNamespace("xml", "http://www.w3.org/XML/1998/namespace"));
                for (Entry<String, String> e : config.getNamespaces().entrySet()) {
                    xPath.setNamespaceContext(getNamespace(e.getKey(), e.getValue()));
                }
            }
            try {
                ap = xPath.compile(xpathExpr);
            } catch (XPathExpressionException e) {
                throw new BlackLabRuntimeException("Error in XPath expression " + xpathExpr + " : " + e.getMessage(), e);
            }
        }
        XPathExpressionsInUse.put(ap, xpathExpr);
        return ap;
    }

    private void releaseXPathExpression(XPathExpression ap) {
        String xpathExpr = XPathExpressionsInUse.remove(ap);
        compiledXPaths.put(xpathExpr, ap);
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
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    protected int getCharacterPosition() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }


}
