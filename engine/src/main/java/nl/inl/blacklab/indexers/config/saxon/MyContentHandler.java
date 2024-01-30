package nl.inl.blacklab.indexers.config.saxon;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

/**
 * Content handler with access to the Locator to determine positions in the source.
 */
public class MyContentHandler implements ContentHandler {

    private final CharPositionsTracker charPositions;

    /**
     * Saxon's default handler, which we defer to.
     */
    private ContentHandler saxonHandler = this;

    /**
     * Locator, for the line and column numbers
     */
    private Locator locator;

    public MyContentHandler(CharPositionsTracker charPositions) {
        this.charPositions = charPositions;
    }

    void setSaxonHandler(ContentHandler saxonHandler) {
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
        charPositions.addNextStartElement(qName, locator);
        saxonHandler.startElement(uri, localName, qName, atts);
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        charPositions.addNextEndElement(locator);
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
