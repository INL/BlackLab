package nl.inl.blacklab.server.auth;

import java.util.Optional;

import org.pac4j.core.context.HttpConstants;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.credentials.Credentials;
import org.pac4j.core.credentials.TokenCredentials;
import org.pac4j.core.credentials.extractor.BearerAuthExtractor;
import org.pac4j.core.credentials.extractor.CredentialsExtractor;
import org.pac4j.core.credentials.extractor.HeaderExtractor;
import org.pac4j.oidc.credentials.OidcCredentials;

import com.nimbusds.oauth2.sdk.token.AccessToken;
import com.nimbusds.oauth2.sdk.token.BearerAccessToken;

/**
 * Special implementation of CredentialsExtractor for OIDC JWT access tokens.
 * Many OIDC providers (e.g. Keycloak) use JWT access tokens, but the default implementation of OIDC in Pac4j assumes an opaque token (i.e. one where it has to contact the server to retrieve the user info).
 * We can skip the request to the user-info endpoint when the access token is already a JWT.
 * <br>
 * However, there is no way to pass data from the Authenticator to the ProfileCreator, except through the Credentials object.
 * The flow looks a little like this:
 * <pre>
 *     Credentials creds = CredentialsExtractor.extract()
 *     authenticator.validate(creds) // <-- at this point we try to parse the token as a JWT
 *     profileCreator.create(creds) // creates profile from creds
 *</pre>
 *
 * We can re-use the default OidcProfileCreator if we can manipulate the credentials object a little bit, depending on whether we have a JWT or an opaque token.
 * In the case of a JWT, we can pretend our access token is really an ID token, in the case of an opaque token, we can just pass through the access token.
 * The ProfileCreator will then call the user-info endpoint and use the returned data to create the profile.
 */
public class OidcJwtAccessTokenCredentialsExtractor extends BearerAuthExtractor {
    @Override
    public Optional<Credentials> extract(final WebContext context, final SessionStore sessionStore) {
        return super.extract(context, sessionStore).map(creds -> {
            TokenCredentials tokenCreds = (TokenCredentials) creds;
            OidcCredentials newCreds = new OidcCredentials();
            newCreds.setAccessToken(new BearerAccessToken(tokenCreds.getToken()));
            return newCreds;
        });
    }
}
