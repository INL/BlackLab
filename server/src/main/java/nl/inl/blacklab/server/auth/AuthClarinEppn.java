package nl.inl.blacklab.server.auth;

import java.util.Map;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;

/**
 * Used for CLARIN login (Shibboleth), which passes userid
 * in an attribute called "eppn". Special class because for unknown
 * reasons, we (sometimes?) get the same userid twice in the attribute
 * (i.e. user@domain.com;user@domain.com). We detect and correct
 * this anomaly here.
 */
public class AuthClarinEppn extends AuthRequestAttribute {

	public AuthClarinEppn(Map<String, Object> dummy) {
		super("eppn");
	}

	@Override
	protected String getUserId(HttpServlet servlet, HttpServletRequest request) {
		String userId = super.getUserId(servlet, request);
		if (userId != null) {
			String[] parts = userId.split(";", 2);
			if (parts.length == 2 && parts[0].equals(parts[1])) {
				// The user id string is of the form "USERID;USERID".
				// Only return it once.
				return parts[0];
			}
		}
		return userId;
	}

}
