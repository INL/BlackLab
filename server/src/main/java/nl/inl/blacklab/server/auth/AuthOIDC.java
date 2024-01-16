package nl.inl.blacklab.server.auth;

import java.util.Map;
import java.util.Optional;

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

    public AuthOIDC(Map<String, String> params) {
        adminRole = Optional.ofNullable(params.get("adminRole")).orElse("admin");
        useEmailAsId = Optional.ofNullable(params.get("useEmailAsId")).map(Boolean::parseBoolean).orElse(true);
        useIdAsEmailFallback = Optional.ofNullable(params.get("useIdAsFallback")).map(Boolean::parseBoolean).orElse(false);

        OidcConfiguration config = new OidcConfiguration();
        config.setDiscoveryURI(params.get("discoveryURI"));
        config.setClientId("unused");//params.get("clientId"));
        config.setSecret("unused");//params.get("secret"));

        // Disable CSRF
        // Since we only consume the bearer token (not generate it), there is no way to supply a CSRF token to the client.
        config.setWithState(false);
        // unused, but required to pass initialization step of oidcclient.
        config.setClientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);

        OidcClient oidcClient = new OidcClient(config);
        oidcClient.setCallbackUrl("notused"); // unused, but required to pass init.
        oidcClient.init();

        client = new HeaderClient();
        client.setCredentialsExtractor(new BearerAuthExtractor()); // use the Authorization header to extract the access token
        client.setAuthenticator(oidcClient.getAuthenticator());
        client.setName("oidc");
        client.setProfileCreator(oidcClient.getProfileCreator());
        client.setSaveProfileInSession(true);
        client.setAuthorizationGenerators(oidcClient.getAuthorizationGenerators());
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
            manager.save(true, profile, false);

            OidcProfile p = (OidcProfile) profile;
            if (p.getRoles().contains(adminRole)) return User.superuser(request.getSessionId());

            String sessionId = request.getSessionId();
            if (!useEmailAsId) return User.loggedIn(p.getId(), sessionId);
            if (p.getEmailVerified()) return User.loggedIn(p.getEmail(), sessionId);

            boolean empty = p.getEmail() == null || p.getEmail().isBlank();
            String message = empty ? "Empty" : "Unverified";

            if (useIdAsEmailFallback) {
                logger.debug(message + " email address for user {}. Using id as fallback.", p.getId(), p.getEmailVerified());
                return User.loggedIn(p.getId(), sessionId);
            }
            logger.debug(message + " email address for user {}. Continuing as anonymous.", p.getId());
            return User.anonymous(sessionId);
        })
        .orElseGet(() -> User.anonymous(request.getSessionId()));
    }
}
