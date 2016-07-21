package nl.inl.blacklab.server.exceptions;

import javax.servlet.http.HttpServletResponse;

public class BadRequest extends BlsException {

	public BadRequest(String code, String msg) {
		super(HttpServletResponse.SC_BAD_REQUEST, code, msg);
	}

}
