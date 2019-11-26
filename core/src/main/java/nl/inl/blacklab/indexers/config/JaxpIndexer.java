package nl.inl.blacklab.indexers.config;

/**
 * An indexer configured using full XPath expressions.
 */
public abstract class JaxpIndexer extends DocIndexerConfig {

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
