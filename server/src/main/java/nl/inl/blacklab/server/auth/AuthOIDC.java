package nl.inl.blacklab.server.auth;

import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.pac4j.core.credentials.extractor.BearerAuthExtractor;
import org.pac4j.core.profile.ProfileManager;
import org.pac4j.http.client.direct.HeaderClient;
import org.pac4j.oidc.client.OidcClient;
import org.pac4j.oidc.config.OidcConfiguration;
import org.pac4j.oidc.profile.OidcProfile;

import com.nimbusds.oauth2.sdk.auth.ClientAuthenticationMethod;

import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.search.UserRequest;

public class AuthOIDC implements AuthMethod {

    private final Logger logger = LogManager.getLogger(AuthOIDC.class);

    final HeaderClient client;

    final String adminRole;

    /** If true, use the email address as the user id. If false, use the id from the OIDC provider. Defaults to true. */
    final boolean useEmailAsId;
    /**
     * If using email as the user id, and the email address is not available, use the id from the OIDC provider as a fallback. Defaults to false.
     * If false, the user will become anonymous when no email is provided.
     */
    final boolean useIdAsEmailFallback;

    UmaResourceActions<BlPermission> uma;

    public AuthOIDC(Map<String, String> params) {
        adminRole = Optional.ofNullable(params.get("adminRole")).orElse("admin");
        useEmailAsId = Optional.ofNullable(params.get("useEmailAsId")).map(Boolean::parseBoolean).orElse(true);
        useIdAsEmailFallback = Optional.ofNullable(params.get("useIdAsFallback")).map(Boolean::parseBoolean).orElse(false);

        String umaImplementation = params.get("uma");
        if ("keycloak".equals(umaImplementation)) {
            String endpoint = getProperty(params, "uma.keycloak.endpoint");
            String realm = getProperty(params, "uma.keycloak.realm");
            String clientId = getProperty(params, "uma.keycloak.clientId");
            String clientSecret = getProperty(params, "uma.keycloak.clientSecret");
            UmaResourceActions.UserIdProperty userIdProperty = UmaResourceActions.UserIdProperty.valueOf(getProperty(params, "uma.keycloak.userIdProperty").toUpperCase());

            if (StringUtils.isAnyBlank(endpoint, realm, clientId, clientSecret)) throw new IllegalArgumentException("Missing required parameter(s) for UMA Keycloak implementation.");
            this.uma = new UmaResourceActionsKeycloak<>(endpoint, realm, clientId, clientSecret, userIdProperty, BlPermission::valueOf, BlPermission::values);
        } else if (StringUtils.isNotBlank(umaImplementation)) {
            throw new IllegalArgumentException("Unknown UMA implementation: " + umaImplementation);
        }

        OidcConfiguration config = new OidcConfiguration();
        config.setDiscoveryURI(params.get("discoveryURI"));
        config.setClientId("unused");
        config.setSecret("unused");
        config.setWithState(false); // Disable CSRF (probably unused since we only use the oidcClient for setup, but just to be sure)
        config.setClientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC); // unused, but required to pass initialization step of oidcClient

        OidcClient oidcClient = new OidcClient(config);
        oidcClient.setCallbackUrl("unused"); // unused, but required to pass init.
        oidcClient.init();

        client = new HeaderClient();
        client.setCredentialsExtractor(new BearerAuthExtractor()); // use the Authorization header to extract the access token
        client.setAuthenticator(oidcClient.getAuthenticator());
        client.setName("oidc");
        client.setProfileCreator(oidcClient.getProfileCreator());
        client.setSaveProfileInSession(true);
        client.setAuthorizationGenerators(oidcClient.getAuthorizationGenerators());
    }

    /** Get parameter with key, throw an exception if it's missing or empty. */
    private String getProperty(Map<String, String> params, String key) {
        return Optional.ofNullable(params.get(key)).orElseThrow(() -> new IllegalArgumentException("Missing required parameter: " + key));
    }

    @Override
    public User determineCurrentUser(UserRequest request) {
        // For some info on how Pac4j works internally, see:
        // https://www.pac4j.org/blog/jee_pac4j_vs_pac4j_jee.html
        // Pac4j is an abstracted library, so we need to include a specific implementation.
        // in our case the wslib is meant to be abstracted away from a specific web framework, so we have to write some of our own code.

        ProfileManager manager = new ProfileManager(request.getContext(), request.getSessionStore());

        // First try to retrieve the profile from the session.
        // ProfileManager is only some util class that can store profiles in the session.
        // But it doesn't do anything to actually create the profile.
        // So we can use it to see if we already have the user profile, but we still need code to create it.
        return manager.getProfile().or(() -> client
            // If we don't have a profile in the session, we need to create one.
            // GetCredentials calls the CredentialsExtractor (BearerAuthExtractor) in the client.
            // Basically it just extracts the authorization header
            // and wraps it in a class that indicates what the string is (in this case a bearer token).
            .getCredentials(request.getContext(), request.getSessionStore())
            // Decode the credentials to a Pac4j profile. Normally we'd be done here, but BlackLab has its own user system.
            // NOTE: for profile creation code see OidcProfileCreator.java
            .flatMap(c -> client.getUserProfile(c, request.getContext(), request.getSessionStore()))
        )
        // Convert the profile to a BlackLab user.
        .map(profile -> {
            OidcProfile p = (OidcProfile) profile;
            manager.save(true, p, false);

            final boolean isAdmin = p.getRoles().contains(adminRole) || request.getSearchManager().config().getAuthentication().isOverrideIp(request.getContext().getRemoteAddr());
            final String sessionId = request.getSessionId();

            if (uma != null) {
                return new UserUMA(uma, ((OidcProfile) profile).getAccessToken().getValue(), sessionId, isAdmin);
            }

            // if not using email: use id
            String userId;
            if (!useEmailAsId) userId = p.getId();
            // using email, make sure it's verified
            else if (p.getEmailVerified()) userId = p.getEmail();
            // using email, but not verified, use id as fallback
            else if (useIdAsEmailFallback) { userId = p.getId(); logger.debug("Email not verified for user {}. Using id as fallback", p.getId()); }
            // using email, but not verified, and using id as fallback is disabled, don't use any id
            else { userId = null; logger.debug("Email not verified for user {}. Continuing as anonymous (useIdAsEmailFallback is false).", p.getId()); }

            if (StringUtils.isBlank(userId)) {
                return User.anonymous(sessionId);
            }

            return isAdmin ? User.superuser(sessionId) : User.loggedIn(userId, sessionId);
        })
        .orElseGet(() -> User.anonymous(request.getSessionId()));
    }
}
