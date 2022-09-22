package nl.inl.blacklab.server.auth;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import nl.inl.blacklab.server.lib.User;

/**
 * An authentication method.
 */
public interface AuthMethod {

    User determineCurrentUser(HttpServlet servlet, HttpServletRequest request);

    default void persistUser(HttpServlet servlet, HttpServletRequest request, HttpServletResponse response, User user) {
        // do nothing
    }

}
