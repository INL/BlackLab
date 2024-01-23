package nl.inl.blacklab.server.auth;

import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.pac4j.http.client.direct.HeaderClient;
import org.pac4j.oidc.client.OidcClient;
import org.pac4j.oidc.config.OidcConfiguration;
import org.pac4j.oidc.profile.OidcProfile;
import org.pac4j.oidc.profile.creator.OidcProfileCreator;
import org.pac4j.oidc.profile.creator.TokenValidator;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jwt.JWT;
import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.auth.ClientAuthenticationMethod;
import com.nimbusds.openid.connect.sdk.Nonce;
import com.nimbusds.openid.connect.sdk.claims.IDTokenClaimsSet;

import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.search.UserRequest;

/**
 * Authenticate a user using an OIDC provider.
 * <br>
 * Parameters:
 * <ul>
 *     <li>clientId: the name of this client/application in the OIDC provider. Should also be part of the "aud" claim in received tokens. Required.</li>
 *     <li>discoveryURI: The URI to the OIDC discovery document. Usually ends in "/.well-known/openid-configuration". Required.</li>
 *
 *     <li>adminRole: The role that should be considered admin. Defaults to "admin".</li>
 *     <li>useEmailAsId: If true, use the email address as the user id. If false, use the id from the OIDC provider. Defaults to true.</li>
 *     <li>useIdAsEmailFallback: Only has effect if useEmailAsId is true. If true, use the user id if their email is not verified. Defaults to false.</li>
 *
 *     <li>uma: User Managed Access implementation to use. Options [keycloak]. Optional</li>
 *     <ul>
 *         <li><b>Keycloak sub-options<b></li>
 *         <li>uma.keycloak.endpoint: base url of the keycloak server, excluding any realm etc. Required.</li>
 *         <li>uma.keycloak.realm: realm name in keycloak. Required.</li>
 *         <li>uma.keycloak.clientSecret: secret for this client. Required.</li>
 *         <li>uma.keycloak.userIdProperty: what profile property to use as username in this application, options [email, username]. required.</li>
 *     </ul>
 * </ul>
 */
// TODO unify username/email story between uma and non-uma.
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
        String clientId = getProperty(params, "clientId");
        String discoveryURI = getProperty(params, "discoveryURI");

        adminRole = Optional.ofNullable(params.get("adminRole")).orElse("admin");
        useEmailAsId = Optional.ofNullable(params.get("useEmailAsId")).map(Boolean::parseBoolean).orElse(true);
        useIdAsEmailFallback = Optional.ofNullable(params.get("useIdAsFallback")).map(Boolean::parseBoolean).orElse(false);

        String umaImplementation = params.get("uma");
        if ("keycloak".equals(umaImplementation)) {
            String endpoint = getProperty(params, "uma.keycloak.endpoint");
            String realm = getProperty(params, "uma.keycloak.realm");
            String clientSecret = getProperty(params, "uma.keycloak.clientSecret");
            UmaResourceActions.UserIdProperty userIdProperty = UmaResourceActions.UserIdProperty.valueOf(getProperty(params, "uma.keycloak.userIdProperty").toUpperCase());

            this.uma = new UmaResourceActionsKeycloak<>(endpoint, realm, clientId, clientSecret, userIdProperty, BlPermission::valueOf, BlPermission::values);
        } else if (StringUtils.isNotBlank(umaImplementation)) {
            throw new IllegalArgumentException("Unknown UMA implementation: " + umaImplementation);
        }

        OidcConfiguration config = new OidcConfiguration();
        config.setDiscoveryURI(discoveryURI);
        config.setClientId(clientId);
        config.setTokenValidator(new TokenValidator(config) {
            // this code only runs if the tokenis a JWT, but pac4j assumes it's an ID token, so it runs some validation we don't need.
            // Specifically, the azp (authorized party) claim is wrong in the access token. It has the ID of the frontend-client who requested the token, not this backend service.
            // This causes the validation to fail, even though the token is fine.
            // See IDTokenValidator
            // I can't find a way around this, so we just skip the pac4j validation.
            // Note that we do verify the token ourselves in the OidcJwtAccessTokenAuthenticator, so we're not skipping any security checks.
            @Override
            public IDTokenClaimsSet validate(JWT idToken, Nonce expectedNonce) throws BadJOSEException, JOSEException {
                try {
                    return new IDTokenClaimsSet(idToken.getJWTClaimsSet());
                } catch (ParseException | java.text.ParseException e) {
                    throw new RuntimeException(e);
                }
            }
        });
