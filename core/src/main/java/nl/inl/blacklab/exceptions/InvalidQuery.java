package nl.inl.blacklab.exceptions;

/**
 * There was something wrong with the query.
 *
 * A message is included that can be shown to the user.
 */
public class InvalidQuery extends BlackLabException {

    public InvalidQuery(String message) {
        super(message);
    }

    public InvalidQuery(String message, Throwable e) {
        super(message, e);
    }

}
