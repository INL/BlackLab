package nl.inl.blacklab.server.auth;

import java.util.Map;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.search.SearchManager;
import nl.inl.blacklab.server.search.UserRequest;

/**
 * Authentication system used for debugging.
 *
 * Requests from debug IPs (specified in config file) are automatically logged
 * in as the specified userId.
 */
public class AuthDebugFixed implements AuthMethod {

    static final Logger logger = LogManager.getLogger(AuthDebugFixed.class);

    private final String userId;
    
    /**
     * 192.168.0.0 - 192.168.255.255
     * 172.16.0.0 - 172.31.255.255 
     * 10.0.0.0 - 10.255.255.255
     */
    private static final Pattern PATT_LOCALSUBNET = Pattern.compile("^(10\\.|172\\.(1[6-9]|2\\d|3[01])|192\\.168)");

    public AuthDebugFixed(Map<String, Object> parameters) {
        boolean hasUserId = parameters.containsKey("userId");
        int expectedParameters = hasUserId ? 1 : 0;
        if (parameters.size() > expectedParameters)
            logger.warn("AuthDebugFixed only takes one parameter (userId), but other parameters were passed.");
        Object u = parameters.get("userId");
        this.userId = u != null ? u.toString() : "DEBUG-USER";
    }

    public User determineCurrentUser(UserRequest request) {

        String sessionId = request.getSessionId();

        // Is client on debug IP or on the local network?
        SearchManager searchMan = request.getSearchManager();
        String ip = request.getRemoteAddr();
        
        boolean isLocalNetwork = PATT_LOCALSUBNET.matcher(ip).find();
        if (!isLocalNetwork && !searchMan.config().getDebug().isDebugMode(ip) && !searchMan.config().getAuthentication().isOverrideIp(ip)) {
            return User.anonymous(sessionId);
        }

        // Return the appropriate User object
        return User.fromIdAndSessionId(userId, sessionId);
    }

}