//        config.setSecret();
        config.setWithState(false); // Disable CSRF (probably unused since we only use the oidcClient for setup, but just to be sure)
        config.setClientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC); // unused, but required to pass initialization step of oidcClient

        OidcClient oidcClient = new OidcClient(config);
        oidcClient.setCallbackUrl("unused"); // unused, but required to pass init.
        oidcClient.init();

        client = new HeaderClient();
        client.setCredentialsExtractor(new OidcJwtAccessTokenCredentialsExtractor());
        client.setAuthenticator(new OidcJwtAccessTokenAuthenticator(config.getProviderMetadata(), clientId));
        client.setProfileCreator(new OidcJwtAccessTokenProfileCreator((OidcProfileCreator) oidcClient.getProfileCreator()));

//        client.setCredentialsExtractor(new BearerAuthExtractor()); // use the Authorization header to extract the access token
        // disable authenticator for now, it expects OIDCCredentials, but we have TokenCredentials.
        // OIDCCredentials are only available when we're using a callback url, etc. i.e. when we're not a direct client.
        // We need to implement our own authenticator that can handle TokenCredentials.
        // We probably also need to create our own OIDCProfileCreator, because the default one always instrospects the token, causing a call to the identity provider.
        // If the access token is a JWT (and it almost always is, at least in all implementations I'm aware of), we can just parse it ourselves.
        // And then only call the introspection endpoint when it turns out it's not a JWT but an opaque token.
//        client.setAuthenticator(oidcClient.getAuthenticator());
//        client.setAuthenticator(new SimpleTestTokenAuthenticator()); // only checks if the token is not empty
        client.setName("oidc");
        client.setSaveProfileInSession(true);
        client.setAuthorizationGenerators(oidcClient.getAuthorizationGenerators()); // implements mapping of bare profile where everything is in scopes, to the actual roles etc.
    }

    /** Get parameter with key, throw an exception if it's missing or empty. */
    private String getProperty(Map<String, String> params, String key) {
        return Optional.ofNullable(params.get(key)).orElseThrow(() -> new IllegalArgumentException("Missing required AuthOIDC parameter: " + key));
    }

    @Override
    public User determineCurrentUser(UserRequest request) {
        // For some info on how Pac4j works internally, see:
        // https://www.pac4j.org/blog/jee_pac4j_vs_pac4j_jee.html
        // Pac4j is an abstracted library, so we need to include a specific implementation.
        // in our case the wslib is meant to be abstracted away from a specific web framework, so we have to write some of our own code.

//        ProfileManager manager = new ProfileManager(request.getContext(), request.getSessionStore());

        // First try to retrieve the profile from the session.
        // ProfileManager is only some util class that can store profiles in the session.
        // But it doesn't do anything to actually create the profile.
        // So we can use it to see if we already have the user profile, but we still need code to create it.
        return client
        // If we don't have a profile in the session, we need to create one.
        // GetCredentials calls the CredentialsExtractor (BearerAuthExtractor) in the client.
        // Basically it just extracts the authorization header
        // and wraps it in a class that indicates what the string is (in this case a bearer token).
        // Then it runs the credentials through the Authenticator
        .getCredentials(request.getContext(), request.getSessionStore())
        // Decode the credentials to a Pac4j profile. Normally we'd be done here, but BlackLab has its own user system.
        // NOTE: for profile creation code see OidcProfileCreator.java
        .flatMap(c -> client.getUserProfile(c, request.getContext(), request.getSessionStore()))

        // Convert the profile to a BlackLab user.
        .map(profile -> {
            // TODO Need to invalidate the session somehow when the token expires.
            OidcProfile p = (OidcProfile) profile;
//            manager.save(true, p, false);

            final boolean isAdmin = p.getRoles().contains(adminRole);
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
        .orElseGet(() -> {
            logger.trace("No access token present, Continuing as anonymous user.");
            return User.anonymous(request.getSessionId());
        });
    }
}
