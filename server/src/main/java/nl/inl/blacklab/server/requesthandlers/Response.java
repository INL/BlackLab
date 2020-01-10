package nl.inl.blacklab.server.requesthandlers;

import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;

public class Response {
    static final Logger logger = LogManager.getLogger(Response.class);

    /**
     * Stream a busy response with "check again" advice.
     *
     * @param ds output stream
     * @param servlet the servlet, for the check again advice
     * @return the data object representing the error message
     */
    @SuppressWarnings("deprecation")
    public static int busy(DataStream ds, BlackLabServer servlet) {
        int when = 1000; //servlet.getSearchManager().getCheckAgainAdviceMinimumMs();
        ds.statusObject("WORKING", "Searching, please wait...", when);
        return HttpServletResponse.SC_OK;
    }

    /**
     * Stream a simple status response.
     *
     * Status response may indicate success, or e.g. that the server is carrying out
     * the request and will have results later.
     *
     * @param ds output stream
     * @param code (string) status code
     * @param msg the message
     * @param httpCode http status code to send
     * @return the data object representing the error message
     */
    public static int status(DataStream ds, String code, String msg, int httpCode) {
        ds.statusObject(code, msg);
        return httpCode;
    }

    /**
     * Stream a simple status response.
     *
     * Status response may indicate success, or e.g. that the server is carrying out
     * the request and will have results later.
     *
     * @param ds output stream
     * @param code (string) BLS status code
     * @param msg the message
     * @param httpCode the HTTP status code to set
     * @return the data object representing the error message
     */
    public static int error(DataStream ds, String code, String msg, int httpCode) {
        ds.error(code, msg);
        return httpCode;
    }

    // Highest internal error code so far: 32

    public static int internalError(DataStream ds, Exception e, boolean debugMode, String code) {
        logger.debug("INTERNAL ERROR " + code + ":");
        e.printStackTrace();
        ds.internalError(e, debugMode, code);
        return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
    }

    public static int internalError(DataStream ds, String message, boolean debugMode, String code) {
        logger.debug("INTERNAL ERROR " + code + ": " + message);
        ds.internalError(message, debugMode, code);
        return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
    }

    public static int internalError(DataStream ds, String code) {
        logger.debug("INTERNAL ERROR " + code + " (no message)");
        ds.internalError(code);
        return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
    }

    public static int success(DataStream ds, String msg) {
        return status(ds, "SUCCESS", msg, HttpServletResponse.SC_OK);
    }

    public static int accepted(DataStream ds) {
        return status(ds, "SUCCESS", "Documents uploaded succesfully; indexing started.",
                HttpServletResponse.SC_ACCEPTED);
    }

    public static int searchTimedOut(DataStream ds) {
        return error(ds, "SEARCH_TIMED_OUT", "Search took too long, cancelled.",
                HttpServletResponse.SC_SERVICE_UNAVAILABLE);
    }

    public static int unauthorized(DataStream ds, String reason) {
        return error(ds, "NOT_AUTHORIZED", "Unauthorized operation. " + reason, HttpServletResponse.SC_UNAUTHORIZED);
    }

    public static int methodNotAllowed(DataStream ds, String method, String reason) {
        reason = reason == null ? "" : " " + reason;
        return error(ds, "ILLEGAL_REQUEST", "Illegal " + method + " request." + reason,
                HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }

    public static int forbidden(DataStream ds) {
        return error(ds, "FORBIDDEN_REQUEST", "Forbidden operation.", HttpServletResponse.SC_FORBIDDEN);
    }

    public static int forbidden(DataStream ds, String reason) {
        return error(ds, "FORBIDDEN_REQUEST", "Forbidden request. " + reason, HttpServletResponse.SC_FORBIDDEN);
    }

    public static int badRequest(DataStream ds, String code, String message) {
        return error(ds, code, message, HttpServletResponse.SC_BAD_REQUEST);
    }

    public static int unavailable(DataStream ds, String indexName, String status) {
        return error(ds, "INDEX_UNAVAILABLE",
                "The index '" + indexName + "' is not available right now. Status: " + status,
                HttpServletResponse.SC_CONFLICT);
    }

    public static int indexNotFound(DataStream ds, String indexName) {
        return error(ds, "CANNOT_OPEN_INDEX", "Could not open index '" + indexName + "'. Please check the name.",
                HttpServletResponse.SC_NOT_FOUND);
    }

    public static int illegalIndexName(DataStream ds, String shortName) {
        return badRequest(ds, "ILLEGAL_INDEX_NAME", "\"" + shortName + "\" " + Response.ILLEGAL_NAME_ERROR);
    }

    public static final String ILLEGAL_NAME_ERROR = "is not a valid index name (only letters, digits, underscores and dashes allowed, and must start with a letter)";

}
