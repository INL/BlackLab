package nl.inl.blacklab.server.auth;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.search.SearchManager;
import nl.inl.blacklab.server.search.UserRequest;

/**
 * Authentication system using servlet request attributes for logged-in user id.
 *
 * Can be used, for example, with Shibboleth authentication.
 */
public class AuthRequestAttribute implements AuthMethod {
    static final Logger logger = LogManager.getLogger(AuthRequestAttribute.class);

    private String attributeName = null;

    public AuthRequestAttribute(Map<String, Object> parameters) {
        Object parName = parameters.get("attributeName");
        if (parName == null) {
            logger.error("authSystem.attributeName parameter missing in blacklab-server.json");
        } else {
            this.attributeName = parName.toString();
            if (parameters.size() > 1)
                logger.warn("AuthRequestAttribute only takes one parameters (attributeName), but others were passed.");
        }
    }

    public AuthRequestAttribute(String attributeName) {
        this.attributeName = attributeName;
    }

    public User determineCurrentUser(UserRequest request) {
        String sessionId = request.getSessionId();
        if (attributeName == null) {
            // (not configured correctly)
            logger.warn(
                    "Cannot determine current user; missing authSystem.attributeName parameter in blacklab-server.json");
            return User.anonymous(sessionId);
        }

        // See if there's a logged-in user or not
        String userId = getUserId(request);

        // Return the appropriate User object
        if (userId == null || userId.length() == 0) {
            return User.anonymous(sessionId);
        }
        return User.loggedIn(userId, sessionId);
    }

    protected String getUserId(UserRequest request) {

        String userId = null;

        // Overridden in URL?
        SearchManager searchMan = request.getSearchManager();
        if (searchMan.config().getAuthentication().isOverrideIp(request.getRemoteAddr()) && request.getParameter("userid") != null) {
            userId = request.getParameter("userid");
        }

        if (userId == null) {
            Object attribute = request.getAttribute(attributeName);
            if (attribute != null)
                userId = attribute.toString();
        }

        return userId;
    }

}
