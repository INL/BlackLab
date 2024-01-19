package nl.inl.blacklab.server.auth;

import java.util.Optional;

import org.pac4j.core.context.WebContext;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.credentials.Credentials;
import org.pac4j.core.credentials.TokenCredentials;
import org.pac4j.core.credentials.extractor.BearerAuthExtractor;
import org.pac4j.core.profile.UserProfile;
import org.pac4j.core.profile.creator.ProfileCreator;
import org.pac4j.oidc.credentials.OidcCredentials;
import org.pac4j.oidc.profile.creator.OidcProfileCreator;

import com.nimbusds.oauth2.sdk.token.BearerAccessToken;

/**
 * Special implementation of CredentialsExtractor for OIDC JWT access tokens.
 * Many OIDC providers (e.g. Keycloak) use JWT access tokens, but the default implementation assumes an opaque token.
 * We can skip the request to the user-info endpoint when the access token is already a JWT.
 * <br>
 * But by default there is no way to pass data from the Authenticator to the ProfileCreator, except through the Credentials object.
 * (the default flow looks a little like this):
 *<pre>
 *     Credentials creds = CredentialsExtractor.extract()
 *     authenticator.validate(creds) // throws exception if not valid
 *     profileCreator.create(creds) // creates profile from creds
 *</pre>
 *
 * We can re-use the default OidcProfileCreator if we can manipulate the credentials object a little bit, depending on whether we have a JWT or an opaque token.
 * In the case of a JWT, we can pretend our access token is really an ID token, in the case of an opaque token, we can just pass through the access token.
 * The ProfileCreator will then call the user-info endpoint and use the returned data to create the profile.
 */
public class OidcJwtAccessTokenProfileCreator implements ProfileCreator {
    OidcProfileCreator delegate;
    public OidcJwtAccessTokenProfileCreator(OidcProfileCreator delegate) {
        this.delegate = delegate;
    }

    @Override
    public Optional<UserProfile> create(Credentials credentials, WebContext context, SessionStore sessionStore) {
        if (credentials instanceof OidcCredentials && ((OidcCredentials) credentials).getIdToken() == null) {
            // We have a JWT access token, but the default OidcProfileCreator expects an ID token if we pass in OidcCredentials.
            // We'll have to change our Credentials type to be of type TokenCredentials, so the OidcProfileCreator will treat them as an opaque access token
            // and call the user-info endpoint.
            credentials = new TokenCredentials(((OidcCredentials) credentials).getAccessToken().getValue());
        }
        // At this point we should have:
        // a) TokenCredentials representing an opaque access token
        // b) OidcCredentials representing a JWT access token, with the JWT set in the ID token field.
        return delegate.create(credentials, context, sessionStore);
    }
}
