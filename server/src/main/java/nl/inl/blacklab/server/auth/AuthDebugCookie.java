package nl.inl.blacklab.server.auth;

import java.util.Map;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.server.jobs.User;
import nl.inl.blacklab.server.requesthandlers.RequestHandler;

/**
 * Set a cookie to simulate a logged-in user.
 */
public class AuthDebugCookie {

    static final Logger logger = LogManager.getLogger(RequestHandler.class);
    
    static final int TIEN_JAAR = 10 * 365 * 24 * 60 * 60;
    
	public AuthDebugCookie(Map<String, Object> parameters) {
		// doesn't take any parameters
	}

	public User determineCurrentUser(HttpServlet servlet,
			HttpServletRequest request) {

		// Is there a cookie yet?
		String userId = null;
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            // Controleer of we een sessie-cookie hebben
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals("autosearch-debug-user")) {
                    userId = cookie.getValue();
                    break;
                }
            }
        }
		
        if (userId == null) {
        	// No cookie yet. Generate userId based on sessionId. Cookie will be saved in persistUser().
            userId = request.getSession().getId();
            if (userId.length() > 6) {
            	userId = userId.substring(0, 6);
            }
            userId = "user-" + userId;
            
        }
	    
		// Return the appropriate User object
		String sessionId = request.getSession().getId();
		if (userId == null || userId.length() == 0) {
			return User.anonymous(sessionId);
		}
		return User.loggedIn(userId, sessionId);
	}
	
	public void persistUser(HttpServlet servlet,
            HttpServletRequest request, HttpServletResponse response, User user) {
	    Cookie cookie = new Cookie("autosearch-debug-user", user.getUserId());
	    cookie.setPath("/");
        cookie.setMaxAge(TIEN_JAAR);
	    response.addCookie(cookie);
	}

}
