package nl.inl.blacklab.server.auth;

import java.net.MalformedURLException;
import java.text.ParseException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.credentials.Credentials;
import org.pac4j.core.credentials.authenticator.Authenticator;
import org.pac4j.core.exception.CredentialsException;
import org.pac4j.oidc.credentials.OidcCredentials;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.JWSAlgorithmFamilyJWSKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimNames;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.nimbusds.jwt.proc.JWTClaimsSetVerifier;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;

/** Custom implementation of oidc access token -> profile mapping that can work with JWT access tokens. (default pac4j implementation assumes opaque tokens). */
public class OidcJwtAccessTokenAuthenticator implements Authenticator {
    Logger logger = LogManager.getLogger(OidcJwtAccessTokenAuthenticator.class);

    OIDCProviderMetadata providerMetadata;

    RemoteJWKSet<SecurityContext> keySource;
    ConfigurableJWTProcessor<SecurityContext> jwtProcessor;

    public OidcJwtAccessTokenAuthenticator(OIDCProviderMetadata providerMetadata, String clientId) {
        try {
            this.providerMetadata = providerMetadata;
            this.keySource = new RemoteJWKSet<>(providerMetadata.getJWKSetURI().toURL(), new DefaultResourceRetriever(5000, 5000, 0));
            this.jwtProcessor = new DefaultJWTProcessor<>();
            jwtProcessor.setJWSTypeVerifier(new DefaultJOSEObjectTypeVerifier<>(JOSEObjectType.JWT));
            jwtProcessor.setJWSKeySelector(new JWSAlgorithmFamilyJWSKeySelector<>(JWSAlgorithm.Family.SIGNATURE, keySource));
            // Checks most of the usual suspects (expiry date, audience). We add some required claims on top.
            jwtProcessor.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<>(
                    clientId,
                    new JWTClaimsSet.Builder()
                            // no audience here, because audience in the token could be a set of audiences including ones unknown to us, and the claims in this list must match EXACTLY
                            .issuer(providerMetadata.getIssuer().toString())
                            .claim("email_verified", Boolean.TRUE) // This might be a keycloak-specific claim...
                            .build(),
                    // Required claims
                    new HashSet<>(Arrays.asList(
                            "email",
                            JWTClaimNames.ISSUER,
                            JWTClaimNames.SUBJECT, // user id
                            JWTClaimNames.ISSUED_AT,
                            JWTClaimNames.EXPIRATION_TIME,
                            JWTClaimNames.AUDIENCE,
                            JWTClaimNames.JWT_ID))
            ));
        } catch (MalformedURLException e) {
            throw new RuntimeException("Invalid JWKSet URI", e);
        }
    }

    @Override
    public void validate(Credentials credentials, WebContext context, SessionStore sessionStore) {
        if (!(credentials instanceof OidcCredentials))
            throw new CredentialsException("Unsupported credentials type: " + credentials.getClass().getName() + "\nThis class is meant to be used with OidcCredentials and the OidcProfileCreator only.");

        OidcCredentials oidcCredentials = (OidcCredentials) credentials;

        try {
            // Try parsing the access token as if it's a JWT.
            JWT jwt = JWTParser.parse(oidcCredentials.getAccessToken().toString());
            // The signature is verified automatically by the JWTProcessor, and now let's check the contents.
            // I.E. if the token is meant for us, not expired, etc.
            jwtProcessor.process(jwt, null);
            // Seems fine? Set the token on the credentials object.
            oidcCredentials.setIdToken(jwt);
        } catch (ParseException exception) {
            logger.debug("Token is not a JWT, assuming opaque token. Will try to call Identity Server userinfo endpoint later.", exception);
            // Token is not parseable as a jwt, so it might be an opaque token.
            // Pass it on to the OidcProfileCreator.
            // It will exchange the token for the userinfo, which will result in a profile or an error anyway.
        } catch (Exception e) {
            // Not a valid token?
            logger.debug("Token is not valid", e.getMessage());
            throw new CredentialsException("Invalid token", e);
        }
    }
}
