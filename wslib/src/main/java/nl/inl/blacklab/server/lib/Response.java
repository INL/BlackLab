package nl.inl.blacklab.server.lib;

import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.IllegalIndexName;
import nl.inl.blacklab.server.lib.results.DStream;

/**
 * Different BLS responses with response code and message.
 */
public class Response {
    static final Logger logger = LogManager.getLogger(Response.class);

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
    public static int status(DStream ds, String code, String msg, int httpCode) {
        ds.getDataStream().statusObject(code, msg);
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
    public static int error(DStream ds, String code, String msg, int httpCode) {
        ds.getDataStream().error(code, msg);
        return httpCode;
    }

    public static int error(DStream ds, String code, String msg, int httpCode, Throwable e) {
        ds.getDataStream().error(code, msg, e);
        return httpCode;
    }

    // Highest internal error code so far: 32

    public static int internalError(DStream ds, Exception e, boolean debugMode, String code) {
        if (e.getCause() instanceof BlsException) {
            BlsException cause = (BlsException) e.getCause();
            logger.warn("BLACKLAB EXCEPTION " + cause.getBlsErrorCode(), e);
            return Response.error(ds, cause.getBlsErrorCode(), cause.getMessage(), cause.getHttpStatusCode());
        }
        logger.error("INTERNAL ERROR " + code + ":", e);
        ds.getDataStream().internalError(e, debugMode, code);
        return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
    }

    public static int internalError(DStream ds, String message, boolean debugMode, String code) {
        logger.debug("INTERNAL ERROR " + code + ": " + message);
        ds.getDataStream().internalError(message, debugMode, code);
        return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
    }

    public static int internalError(DStream ds, String code) {
        logger.debug("INTERNAL ERROR " + code + " (no message)");
        ds.getDataStream().internalError(code);
        return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
    }

    public static int success(DStream ds, String msg) {
        return status(ds, "SUCCESS", msg, HttpServletResponse.SC_OK);
    }

    public static int accepted(DStream ds) {
        return status(ds, "SUCCESS", "Documents uploaded succesfully; indexing started.",
                HttpServletResponse.SC_ACCEPTED);
    }

    public static int searchTimedOut(DStream ds) {
        return error(ds, "SEARCH_TIMED_OUT", "Search took too long, cancelled.",
                HttpServletResponse.SC_SERVICE_UNAVAILABLE);
    }

    public static int unauthorized(DStream ds, String reason) {
        return error(ds, "NOT_AUTHORIZED", "Unauthorized operation. " + reason, HttpServletResponse.SC_UNAUTHORIZED);
    }

    public static int methodNotAllowed(DStream ds, String method, String reason) {
        reason = reason == null ? "" : " " + reason;
        return error(ds, "ILLEGAL_REQUEST", "Illegal " + method + " request." + reason,
                HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }

    public static int forbidden(DStream ds) {
        return error(ds, "FORBIDDEN_REQUEST", "Forbidden operation.", HttpServletResponse.SC_FORBIDDEN);
    }

    public static int forbidden(DStream ds, String reason) {
        return error(ds, "FORBIDDEN_REQUEST", "Forbidden request. " + reason, HttpServletResponse.SC_FORBIDDEN);
    }

    public static int badRequest(DStream ds, String code, String message) {
        return error(ds, code, message, HttpServletResponse.SC_BAD_REQUEST);
    }

    public static int unavailable(DStream ds, String indexName, String status) {
        return error(ds, "INDEX_UNAVAILABLE",
                "The index '" + indexName + "' is not available right now. Status: " + status,
                HttpServletResponse.SC_CONFLICT);
    }

    public static int indexNotFound(DStream ds, String indexName) {
        return error(ds, "CANNOT_OPEN_INDEX", "Could not open index '" + indexName + "'. Please check the name.",
                HttpServletResponse.SC_NOT_FOUND);
    }

    public static int illegalIndexName(DStream ds, String shortName) {
        return badRequest(ds, "ILLEGAL_INDEX_NAME", "\"" + shortName + "\" " + IllegalIndexName.ILLEGAL_NAME_ERROR);
    }

}
