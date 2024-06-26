package nl.inl.blacklab.server.exceptions;

import java.util.Collections;
import java.util.Map;

/**
 * Thrown when the requested index was not available or could not be opened
 */
public class BlsException extends RuntimeException {

    /**
     * The HTTP error code to send.
     */
    protected final int httpStatusCode;

    /**
     * A symbolic error code that the client can recognize and show a custom message
     * for.
     */
    protected final String errorCode;

    /** Structured information about the error, e.g. "name" for UNKNOWN_MATCH_INFO;
     * Can be used by the client to show custom error messages that include relevant information.
     */
    private final Map<String, String> info;

    public BlsException(int httpStatusCode, String errorCode, String msg, Map<String, String> info, Throwable cause) {
        super(msg, cause);
        this.httpStatusCode = httpStatusCode;
        this.errorCode = errorCode;
        this.info = info == null ? Collections.emptyMap() : info;
    }

    public BlsException(int httpCode, String errorCode, Map<String, String> info, String msg) {
        this(httpCode, errorCode, msg, info, null);
    }

    public BlsException(int httpStatusCode, String errorCode, String msg, Throwable cause) {
        this(httpStatusCode, errorCode, msg, null, cause);
    }

    public BlsException(int httpCode, String errorCode, String msg) {
        this(httpCode, errorCode, msg, null, null);
    }

    public int getHttpStatusCode() {
        return httpStatusCode;
    }

    public String getBlsErrorCode() {
        return errorCode;
    }

    public Map<String, String> getInfo() {
        return Collections.unmodifiableMap(info);
    }

}
