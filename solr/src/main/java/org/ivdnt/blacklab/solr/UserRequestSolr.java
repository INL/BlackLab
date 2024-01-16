package org.ivdnt.blacklab.solr;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import org.apache.solr.common.params.SolrParams;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.search.DocSet;

import com.fasterxml.jackson.core.JsonProcessingException;

import nl.inl.blacklab.instrumentation.RequestInstrumentationProvider;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.lib.QueryParams;
import nl.inl.blacklab.server.lib.QueryParamsJson;
import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.lib.results.ApiVersion;
import nl.inl.blacklab.webservice.WebserviceOperation;
import nl.inl.blacklab.server.lib.WebserviceParams;
import nl.inl.blacklab.server.lib.WebserviceParamsImpl;
import nl.inl.blacklab.server.search.SearchManager;
import nl.inl.blacklab.server.search.UserRequest;
import nl.inl.blacklab.server.util.ServletUtil;

import org.eclipse.jetty.server.session.Session;
import org.pac4j.core.context.Cookie;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.context.session.SessionStore;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

public class UserRequestSolr implements UserRequest {

    private final ResponseBuilder rb;

    private final BlackLabSearchComponent searchComponent;

    private final SearchManager searchMan;

    private User user;

    private WebContext context;
    private SessionStore sessionStore;

    public UserRequestSolr(ResponseBuilder rb, BlackLabSearchComponent searchComponent) {
        this.rb = rb;
        this.searchComponent = searchComponent;
        this.searchMan = searchComponent.getSearchManager();

        HttpServletRequest req = rb.req.getHttpSolrCall().getReq();
        this.context = new WebContext() {
            @Override
            public Optional<String> getRequestParameter(String name) {
                return Optional.ofNullable(req.getParameter(name));
            }

            @Override
            public Map<String, String[]> getRequestParameters() {
                return req.getParameterMap();
            }

            @Override
            public Optional getRequestAttribute(String name) {
                return Optional.ofNullable(req.getAttribute(name));
            }

            @Override
            public void setRequestAttribute(String name, Object value) {
                req.setAttribute(name, value);
            }

            @Override
            public Optional<String> getRequestHeader(String name) {
                return Optional.ofNullable(req.getHeader(name));
            }

            @Override
            public String getRequestMethod() {
                return req.getMethod();
            }

            @Override
            public String getRemoteAddr() {
                return ServletUtil.getOriginatingAddress(req);
            }

            @Override
            public void setResponseHeader(String name, String value) {

            }

            @Override
            public Optional<String> getResponseHeader(String name) {
                return Optional.empty();
            }

            @Override
            public void setResponseContentType(String content) {

            }

            @Override
            public String getServerName() {
                return "";
            }

            @Override
            public int getServerPort() {
                return 0;
            }

            @Override
            public String getScheme() {
                return req.getScheme();
            }

            @Override
            public boolean isSecure() {
                return req.isSecure();
            }

            @Override
            public String getFullRequestURL() {
                return req.getRequestURL().toString();
            }

            @Override
            public Collection<Cookie> getRequestCookies() {
                ArrayList<Cookie> cookies = new ArrayList<>();
                for (javax.servlet.http.Cookie c : req.getCookies()) {
                    Cookie cookie = new Cookie(c.getName(), c.getValue());
                    cookie.setDomain(c.getDomain());
                    cookie.setPath(c.getPath());
                    cookie.setSecure(c.getSecure());
                    cookie.setHttpOnly(c.isHttpOnly());
                    cookie.setComment(c.getComment());
                    cookie.setMaxAge(c.getMaxAge());
                    cookies.add(cookie);
                }
                return cookies;
            }

            @Override
            public void addResponseCookie(Cookie cookie) {

            }

            @Override
            public String getPath() {
                return null;
            }
        };

        this.sessionStore = new SessionStore() {
            @Override
            public Optional<String> getSessionId(WebContext context, boolean createSession) {
                return Optional.of(req.getSession(true)).map(HttpSession::getId);
            }

            @Override
            public Optional<Object> get(WebContext context, String key) {
                return Optional.ofNullable(req.getSession().getAttribute(key));
            }

            @Override
            public void set(WebContext context, String key, Object value) {
                req.getSession().setAttribute(key, value);
            }

            @Override
            public boolean destroySession(WebContext context) {
                req.getSession().invalidate();
                return true;
            }

            @Override
            public Optional<Object> getTrackableSession(WebContext context) {
                return Optional.ofNullable(req.getSession());
            }

            @Override
            public Optional<SessionStore> buildFromTrackableSession(WebContext context, Object trackableSession) {
                return Optional.empty();
            }

            @Override
            public boolean renewSession(WebContext context) {
                return false;
            }
        };
    }

    @Override
    public WebContext getContext() {
        return context;
    }

    @Override
    public SessionStore getSessionStore() {
        return sessionStore;
    }

    @Override
    public synchronized User getUser() {
        if (user == null) {
            //AuthMethod authObj = getSearchManager().getAuthSystem().getAuthObject();
            // TODO: detect logged-in user vs. anonymous user with session id
            Principal p = rb.req.getUserPrincipal();
            user = User.anonymous(p == null ? "UNKNOWN" : p.getName());
        }
        return user;
    }

    @Override
    public SearchManager getSearchManager() {
        return searchMan;
    }

    @Override
    public String getSessionId() {
        return null;
    }

    public Map<String, String[]> getParameters() { return null; }

    public WebserviceParams getParams(BlackLabIndex index, WebserviceOperation operation) {
        User user = getUser();
        SolrParams solrParams = rb.req.getParams();
        String blReq = solrParams.get("bl.req");
        QueryParams qpSolr;
        if (blReq != null) {
            // Request was passed as a JSON structure. Parse that.
            try {
                qpSolr = new QueryParamsJson(getCorpusName(), searchMan, user, blReq, operation);
            } catch (JsonProcessingException e) {
                throw new BadRequest("INVALID_JSON", "Error parsing bl.req parameter", e);
            }
        } else {
            // Request was passed as separate bl.* parameters. Parse them.
            qpSolr = new QueryParamsSolr(getCorpusName(), searchMan, solrParams, user);
        }
        try {
            operation = qpSolr.getOperation();
        } catch (UnsupportedOperationException e) {
            throw new BadRequest("UNKNOWN_OPERATION", "Unknown operation");
        }
        boolean isDocs = operation.isDocsOperation();
        WebserviceParamsImpl params = WebserviceParamsImpl.get(isDocs, isDebugMode(), qpSolr);
        if (params.getDocumentFilterQuery().isEmpty()) {
            // No explicit bl.filter specified; use Solr's document results as our filter query
            DocSet docSet = rb.getResults() != null ? rb.getResults().docSet : null;
            if (docSet != null && docSet.size() > 0 && index != null)
                params.setFilterQuery(new DocSetFilter(docSet, index.metadata().metadataDocId()));
        }
        return params;
    }

    @Override
    public String getCorpusName() {
        return rb.req.getCore().getName();
    }

    @Override
    public boolean isDebugMode() {
        return rb.req.getHttpSolrCall() == null || searchMan.isDebugMode(getContext().getRemoteAddr());
    }

    @Override
    public RequestInstrumentationProvider getInstrumentationProvider() {
        return searchComponent.getInstrumentationProvider();
    }

    @Override
    public ApiVersion apiVersion() {
        String paramApi = rb.req.getParams().get("bl.api");
        return paramApi == null ? searchMan.config().getParameters().getApi() :
                ApiVersion.fromValue(paramApi);
    }
}
