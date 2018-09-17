package nl.inl.blacklab.server.search;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
import nl.inl.blacklab.server.config.BLSConfigAuth;
import nl.inl.blacklab.server.exceptions.ConfigurationException;
import nl.inl.blacklab.server.jobs.User;

public class AuthManager {

    private static final Logger logger = LogManager.getLogger(AuthManager.class);

    /**
     * The authentication object, giving information about the currently logged-in
     * user (or at least a session id)
     */
    private Object authObj = null;

    /** The method to invoke for determining the current user. */
    private Method authMethodDetermineCurrentUser = null;

    private Method authMethodPersistUser;

    public AuthManager(String authClass, Map<String, Object> authParam) throws ConfigurationException {
        init(authClass, authParam);
    }

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

    private void init(String authClass, Map<String, ? extends Object> authParam) throws ConfigurationException {
        if (authClass.length() > 0) {
            try {
                if (!authClass.contains(".")) {
                    // Allows us to abbreviate the built-in auth classes
                    authClass = "nl.inl.blacklab.server.auth." + authClass;
                }
                Class<?> cl = Class.forName(authClass);
                authObj = cl.getConstructor(Map.class).newInstance(authParam);
                authMethodDetermineCurrentUser = cl.getMethod("determineCurrentUser", HttpServlet.class,
                        HttpServletRequest.class);
                try {
                    authMethodPersistUser = cl.getMethod("persistUser", HttpServlet.class, HttpServletRequest.class,
                            HttpServletResponse.class, User.class);
                } catch (NoSuchMethodException e) {
                    authMethodPersistUser = null; // ok, optional method
                }
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
            User user = User.anonymous(request.getSession().getId());
            return user;
        }

        // Is client on debug IP and is there a userid parameter?
        if (servlet.getSearchManager().config().getAuthentication().isOverrideIp(request.getRemoteAddr())
                && request.getParameter("userid") != null) {
            return User.loggedIn(request.getParameter("userid"), request.getSession().getId());
        }

        // Let auth system determine the current user.
        try {
            User user = (User) authMethodDetermineCurrentUser.invoke(authObj, servlet, request);
            return user;
        } catch (Exception e) {
            throw new RuntimeException("Error determining current user", e);
        }
    }

    public void persistUser(HttpServlet servlet, HttpServletRequest request, HttpServletResponse response, User user) {
        if (authMethodPersistUser != null) {
            // i.e. set cookie
            try {
                authMethodPersistUser.invoke(authObj, servlet, request, response, user);
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                throw new RuntimeException("Error persisting user information");
            }
        }
    }

}
