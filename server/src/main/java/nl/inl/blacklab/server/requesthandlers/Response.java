package nl.inl.blacklab.server.requesthandlers;

import javax.servlet.http.HttpServletResponse;

import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.dataobject.DataFormat;
import nl.inl.blacklab.server.dataobject.DataObject;
import nl.inl.blacklab.server.search.SearchManager;

import org.apache.log4j.Logger;

public class Response {
	static final Logger logger = Logger.getLogger(Response.class);

	/**
	 * Construct a busy response with "check again" advice.
	 *
	 * @param servlet the servlet, for the check again advice
	 * @return the data object representing the error message
	 */
	public static Response busy(BlackLabServer servlet) {
		int when = 1000; //servlet.getSearchManager().getCheckAgainAdviceMinimumMs();
		Response r = new Response(DataObject.statusObjectWithCheckAgain("WORKING", "Searching, please wait...", when));
		r.setCacheAllowed(false); // status should never be cached
		return r;
	}

	/**
	 * Construct a simple status response.
	 *
	 * Status response may indicate success, or e.g. that the
	 * server is carrying out the request and will have results later.
	 *
	 * @param code (string) status code
	 * @param msg the message
	 * @param httpCode http status code to send
	 * @return the data object representing the error message
	 */
	public static Response status(String code, String msg, int httpCode) {
		Response r = new Response(DataObject.statusObject(code, msg), httpCode);
		r.setCacheAllowed(false); // status should never be cached
		return r;
	}

	/**
	 * Construct a simple status response.
	 *
	 * Status response may indicate success, or e.g. that the
	 * server is carrying out the request and will have results later.
	 *
	 * @param code (string) BLS status code
	 * @param msg the message
	 * @param httpCode the HTTP status code to set
	 * @return the data object representing the error message
	 */
	public static Response error(String code, String msg, int httpCode) {
		Response r = new Response(DataObject.errorObject(code, msg), httpCode);
		r.setCacheAllowed(false); // (error)status should never be cached
		return r;
	}

	// Highest internal error code so far: 30

	public static Response internalError(Exception e, boolean debugMode, int code) {
		logger.debug("INTERNAL ERROR " + code + ":");
		e.printStackTrace();
		Response r = new Response(DataObject.internalError(e, debugMode, code), HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		r.setCacheAllowed(false); // (error)status should never be cached
		return r;
	}

	public static Response internalError(String message, boolean debugMode, int code) {
		logger.debug("INTERNAL ERROR " + code + ": " + message);
		Response r = new Response(DataObject.internalError(message, debugMode, code), HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		r.setCacheAllowed(false); // (error)status should never be cached
		return r;
	}

	public static Response internalError(int code) {
		logger.debug("INTERNAL ERROR " + code + " (no message)");
		Response r = new Response(DataObject.internalError(code), HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		r.setCacheAllowed(false); // (error)status should never be cached
		return r;
	}

	public static Response success(String msg) {
		return status("SUCCESS", msg, HttpServletResponse.SC_OK);
	}

	public static Response accepted() {
		return status("SUCCESS", "Documents uploaded succesfully; indexing started.", HttpServletResponse.SC_ACCEPTED);
	}

	public static Response searchTimedOut() {
		return error("SEARCH_TIMED_OUT", "Search took too long, cancelled.", HttpServletResponse.SC_SERVICE_UNAVAILABLE);
	}

	public static Response unauthorized(String reason) {
		return error("NOT_AUTHORIZED", "Unauthorized operation. " + reason, HttpServletResponse.SC_UNAUTHORIZED);
	}

	public static Response methodNotAllowed(String method, String reason) {
		reason = reason == null ? "" : " " + reason;
		return error("ILLEGAL_REQUEST", "Illegal " + method + " request." + reason, HttpServletResponse.SC_METHOD_NOT_ALLOWED);
	}

	public static Response forbidden() {
		return error("FORBIDDEN_REQUEST", "Forbidden operation.", HttpServletResponse.SC_FORBIDDEN);
	}

	public static Response forbidden(String reason) {
		return error("FORBIDDEN_REQUEST", "Forbidden request. " + reason, HttpServletResponse.SC_FORBIDDEN);
	}

	public static Response badRequest(String code, String message) {
		return error(code, message, HttpServletResponse.SC_BAD_REQUEST);
	}

	public static Response unavailable(String indexName, String status) {
		return error("INDEX_UNAVAILABLE", "The index '" + indexName + "' is not available right now. Status: " + status, HttpServletResponse.SC_CONFLICT);
	}

	public static Response indexNotFound(String indexName) {
		return error("CANNOT_OPEN_INDEX", "Could not open index '" + indexName + "'. Please check the name.", HttpServletResponse.SC_NOT_FOUND);
	}

	public static Response illegalIndexName(String shortName) {
		return badRequest("ILLEGAL_INDEX_NAME", "\"" + shortName + "\" " + SearchManager.ILLEGAL_NAME_ERROR);
	}

	/** HTTP response status code to use. */
	int httpStatusCode = 200;

	/** The response data */
	DataObject dataObject;

	/** If set, overrides the response type (XML/JSON) for this response. */
	DataFormat overrideType = null;

	/** If true, the client may cache this response. If false, it should never cache this. */
	boolean cacheAllowed = true;

	public Response(DataObject dataObject, int httpStatusCode) {
		this.dataObject = dataObject;
		this.httpStatusCode = httpStatusCode;
	}

	public Response(DataObject dataObject) {
		this(dataObject, 200);
	}

	public int getHttpStatusCode() {
		return httpStatusCode;
	}

	public DataObject getDataObject() {
		return dataObject;
	}

	public DataFormat getOverrideType() {
		return overrideType;
	}

	public void setOverrideType(DataFormat type) {
		this.overrideType = type;
	}

	public boolean isCacheAllowed() {
		return cacheAllowed;
	}

	public void setCacheAllowed(boolean b) {
		cacheAllowed = b;
	}

}
