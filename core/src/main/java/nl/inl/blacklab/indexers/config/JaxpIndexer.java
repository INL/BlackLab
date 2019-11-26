package nl.inl.blacklab.indexers.config;

import net.sf.saxon.Configuration;
import net.sf.saxon.om.TreeInfo;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.xpath.XPathFactoryImpl;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.index.Indexer;
import org.apache.commons.io.IOUtils;
import org.xml.sax.InputSource;

import javax.xml.transform.sax.SAXSource;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An indexer configured using full XPath expressions.
 */
public abstract class JaxpIndexer extends DocIndexerConfig {

    private final XPathFactory xpFactory = new XPathFactoryImpl();
    private final XPath xpExpression = xpFactory.newXPath();

    private TreeInfo treeInfo = null;
    private Map<Integer, Integer> cumulativeColsPerLine = new HashMap<>();

    @Override
    public void setDocument(Reader reader) {
        try {
            byte[] bytes = IOUtils.toByteArray(reader, Indexer.DEFAULT_INPUT_ENCODING);
            ByteArrayInputStream stream = new ByteArrayInputStream(bytes);
            AtomicInteger line = new AtomicInteger();
            IOUtils.lineIterator(stream, Indexer.DEFAULT_INPUT_ENCODING).forEachRemaining(l ->
                    cumulativeColsPerLine.put(line.getAndIncrement(), l.length()));
            stream.reset();
            InputSource inputSrc = new InputSource(stream);
            SAXSource saxSrc = new SAXSource(inputSrc);
            Configuration config = ((XPathFactoryImpl) xpFactory).getConfiguration();
            treeInfo = config.buildDocumentTree(saxSrc);
        } catch (IOException | XPathException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
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
}
