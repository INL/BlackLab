package nl.inl.blacklab.server.requesthandlers;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.core.JsonProcessingException;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.auth.AuthMethod;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.lib.QueryParams;
import nl.inl.blacklab.server.lib.QueryParamsJson;
import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.lib.WebserviceOperation;
import nl.inl.blacklab.server.lib.WebserviceParams;
import nl.inl.blacklab.server.lib.WebserviceParamsImpl;
import nl.inl.blacklab.server.search.SearchManager;
import nl.inl.blacklab.server.search.UserRequest;
import nl.inl.blacklab.server.util.ServletUtil;

/** Represents a servlet request to the webservice. */
public class UserRequestBls implements UserRequest {

    private final BlackLabServer servlet;

    private final HttpServletRequest request;

    private final HttpServletResponse response;

    private User user;

    public UserRequestBls(BlackLabServer servlet, HttpServletRequest request, HttpServletResponse response) {
        this.servlet = servlet;
        this.request = request;
        this.response = response;
    }

    @Override
    public synchronized User getUser() {
        if (user == null) {
            AuthMethod authObj = getSearchManager().getAuthSystem().getAuthObject();

            // If no auth system is configured, all users are anonymous
            if (authObj == null) {
                user = User.anonymous(request.getSession().getId());
            } else {

                // Is client on debug IP and is there a userid parameter?
                if (servlet.getSearchManager().config().getAuthentication().isOverrideIp(request.getRemoteAddr())
                        && request.getParameter("userid") != null) {
                    user = User.loggedIn(request.getParameter("userid"), request.getSession().getId());
                } else {
                    // Let auth system determine the current user.
                    try {
                        user = authObj.determineCurrentUser(this);
                    } catch (Exception e) {
                        throw new RuntimeException("Error determining current user", e);
                    }
                }
            }
        }
        return user;
    }

    public BlackLabServer getServlet() {
        return servlet;
    }

    public HttpServletRequest getRequest() {
        return request;
    }

    public HttpServletResponse getResponse() {
        return response;
    }

    @Override
    public SearchManager getSearchManager() {
        return servlet.getSearchManager();
    }

    @Override
    public String getSessionId() {
        return request.getSession().getId();
    }

    @Override
    public String getRemoteAddr() {
        return request.getRemoteAddr();
    }

    @Override
    public String getPersistedUserId() {
        // Is there a cookie yet?
        String userId = null;
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            // Check if we have a session cookie
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals("autosearch-debug-user")) {
                    userId = cookie.getValue();
                    break;
                }
            }
        }
        return userId;
    }

    @Override
    public void persistUser(User user, int durationSec) {
        Cookie cookie = new Cookie("autosearch-debug-user", user.getUserId());
        cookie.setPath("/");
        cookie.setMaxAge(durationSec);
        response.addCookie(cookie);
    }

    @Override
    public String getHeader(String name) {
        return request.getHeader(name);
    }

    @Override
    public String getParameter(String name) {
        return request.getParameter(name);
    }

    @Override
    public Object getAttribute(String name) {
        return request.getAttribute(name);
    }

    @Override
    public WebserviceParams getParams(String indexName, BlackLabIndex index, WebserviceOperation operation) {
        String jsonRequest = getRequest().getParameter("req");
        QueryParams blsParams;
        if (jsonRequest != null) {
            // Request was passed as a JSON structure. Parse that.
            try {
                blsParams = new QueryParamsJson(indexName, getSearchManager(), getUser(), jsonRequest, operation);
            } catch (JsonProcessingException e) {
                throw new BadRequest("INVALID_JSON", "Error parsing req parameter (JSON request)", e);
            }
        } else {
            // Request was passed as separate bl.* parameters. Parse them.
            blsParams = new QueryParamsBlackLabServer(indexName, getSearchManager(), getUser(), getRequest(), operation);
        }
        return WebserviceParamsImpl.get(operation.isDocsOperation(), isDebugMode(), blsParams);
    }

    @Override
    public boolean isDebugMode() {
        return getSearchManager().isDebugMode(ServletUtil.getOriginatingAddress(request));
    }
}
