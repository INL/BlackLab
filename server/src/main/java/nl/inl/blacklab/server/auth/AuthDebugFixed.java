package nl.inl.blacklab.server.auth;

import java.util.Map;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.jobs.User;
import nl.inl.blacklab.server.search.SearchManager;

/**
 * Authentication system used for debugging.
 *
 * Requests from debug IPs (specified in config file) may fake logged-in
 * user by passing "userid" parameter.
 */
public class AuthDebugFixed {

	private String userId;

	public AuthDebugFixed(Map<String, Object> parameters) {
		this.userId = parameters.get("userId").toString();
	}

	public User determineCurrentUser(HttpServlet servlet,
			HttpServletRequest request) {

		String sessionId = request.getSession().getId();

		// Is client on debug IP?
		SearchManager searchMan = ((BlackLabServer)servlet).getSearchManager();
		if (!searchMan.config().overrideUserId(request.getRemoteAddr())) {
			return User.anonymous(sessionId);
		}

		// Return the appropriate User object
		return User.loggedIn(userId, sessionId);
	}

}
