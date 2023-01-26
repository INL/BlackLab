package nl.inl.blacklab.server.search;

import nl.inl.blacklab.instrumentation.RequestInstrumentationProvider;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.lib.WebserviceOperation;
import nl.inl.blacklab.server.lib.WebserviceParams;

/** Represents a request from the user to the webservice.
 * Used to factor out implementation-specific classes like HttpServlet,
 * HttpServletRequest, etc.
 */
public interface UserRequest {
    /**
     * Use the specified authentication method to determine the current user.
     *
     * @return user object (either a logged-in user or the anonymous user object)
     */
    User getUser();

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

    /**
     * Create parameters object from the request.
     *
     * @param indexName index name
     * @param index index we're querying
     * @param operation operation to perform (if not passed as a parameter)
     * @return parameters object
     */
    WebserviceParams getParams(BlackLabIndex index, WebserviceOperation operation);

    /**
     * Is this a debug request?
     *
     * @return true if it's a debug request
     */
    boolean isDebugMode();

    /**
     * Get the instrumentation provider for metrics.
     *
     * Will return a "no op" version if none is configured.
     *
     * @return instrumentation provider
     */
    RequestInstrumentationProvider getInstrumentationProvider();

    /**
     * Get the name of the corpus we're accessing.
     *
     * @return corpus name
     */
    public String getCorpusName();
}
