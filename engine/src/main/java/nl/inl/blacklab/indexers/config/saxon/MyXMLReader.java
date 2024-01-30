package nl.inl.blacklab.indexers.config.saxon;

import java.io.IOException;

import org.xml.sax.ContentHandler;
import org.xml.sax.DTDHandler;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;

/**
 * Needed to not lose our ContentHandler, which would otherwise be overridden by Saxon.
 */
public class MyXMLReader implements XMLReader {

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
