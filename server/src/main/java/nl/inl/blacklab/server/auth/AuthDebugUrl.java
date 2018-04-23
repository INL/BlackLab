package nl.inl.blacklab.server.auth;

import java.util.Map;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.server.jobs.User;

/**
 * Authentication system used for debugging.
 *
 * Requests from debug IPs (specified in config file) may fake logged-in
 * user by passing "userid" parameter.
 */
public class AuthDebugUrl {

	public AuthDebugUrl(Map<String, Object> parameters) {
		// doesn't take any parameters
	}

	public User determineCurrentUser(HttpServlet servlet,
			HttpServletRequest request) {
		// URL parameter is already dealt with in AuthManager. If we end up here,
		// there was no userid parameter, so just return an anonymous user.
		return User.anonymous(request.getSession().getId());
	}

}
