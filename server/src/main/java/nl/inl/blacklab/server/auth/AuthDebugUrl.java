package nl.inl.blacklab.server.auth;

import java.util.Map;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.server.jobs.User;

/**
 * Authentication system used for debugging.
 *
 * Requests from debug IPs (specified in config file) may fake logged-in user by
 * passing "userid" parameter.
 */
public class AuthDebugUrl {

    static final Logger logger = LogManager.getLogger(AuthDebugFixed.class);

    public AuthDebugUrl(Map<String, Object> param) {
        // doesn't take any parameters
        if (param.size() > 0)
            logger.warn("Parameters were passed to " + this.getClass().getName() + ", but it takes no parameters.");
    }

    public User determineCurrentUser(HttpServlet servlet,
            HttpServletRequest request) {
        // URL parameter is already dealt with in AuthManager. If we end up here,
        // there was no userid parameter, so just return an anonymous user.
        return User.anonymous(request.getSession().getId());
    }

}
