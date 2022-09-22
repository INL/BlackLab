package nl.inl.blacklab.server.requesthandlers;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.auth.AuthMethod;
import nl.inl.blacklab.server.config.BLSConfigAuth;
import nl.inl.blacklab.server.exceptions.ConfigurationException;
import nl.inl.blacklab.server.lib.User;

public class AuthManager {

    private static final Logger logger = LogManager.getLogger(AuthManager.class);

    /**
     * The authentication object, giving information about the currently logged-in
     * user (or at least a session id)
     */
    private AuthMethod authObj = null;

    public AuthManager(BLSConfigAuth authentication) throws ConfigurationException {
        Map<String, String> system = authentication.getSystem();
        String authClass = StringUtils.defaultString(system.get("class"));
        Map<String, String> authParam = new HashMap<>();
        for (Entry<String, String> entry: system.entrySet()) {
            if (!entry.getKey().equals("class"))
                authParam.put(entry.getKey(), entry.getValue());
        }
        init(authClass, authParam);
    }

    private void init(String authClass, Map<String, ?> authParam) throws ConfigurationException {
        if (authClass.length() > 0) {
            try {
                if (!authClass.contains(".")) {
                    // Allows us to abbreviate the built-in auth classes
                    authClass = "nl.inl.blacklab.server.auth." + authClass;
                }
                Class<? extends AuthMethod> cl = (Class<? extends AuthMethod>)Class.forName(authClass);
                authObj = cl.getConstructor(Map.class).newInstance(authParam);
            } catch (Exception e) {
                throw new ConfigurationException("Error instantiating auth system: " + authClass, e);
            }
            logger.info("Auth system initialized: " + authClass);
        } else {
            logger.info("No auth system configured");
        }
    }

    public User determineCurrentUser(BlackLabServer servlet, HttpServletRequest request) {
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
            return authObj.determineCurrentUser(servlet, request);
        } catch (Exception e) {
            throw new RuntimeException("Error determining current user", e);
        }
    }

    public void persistUser(HttpServlet servlet, HttpServletRequest request, HttpServletResponse response, User user) {
        // i.e. set cookie
        authObj.persistUser(servlet, request, response, user);
    }
}
