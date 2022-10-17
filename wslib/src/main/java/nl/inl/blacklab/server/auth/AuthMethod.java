package nl.inl.blacklab.server.auth;

import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.search.UserRequest;

/**
 * An authentication method.
 */
public interface AuthMethod {

    User determineCurrentUser(UserRequest request);

    /**
     * If this auth method wants to persist the user id, it should do so now.
     *
     * E.g. the AuthDebugCookie method persists the user in a cookie.
     *
     * @param request user request
     * @param user current user
     */
    default void persistUser(UserRequest request, User user) {
        // do nothing
    }

}
