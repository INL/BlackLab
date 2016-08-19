package nl.inl.blacklab.search;

public class RegexpTooLargeException extends InvalidQueryException {

	public RegexpTooLargeException() {
		super("Regular expression too large.");
	}

}
