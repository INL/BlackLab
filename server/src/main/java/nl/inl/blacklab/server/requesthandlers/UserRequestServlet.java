package nl.inl.blacklab.server.requesthandlers;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.auth.AuthMethod;
import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.search.SearchManager;
import nl.inl.blacklab.server.search.UserRequest;

/** Represents a servlet request to the webservice. */
public class UserRequestServlet implements UserRequest {

    private final BlackLabServer servlet;

    private final HttpServletRequest request;

    private final HttpServletResponse response;

    public UserRequestServlet(BlackLabServer servlet, HttpServletRequest request, HttpServletResponse response) {
        this.servlet = servlet;
        this.request = request;
        this.response = response;
    }

    @Override
    public User determineCurrentUser(AuthMethod authObj) {
        // If no auth system is configured, all users are anonymous
        if (authObj == null) {
            return User.anonymous(request.getSession().getId());
        }

        // Is client on debug IP and is there a userid parameter?
        if (servlet.getSearchManager().config().getAuthentication().isOverrideIp(request.getRemoteAddr())
                && request.getParameter("userid") != null) {
            return User.loggedIn(request.getParameter("userid"), request.getSession().getId());
        }

        // Let auth system determine the current user.
        try {
            return authObj.determineCurrentUser(this);
        } catch (Exception e) {
            throw new RuntimeException("Error determining current user", e);
        }
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
}
