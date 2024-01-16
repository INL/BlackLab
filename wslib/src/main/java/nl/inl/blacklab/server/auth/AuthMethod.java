package nl.inl.blacklab.server.auth;

import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.search.UserRequest;

/**
 * An authentication method.
 */
public interface AuthMethod {
    User determineCurrentUser(UserRequest request);
}
