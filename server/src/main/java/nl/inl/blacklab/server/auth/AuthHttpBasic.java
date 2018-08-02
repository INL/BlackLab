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
 * Note that you will have to enable this in web.xml for this to work.
 */
public class AuthHttpBasic {

    static final Logger logger = LogManager.getLogger(RequestHandler.class);

    Decoder base64Decoder = Base64.getDecoder();

    public AuthHttpBasic(Map<String, Object> param) {
        // doesn't take any parameters
        if (param.size() > 0)
            logger.warn("Parameters were passed to " + this.getClass().getName() + ", but it takes no parameters.");
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
