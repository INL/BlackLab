package nl.inl.blacklab.server.requesthandlers;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.jobs.User;

public class RequestHandlerStaticResponse extends RequestHandler {

    /** Error or status? */
    boolean isError = false;

    String code = null;

    String msg = null;

    int httpCode = HTTP_OK;

    /** If it's an internal error, this contains the exception */
    private Exception exception = null;

    /** If it's an internal error, this is nonempty */
    private String internalErrorCode = "";

    private int checkAgainAdviceMs = 0;

    /**
     * Stream a busy response.
     * 
     * @return the request handler itself
     */
    public RequestHandlerStaticResponse busy() {
        code = "WORKING";
        msg = "Searching, please wait...";
        checkAgainAdviceMs = 1000;
        httpCode = HTTP_OK;
        return this;
    }

    /**
     * Stream a simple status response.
     *
     * Status response may indicate success, or e.g. that the server is carrying out
     * the request and will have results later.
     *
     * @param code (string) status code
     * @param msg the message
     * @param httpCode http status code to send
     * @return the request handler itself
     */
    public RequestHandlerStaticResponse status(String code, String msg, int httpCode) {
        this.code = code;
        this.msg = msg;
        this.httpCode = httpCode;
        return this;
    }

    /**
     * Stream a simple status response.
     *
     * Status response may indicate success, or e.g. that the server is carrying out
     * the request and will have results later.
     *
     * @param code (string) BLS status code
     * @param msg the message
     * @param httpCode the HTTP status code to set
     * @return the request handler itself
     */
    public RequestHandlerStaticResponse error(String code, String msg, int httpCode) {
        this.code = code;
        this.msg = msg;
        this.httpCode = httpCode;
        isError = true;
        return this;
    }

    // Highest internal error code so far: 31

    public RequestHandlerStaticResponse internalError(Exception e, boolean debugMode, String code) {
        logger.debug("INTERNAL ERROR " + code + ":");
        e.printStackTrace();
        this.exception = e;
        this.debugMode = debugMode;
        internalErrorCode = code;
        httpCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        return this;
    }

    public RequestHandlerStaticResponse internalError(String message, boolean debugMode, String code) {
        logger.debug("INTERNAL ERROR " + code + ": " + message);
        msg = message;
        this.debugMode = debugMode;
        internalErrorCode = code;
        httpCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        return this;
    }

    public RequestHandlerStaticResponse internalError(String code) {
        logger.debug("INTERNAL ERROR " + code + " (no message)");
        internalErrorCode = code;
        httpCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        return this;
    }

    public RequestHandlerStaticResponse success(String msg) {
        return status("SUCCESS", msg, HTTP_OK);
    }

    public RequestHandlerStaticResponse accepted(DataStream ds) {
        return status("SUCCESS", "Documents uploaded succesfully; indexing started.", HttpServletResponse.SC_ACCEPTED);
    }

    public RequestHandlerStaticResponse searchTimedOut(DataStream ds) {
        return error("SEARCH_TIMED_OUT", "Search took too long, cancelled.",
                HttpServletResponse.SC_SERVICE_UNAVAILABLE);
    }

    public RequestHandlerStaticResponse unauthorized(String reason) {
        return error("NOT_AUTHORIZED", "Unauthorized operation. " + reason, HttpServletResponse.SC_UNAUTHORIZED);
    }

    public RequestHandlerStaticResponse methodNotAllowed(String method, String reason) {
        reason = reason == null ? "" : " " + reason;
        return error("ILLEGAL_REQUEST", "Illegal " + method + " request." + reason,
                HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }

    public RequestHandlerStaticResponse forbidden(DataStream ds) {
        return error("FORBIDDEN_REQUEST", "Forbidden operation.", HttpServletResponse.SC_FORBIDDEN);
    }

    public RequestHandlerStaticResponse forbidden(String reason) {
        return error("FORBIDDEN_REQUEST", "Forbidden request. " + reason, HttpServletResponse.SC_FORBIDDEN);
    }

    public RequestHandlerStaticResponse badRequest(String code, String message) {
        return error(code, message, HttpServletResponse.SC_BAD_REQUEST);
    }

    public RequestHandler unknownOperation(String operationName) {
        return badRequest("UNKNOWN_OPERATION", "Unknown operation '" + operationName + "'. Check your URL.");
    }

    public RequestHandlerStaticResponse unavailable(String indexName, String status) {
        return error("INDEX_UNAVAILABLE", "The index '" + indexName + "' is not available right now. Status: " + status,
                HttpServletResponse.SC_CONFLICT);
    }

    public RequestHandlerStaticResponse indexNotFound(String indexName) {
        return error("CANNOT_OPEN_INDEX", "Could not open index '" + indexName + "'. Please check the name.",
                HttpServletResponse.SC_NOT_FOUND);
    }

    public RequestHandlerStaticResponse illegalIndexName(String shortName) {
        return badRequest("ILLEGAL_INDEX_NAME", "\"" + shortName + "\" " + Response.ILLEGAL_NAME_ERROR);
    }

    RequestHandlerStaticResponse(BlackLabServer servlet, HttpServletRequest request, User user, String indexName,
            String urlResource,
            String urlPathInfo) {
        super(servlet, request, user, indexName, urlResource, urlPathInfo);
    }

    @Override
    public boolean isCacheAllowed() {
        return false; // error/status should never be cached, can change
    }

    @SuppressWarnings("deprecation")
    @Override
    public int handle(DataStream ds) throws BlsException {
        if (checkAgainAdviceMs != 0) {
            ds.statusObject(code, msg, checkAgainAdviceMs);
        }
        if (internalErrorCode != null && internalErrorCode.length() > 0) {
            if (exception != null)
                ds.internalError(exception, debugMode, internalErrorCode);
            else if (msg != null)
                ds.internalError(msg, debugMode, internalErrorCode);
            else
                ds.internalError(internalErrorCode);
        } else if (isError) {
            ds.error(code, msg);
        } else {
            ds.statusObject(code, msg);
        }
        return httpCode;
    }

}
