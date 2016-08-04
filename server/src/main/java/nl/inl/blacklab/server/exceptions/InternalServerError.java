package nl.inl.blacklab.server.exceptions;

import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;

import nl.inl.blacklab.server.requesthandlers.Response;

public class InternalServerError extends BlsException {
	static final Logger logger = Logger.getLogger(Response.class);

	private int internalErrorCode;

	public int getInternalErrorCode() {
		return internalErrorCode;
	}

	public InternalServerError(int code) {
		this("Internal error", code, null);
		logger.debug("INTERNAL ERROR " + internalErrorCode + " (no message)");
	}

	public InternalServerError(String msg, int internalErrorCode) {
		this(msg, internalErrorCode, null);
		logger.debug("INTERNAL ERROR " + internalErrorCode + ":" + msg);
	}

	public InternalServerError(String msg, int internalErrorCode, Throwable cause) {
		super(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", msg + (cause == null ? "" : " (" + cause + ")"), cause);
		this.internalErrorCode = internalErrorCode;
		logger.debug("INTERNAL ERROR " + internalErrorCode + (cause == null ? "" : ":") );
		if (cause != null)
			cause.printStackTrace();
	}

}
