package nl.inl.blacklab.server.auth;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.search.UserRequest;

import org.pac4j.core.context.Cookie;

/**
 * Set a cookie to simulate a logged-in user.
 * 
 * NOTE: this doesn't work properly with corpus-frontend at the moment, because
 * corpus-frontend performs requests to BLS from both the client and the server.
 * Both of these get assigned different cookies, causing problems. One way to
 * fix this would be to only use BLS from the client, but that would require
 * significant changes to the application.
 */
public class AuthDebugCookie implements AuthMethod {

    static final Logger logger = LogManager.getLogger(AuthDebugCookie.class);

    static final int TEN_YEARS = 10 * 365 * 24 * 60 * 60;

    public AuthDebugCookie(Map<String, Object> param) {
        // doesn't take any parameters
        if (!param.isEmpty())
            logger.warn("Parameters were passed to " + this.getClass().getName() + ", but it takes no parameters.");
    }

    @Override
    public User determineCurrentUser(UserRequest request) {
        String userId = request.getContext()
                .getRequestCookies().stream().
                filter(c -> c.getName().equals("autosearch-debug-user"))
                .findFirst()
                .map(Cookie::getValue)
                .orElseGet(() -> "user-" + request.getSessionId().substring(0, 6));

        Cookie cookie = new Cookie("autosearch-debug-user", userId);
        cookie.setPath("/");
        cookie.setMaxAge(TEN_YEARS);
        request.getContext().addResponseCookie(cookie);

        return User.loggedIn(userId, request.getSessionId());
    }
}
