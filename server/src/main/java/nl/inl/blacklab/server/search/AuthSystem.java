package nl.inl.blacklab.server.search;

import java.lang.reflect.Method;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.server.jobs.User;

public class AuthSystem {

	/** The authentication system, giving information about the currently logged-in user
        (or at least a session id) */
	private Object authSystem;

	/** The method to invoke for determining the current user. */
	private Method authMethodDetermineCurrentUser;

	public AuthSystem(Object authSystem, Method authMethodDetermineCurrentUser) {
		super();
		this.authSystem = authSystem;
		this.authMethodDetermineCurrentUser = authMethodDetermineCurrentUser;
	}

	public Object getAuthSystem() {
		return authSystem;
	}

	public Method getAuthMethodDetermineCurrentUser() {
		return authMethodDetermineCurrentUser;
	}

	public User determineCurrentUser(HttpServlet servlet, HttpServletRequest request) {
		// If no auth system is configured, all users are anonymous
		if (authSystem == null) {
			User user = User.anonymous(request.getSession().getId());
			return user;
		}

		// Let auth system determine the current user.
		try {
			User user = (User)authMethodDetermineCurrentUser.invoke(authSystem, servlet, request);
			return user;
		} catch (Exception e) {
			throw new RuntimeException("Error determining current user", e);
		}
	}

}