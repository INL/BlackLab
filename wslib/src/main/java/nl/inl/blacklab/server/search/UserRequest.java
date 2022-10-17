package nl.inl.blacklab.server.search;

import nl.inl.blacklab.server.auth.AuthMethod;
import nl.inl.blacklab.server.lib.User;

/** Represents a request from the user to the webservice.
 * Used to factor out implementation-specific classes like HttpServlet,
 * HttpServletRequest, etc.
 */
public interface UserRequest {
    /**
     * Use the specified authentication method to determine the current user.
     *
     * @param authObj authentication method to use
     * @return user object (either a logged-in user or the anonymous user object)
     */
    User determineCurrentUser(AuthMethod authObj);

    SearchManager getSearchManager();

    /**
     * Get our current session id.
     * @return unique id for the current session
     */
    String getSessionId();

    /**
     * Get the remote address.
     * @return user's remote address
     */
    String getRemoteAddr();

    /**
     * Return the previously persisted user id, if any.
     * @return persisted user id, or null if none
     */
    String getPersistedUserId();

    /**
     * Persist the user (if the auth method wants to do that).
     *
     * Only used by AuthDebugCookie.
     *
     * @param user current user
     * @param durationSec how long to persist
     */
    void persistUser(User user, int durationSec);

    /**
     * Get the value of a request header.
     *
     * @param name header name
     * @return header value or null if not present
     */
    String getHeader(String name);

    /**
     * Get the value of a request parameter.
     *
     * @param name parameter name
     * @return parameter value or null if not present
     */
    String getParameter(String name);


    /**
     * Get the value of a request attribute.
     *
     * @param name attribute name
     * @return attribute value or null if not present
     */
    Object getAttribute(String name);
}
