package nl.inl.blacklab.server.search;

import java.util.Map;
import java.util.Objects;

import nl.inl.blacklab.instrumentation.RequestInstrumentationProvider;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.lib.results.ApiVersion;
import nl.inl.blacklab.webservice.WebserviceOperation;
import nl.inl.blacklab.server.lib.WebserviceParams;

import org.pac4j.core.context.WebContext;
import org.pac4j.core.context.session.SessionStore;

/** Represents a request from the user to the webservice.
 * Used to factor out implementation-specific classes like HttpServlet,
 * HttpServletRequest, etc.
 */
public interface UserRequest {

    /** Pac4j web context, for authentication/authorization purposes. */
    WebContext getContext();

    /** pac4j session store, for authentication/authorization purposes. */
    SessionStore getSessionStore();

    default String getSessionId() {
        // Rather have an exception than a null session, as we rely on the session id to always be present.
        return getSessionStore().getSessionId(getContext(), true).orElseThrow();
    }

    /**
     * Use the specified authentication method to determine the current user.
     *
     * @return user object (either a logged-in user or the anonymous user object)
     */
    User getUser();

    SearchManager getSearchManager();

    /**
     * Create BlackLab's operation parameters object from the request.
     *
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

    ApiVersion apiVersion();
}
