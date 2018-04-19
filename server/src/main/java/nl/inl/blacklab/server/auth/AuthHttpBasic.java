package nl.inl.blacklab.server.auth;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Base64.Decoder;
import java.util.Map;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.server.jobs.User;
import nl.inl.blacklab.server.requesthandlers.RequestHandler;

/**
 * Use basic HTTP authentication.
 * 
 * NOTE: this does not work properly with our corpus-frontend,
 * because not all requests originate from the client side,
 * so the credentials are not always passed to blacklab-server.
 */
public class AuthHttpBasic {

    static final Logger logger = LogManager.getLogger(RequestHandler.class);
    
    Decoder base64Decoder = Base64.getDecoder();
    
	public AuthHttpBasic(Map<String, Object> parameters) {
		// doesn't take any parameters
	}

	public User determineCurrentUser(HttpServlet servlet,
			HttpServletRequest request) {

	    String userId = null;
	    String authHeader = request.getHeader("authorization");
	    if (authHeader != null) {
            String encodedValue = authHeader.split(" ")[1];
            String decodedValue = new String(base64Decoder.decode(encodedValue), StandardCharsets.UTF_8);
            userId = decodedValue.split(":", 2)[0];
	    }
	    
		// Return the appropriate User object
		String sessionId = request.getSession().getId();
		if (userId == null || userId.length() == 0) {
			return User.anonymous(sessionId);
		}
		return User.loggedIn(userId, sessionId);
	}

}
