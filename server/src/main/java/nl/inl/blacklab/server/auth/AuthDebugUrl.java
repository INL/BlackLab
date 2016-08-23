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
public class AuthDebugUrl {

	public AuthDebugUrl(Map<String, Object> parameters) {
		// doesn't take any parameters
	}

	public User determineCurrentUser(HttpServlet servlet,
			HttpServletRequest request) {

		// Is client on debug IP and is there a userid parameter?
		String userId = null;
		SearchManager searchMan = ((BlackLabServer)servlet).getSearchManager();
		if (searchMan.config().overrideUserId(request.getRemoteAddr()) && request.getParameter("userid") != null) {
			userId = request.getParameter("userid");
		}

		// Return the appropriate User object
		String sessionId = request.getSession().getId();
		if (userId == null || userId.length() == 0) {
			return User.anonymous(sessionId);
		}
		return User.loggedIn(userId, sessionId);
	}

}
