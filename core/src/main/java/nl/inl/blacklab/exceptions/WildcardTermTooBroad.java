package nl.inl.blacklab.exceptions;

public class WildcardTermTooBroad extends InvalidQuery {

    public WildcardTermTooBroad() {
        super(null);
    }

    public WildcardTermTooBroad(String message, Throwable e) {
        super(message, e);
    }

    public WildcardTermTooBroad(String message) {
        super(message);
    }

    public WildcardTermTooBroad(Throwable e) {
        super("A term in your query matches too many terms in the index.", e);
    }

}
