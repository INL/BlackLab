package nl.inl.blacklab.server.auth;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Optional;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jwt.JWTClaimNames;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;

import nl.inl.blacklab.server.exceptions.InternalServerError;

import org.keycloak.authorization.client.AuthzClient;

public class OIDCUtil {
    AuthzClient client;

    /**
     * This will cache and re-retrieve internally (15 minutes by default I think)
     * In future versions, migrate to JWKSourceBuilder.
     * See https://connect2id.com/products/nimbus-jose-jwt/examples/validating-jwt-access-tokens
     * we should cache this keysource object as well.
     */
    RemoteJWKSet<SecurityContext> keySource;

    ConfigurableJWTProcessor<SecurityContext> jwtProcessor;

    public OIDCUtil(AuthzClient client, String idpUrl, String clientId, String clientSecret) {
        this.client = client;
        try {
            this.keySource =
            new RemoteJWKSet<>(
                    new URL(client.getServerConfiguration().getJwksUri()),
                    new DefaultResourceRetriever(5000, 5000, 4));
        } catch (MalformedURLException ex) {
            throw new RuntimeException("Invalid JWK URI", ex);
        }

        // Create a JWT processor for the access tokens
        jwtProcessor = new DefaultJWTProcessor<>();
        // Set the required "typ" header "jwt" for access tokens
        jwtProcessor.setJWSTypeVerifier(new DefaultJOSEObjectTypeVerifier<>(JOSEObjectType.JWT));

        // Configure the JWT processor with a key selector to feed matching public
        // RSA keys sourced from the JWK set URL
        // We only allow RS256-signed tokens for now (which is the default anyway).
        jwtProcessor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keySource));

        // Set the required JWT claims for access tokens
        jwtProcessor.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<>(
                        new JWTClaimsSet.Builder()
                                .issuer(client.getServerConfiguration().getIssuer())
                                .audience(client.getConfiguration()
                                        .getResource()) // clientId is called resource in the keycloak config.
                                .claim("email_verified", true) // This might be a keycloak-specific claim...
                                .expirationTime(Date.from(Instant.now().plusSeconds(60))) // 1 minute leeway
                                .build()
                        ,
                        new HashSet<>(Arrays.asList(
                                JWTClaimNames.ISSUER,
                                JWTClaimNames.SUBJECT, // user id
                                JWTClaimNames.ISSUED_AT,
                                JWTClaimNames.EXPIRATION_TIME,
                                JWTClaimNames.AUDIENCE,
                                JWTClaimNames.JWT_ID))
                )
        );
    }

    /**
     * Parse the token into a JWTClaimsSet object. Introspect the token if necessary.
     * (TODO) internally cache the token until it expires.
     * @param accessToken Token without Bearer prefix
     */
    public Optional<JWTClaimsSet> decodeAccessToken(String accessToken) {
        // TODO when parsing fails (opaque token), try to introspect it and return the result of that
        // TODO cache these until the token expires.
        // TODO RPT and Access Token have different shape. Map into a common shape.
        // TODO introspection for RPT doesn't contain very much info. We need to introspect the access token to get the user id.
        // I believe there are two introspection points in keycloak, but passing an RPT will work with either one, whereas passing an access token
        // will only work with the accesstoken introspection endpoint.
        // the rpt introspection point returns a lot less useful information.

        // Process the token
        try {
            return Optional.ofNullable(jwtProcessor.process(accessToken, null));
        } catch (ParseException | BadJOSEException e) {
            // Invalid token
            System.err.println(e.getMessage());
            return Optional.empty();
        } catch (JOSEException e) {
            // Key sourcing failed or another internal exception
            System.err.println(e.getMessage());
            throw new InternalServerError("Internal error while processing token", e.getMessage());
        }

    }
    public Optional<JWTClaimsSet> decodeRPT(String rpt) throws IOException {
        return decodeAccessToken(rpt); // seems to work the same in keycloak.
    }
}
