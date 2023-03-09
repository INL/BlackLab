package nl.inl.blacklab.server.lib;

import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.IllegalIndexName;
import nl.inl.blacklab.server.lib.results.ResponseStreamer;

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
     * @param rs output stream
     * @param code (string) status code
     * @param msg the message
     * @param httpCode http status code to send
     * @return the data object representing the error message
     */
    public static int status(ResponseStreamer rs, String code, String msg, int httpCode) {
        rs.getDataStream().statusObject(code, msg);
        return httpCode;
    }

    /**
     * Stream a simple status response.
     *
     * Status response may indicate success, or e.g. that the server is carrying out
     * the request and will have results later.
     *
     * @param rs output stream
     * @param code (string) BLS status code
     * @param msg the message
     * @param httpCode the HTTP status code to set
     * @return the data object representing the error message
     */
    public static int error(ResponseStreamer rs, String code, String msg, int httpCode) {
        rs.getDataStream().error(code, msg);
        return httpCode;
    }

    public static int error(ResponseStreamer rs, String code, String msg, int httpCode, Throwable e) {
        rs.getDataStream().error(code, msg, e);
        return httpCode;
    }

    // Highest internal error code so far: 32

    public static int internalError(ResponseStreamer rs, Exception e, boolean debugMode, String code) {
        if (e.getCause() instanceof BlsException) {
            BlsException cause = (BlsException) e.getCause();
            logger.warn("BLACKLAB EXCEPTION " + cause.getBlsErrorCode(), e);
            return Response.error(rs, cause.getBlsErrorCode(), cause.getMessage(), cause.getHttpStatusCode());
        }
        logger.error("INTERNAL ERROR " + code + ":", e);
        rs.getDataStream().internalError(e, debugMode, code);
        return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
    }

    public static int internalError(ResponseStreamer rs, String message, boolean debugMode, String code) {
        logger.debug("INTERNAL ERROR " + code + ": " + message);
        rs.getDataStream().internalError(message, debugMode, code);
        return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
    }

    public static int internalError(ResponseStreamer rs, String code) {
        logger.debug("INTERNAL ERROR " + code + " (no message)");
        rs.getDataStream().internalError(code);
        return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
    }

    public static int success(ResponseStreamer rs, String msg) {
        return status(rs, "SUCCESS", msg, HttpServletResponse.SC_OK);
    }

    public static int accepted(ResponseStreamer rs) {
        return status(rs, "SUCCESS", "Documents uploaded succesfully; indexing started.",
                HttpServletResponse.SC_ACCEPTED);
    }

    public static int searchTimedOut(ResponseStreamer rs) {
        return error(rs, "SEARCH_TIMED_OUT", "Search took too long, cancelled.",
                HttpServletResponse.SC_SERVICE_UNAVAILABLE);
    }

    public static int unauthorized(ResponseStreamer rs, String reason) {
        return error(rs, "NOT_AUTHORIZED", "Unauthorized operation. " + reason, HttpServletResponse.SC_UNAUTHORIZED);
    }

    public static int methodNotAllowed(ResponseStreamer rs, String method, String reason) {
        reason = reason == null ? "" : " " + reason;
        return error(rs, "ILLEGAL_REQUEST", "Illegal " + method + " request." + reason,
                HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }

    public static int forbidden(ResponseStreamer rs) {
        return error(rs, "FORBIDDEN_REQUEST", "Forbidden operation.", HttpServletResponse.SC_FORBIDDEN);
    }

    public static int forbidden(ResponseStreamer rs, String reason) {
        return error(rs, "FORBIDDEN_REQUEST", "Forbidden request. " + reason, HttpServletResponse.SC_FORBIDDEN);
    }

    public static int badRequest(ResponseStreamer rs, String code, String message) {
        return error(rs, code, message, HttpServletResponse.SC_BAD_REQUEST);
    }

    public static int unavailable(ResponseStreamer rs, String indexName, String status) {
        return error(rs, "INDEX_UNAVAILABLE",
                "The index '" + indexName + "' is not available right now. Status: " + status,
                HttpServletResponse.SC_CONFLICT);
    }

    public static int indexNotFound(ResponseStreamer rs, String indexName) {
        return error(rs, "CANNOT_OPEN_INDEX", "Could not open index '" + indexName + "'. Please check the name.",
                HttpServletResponse.SC_NOT_FOUND);
    }

    public static int illegalIndexName(ResponseStreamer rs, String shortName) {
        return badRequest(rs, "ILLEGAL_INDEX_NAME", "\"" + shortName + "\" " + IllegalIndexName.ILLEGAL_NAME_ERROR);
    }

}
