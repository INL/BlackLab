package nl.inl.blacklab.server.exceptions;

import nl.inl.blacklab.exceptions.RegexpTooLarge;
import nl.inl.blacklab.exceptions.WildcardTermTooBroad;

/**
 * Thrown when the requested index was not available or could not be opened
 */
public class BlsException extends Exception {
    
    public static InternalServerError indexTooOld() {
        return indexTooOld(null);
    }

    public static InternalServerError indexTooOld(Throwable e) {
        return new InternalServerError("Index too old to open with this BlackLab version", "INTERR_INDEX_TOO_OLD", e);
    }

    public static BadRequest wildcardTermTooBroad(WildcardTermTooBroad e) {
        return new BadRequest("QUERY_TOO_BROAD", "Query too broad, too many matching terms. Please be more specific.", e);
    }
    
    public static BadRequest regexpTooLarge(RegexpTooLarge e) {
        return new BadRequest("REGEXP_TOO_LARGE", "Regular expression too large.", e);
    }

    /**
     * The HTTP error code to send.
     */
    protected int httpStatusCode;

    /**
     * A symbolic error code that the client can recognize and show a custom message
     * for.
     */
    protected String errorCode;

    public BlsException(int httpStatusCode, String errorCode, String msg, Throwable cause) {
        super(msg, cause);
        this.httpStatusCode = httpStatusCode;
        this.errorCode = errorCode;
    }

    public BlsException(int httpCode, String errorCode, String msg) {
        this(httpCode, errorCode, msg, null);
    }

    public int getHttpStatusCode() {
        return httpStatusCode;
    }

    public String getBlsErrorCode() {
        return errorCode;
    }

}
