package nl.inl.blacklab.server.auth;

import java.util.Map;
import java.util.Optional;

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

    /** Sets the attribute name to use for the user id. */
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

        return getUserId(request).map(userId -> User.loggedIn(userId, sessionId)).orElseGet(() -> User.anonymous(sessionId));
    }

    protected Optional<String> getUserId(UserRequest request) {
        // Overridden in URL?
        boolean isOverrideIp = request.getSearchManager().config().getAuthentication().isOverrideIp(request.getContext().getRemoteAddr());
        Optional<String> user = isOverrideIp ? Optional.empty() : request.getContext().getRequestParameter("userid");
        // If not overridden (or not allowed, return the regular user attribute
        return user.or(() -> request.getContext().getRequestAttribute(attributeName).map(Object::toString));
    }

}
