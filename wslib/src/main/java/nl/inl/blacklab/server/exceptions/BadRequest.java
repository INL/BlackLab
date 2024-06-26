package nl.inl.blacklab.server.exceptions;

import java.util.Map;

import javax.servlet.http.HttpServletResponse;

public class BadRequest extends BlsException {

    public BadRequest(String code, String msg) {
        this(code, msg, null, null);
    }

    public BadRequest(String code, String msg, Map<String, String> info) {
        this(code, msg, info, null);
    }

    public BadRequest(String code, String msg, Throwable cause) {
        this(code, msg, null, cause);
    }

    public BadRequest(String code, String msg, Map<String, String> info, Throwable cause) {
        super(HttpServletResponse.SC_BAD_REQUEST, code, msg, info, cause);
    }

}
