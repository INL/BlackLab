package nl.inl.blacklab.search;

/**
 * There was something wrong with the query.
 *
 * A message is included that can be shown to the user.
 */
public class InvalidQueryException extends BLRuntimeException {

	public InvalidQueryException(String message) {
		super(message);
	}

}
