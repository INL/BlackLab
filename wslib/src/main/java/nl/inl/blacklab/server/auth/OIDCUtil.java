package nl.inl.blacklab.server.auth;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;

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

public class OIDCUtil {
    RemoteJWKSet<SecurityContext> keySource = new RemoteJWKSet<>(
            new URL(client.getServerConfiguration().getJwksUri()),
            new DefaultResourceRetriever(5000, 5000, 4));


    public OIDCUtil(String idpUrl, String clientId, String clientSecret) {

    }

    public Object decodeAccessToken(String accessToken) throws IOException {
        /**
         * Token without Bearer prefix
         */
        // TODO move to utils
        // TODO when parsing fails (opaque token), try to introspect it and return the result of that
        // TODO cache these until the token expires.
        // TODO RPT and Access Token have different shape. Map into a common shape.
        // TODO introspection for RPT doesn't contain very much info. We need to introspect the access token to get the user id.
        // So we need to make sure we never have the situation where we need to introspect an RPT to obtain a user ID (or some other non-permission claim).


        // Create a JWT processor for the access tokens
        ConfigurableJWTProcessor<SecurityContext> jwtProcessor = new DefaultJWTProcessor<>();

        // Set the required "typ" header "jwt" for access tokens
        jwtProcessor.setJWSTypeVerifier(new DefaultJOSEObjectTypeVerifier<>(JOSEObjectType.JWT));

        // This will cache and re-retrieve internally (15 minutes by defualt I think)
        // In future versions, migrate to JWKSourceBuilder.
        // See https://connect2id.com/products/nimbus-jose-jwt/examples/validating-jwt-access-tokens
        // we should cache this keysource object as well.
        RemoteJWKSet<SecurityContext> keySource = new RemoteJWKSet<>(
                new URL(client.getServerConfiguration().getJwksUri()),
                new DefaultResourceRetriever(5000, 5000, 4));

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

        // Process the token
        try {
            return jwtProcessor.process(accessTokenOrRPT, null);
        } catch (ParseException | BadJOSEException e) {
            // Invalid token
            System.err.println(e.getMessage());
            return null;
        } catch (JOSEException e) {
            // Key sourcing failed or another internal exception
            System.err.println(e.getMessage());
            throw new InternalServerError("Internal error while processing token", e.getMessage());
        }

    }
    public Object decodeRPT(String rpt) throws IOException {

    }

    public String getSubject(String accessToken) throws IOException {

    }
    public String getSubjectFromRPT(String rpt) throws IOException {

    }
}
