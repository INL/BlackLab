package nl.inl.blacklab.server.exceptions;


public class TooManyRequests extends BlsException {

	private static final int HTTP_TOO_MANY_REQUESTS = 429;

	public TooManyRequests(String msg) {
		super(HTTP_TOO_MANY_REQUESTS, "TOO_MANY_JOBS", msg);
	}

}
