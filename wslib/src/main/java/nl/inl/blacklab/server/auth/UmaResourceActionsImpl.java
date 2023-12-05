package nl.inl.blacklab.server.auth;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.text.ParseException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.keycloak.authorization.client.AuthzClient;
import org.keycloak.authorization.client.Configuration;
import org.keycloak.representations.idm.authorization.ResourceRepresentation;

import com.fasterxml.jackson.databind.json.JsonMapper;
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
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;

import nl.inl.blacklab.server.exceptions.InternalServerError;
import nl.inl.blacklab.server.lib.User;

public class UmaResourceActionsImpl implements UmaResourceActions {

    /**
     * What does the Blacklab user id correspond to in keycloak?
     * In Keycloak, the user ID is a UUID, in Blacklab it's a username or email.
     */
    public enum UserIdProperty {
        USERNAME,
        EMAIL;

        @Override
        public String toString() {
            return this == USERNAME ? "username" : "email";
        }

        public static UserIdProperty fromString(String s) {
            return s.equals("username") ? USERNAME : EMAIL;
        }
    }

    public enum BlPermission {
        READ,
        WRITE,
        DELETE,
        SHARE,
        ADMIN;

        public BlPermission[] implies() {
            switch (this) {
            case READ:
                return new BlPermission[] { BlPermission.READ };
            case WRITE:
                return new BlPermission[] { BlPermission.READ, BlPermission.WRITE };
            case DELETE:
                return new BlPermission[] { BlPermission.READ, BlPermission.WRITE, BlPermission.DELETE };
            case SHARE:
                return new BlPermission[] { BlPermission.READ, BlPermission.SHARE };
            case ADMIN:
                return new BlPermission[] { BlPermission.READ, BlPermission.WRITE, BlPermission.DELETE, BlPermission.SHARE, BlPermission.ADMIN };
            default:
                throw new RuntimeException("Unknown permission: " + this);
            }
        }
        public boolean implies(BlPermission other) {
            return Arrays.asList(implies()).contains(other);
        }
        public boolean isImplied(Iterable<BlPermission> others) {
            for (BlPermission other: others) {
                if (implies(other)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public String toString() {
            return this.name().toLowerCase();
        }
    }

    // technically this should be a URN, though keycloak seems to accept anything.
    // I don't see the benefit of using a URN, so we'll just use a string for now.
    private static final String RESOURCE_TYPE = "corpus";

    private AuthzClient client;

    private UserIdProperty userIdProperty;

    public UmaResourceActionsImpl(String endpoint, String realm, String clientId, String clientSecret, UserIdProperty userNameProperty) {
        client = AuthzClient.create(new Configuration(endpoint, realm, clientId, Map.of("secret", clientSecret), null));
        this.userIdProperty = userNameProperty;
    }

    /**
     * Create/register a new resource in the UMA server.
     * A resource is identified by either: the ID in the Authorization Server, or the name + owner.
     * A user cannot have two resources with the same name.
     * Ids are UUIDs, so they are unique, and they are automatically assigned on creation.
     *
     * @param owner the owner of the resource. This is the user in the application, not the Authorization Server.
     * @param resourceName the name for the resource in the Authorization Server.
     * @param resourceDisplayName A display name for the resource in the Authorization Server.
     * @param resourcePath the (url) path of the resource. This is the path in the application, not the authentication server. This can be used to identify the resource later, or create rules in the authentication server.
     * @param permissionsName the permissions to grant to the owner of the resource. Very freeform, so we can reuse this for other things than Blacklab.
     * @return the ID of the created resource in the Authorization Server.
     */
    @Override
    public String createResource(User owner, String resourceName, String resourceDisplayName, String resourcePath,
            String... permissionsName) {
        /*
        curl -v -X POST \
          http://${host}:${port}/realms/${realm_name}/authz/protection/resource_set \
          -H 'Authorization: Bearer '$pat \
          -H 'Content-Type: application/json' \
          -d '{
             "name":"Tweedl Social Service",
             "type":"http://www.example.com/rsrcs/socialstream/140-compatible",
             "icon_uri":"http://www.example.com/icons/sharesocial.png",
             "resource_scopes":[
                 "read-public",
                 "post-updates",
                 "read-private",
                 "http://www.example.com/scopes/all"
              ]
          }'
         */
        ResourceRepresentation resource = new ResourceRepresentation();
        resource.setName(resourceName);
        resource.setDisplayName(resourceDisplayName);
        resource.setType(RESOURCE_TYPE);
        resource.setOwner(getUserId(owner)); // omit this to create a resource owned by the application.
        resource.setOwnerManagedAccess(true); // This doesn't seem to actually do anything.
        resource.setUris(Set.of(resourcePath));
        resource.addScope(permissionsName); // in UMA, a permission is called a Scope, but we abstract that away.

        ResourceRepresentation response = client.protection().resource().create(resource);
        return response.getId();
    }

    @Override
    public void deleteResource(User owner, String resourceName) {
        /* curl -v -X DELETE \
         http://${host}:${port}/realms/${realm_name}/authz/protection/resource_set/{resource_id} \
         -H 'Authorization: Bearer '$pat
         */
        ResourceRepresentation resource = new ResourceRepresentation(resourceName);
        String id = client.protection().resource().findByName(resourceName).getId();
        client.protection().resource().delete(id);
    }

    @Override
    public void updatePermissions(String ownerAccessToken, String resourceName, String resourcePath, String otherUser, boolean grant,
            String... permissionName)
            throws IOException, ParseException {
        String ownerName = tokenToJwt(ownerAccessToken).getStringClaim(this.userIdProperty.toString());
        // Unfortunately this will result in a call to the Authorization Server.
        String resourceId = getResourceId(User.loggedIn(ownerName, null), resourceName, resourcePath);
        String otherUserId = getUserId(User.loggedIn(otherUser, null));

        for (String permission : permissionName) {
            HttpUriRequest req = RequestBuilder.create("delete")
                    .setUri(client.getServerConfiguration().getPermissionEndpoint() + "/ticket")
                    .addHeader("Authorization", "Bearer " + ownerAccessToken)
                    .addHeader("Content-Type", "application/json")
                    // Could also pass "requestName" and the username of the other. But we don't know if we're using usernames or emails (probably emails to be honest).
                    .setEntity(new StringEntity(new JsonMapper().writeValueAsString(Map.of(
                            "resource", resourceId,
                            "granted", grant,
                            "requester", otherUserId,
                            "scopeName", permission
                    ))))
                    .build();

            // TODO reuse client?
            try (CloseableHttpClient c = HttpClients.createDefault()) {
                CloseableHttpResponse response = c.execute(req);
                response.close();
            }
        }
    }

    @Override
    public Map<UMAResource, Map<String, List<String>>> getPermissionsOnMyResources(User user) {
        return null;
    }

    @Override
    public Map<UMAResource, List<String>> getMyPermissionsOnResources(User user) {
        return null;
    }

    @Override
    public String createPermissionTicket(User forUser, String forResource, String forResourcePath, String otherUser,
            boolean requestIfNotGranted, String... permissionsName) {
        return null;
    }

    @Override
    public String getResourceId(User owner, String resourceName, String resourcePath) {
        return null;
    }

    @Override
    public String[] getPermissionId(User owner, String resourceName, String resourcePath, String otherUser,
            String... permissionsName) {
        return new String[0];
    }

    @Override
    public void grantPermission(String resourceName, String resourcePath, String otherUser, String... permissionsName) {

    }

    @Override
    public void revokePermission(String resourceName, String resourcePath, String otherUser,
            String... permissionsName) {

    }

    @Override
    public boolean hasPermission(String accessToken, String resourceName, String resourcePath,
            String... permissionsName) {
        return false;
        // cache this stuff.
        // check audience, issuer, expiration, etc.
        // unfortunate.
        // can pac4j do this for us?

        // even though pac4j can only protect specific paths etc
        // according to the config you pre-define (so no dynamic access, as we do with uma)
        // but it does have a lot of the checks we need.
        // perhaps we can just do the jwt validation with pac4j, and then do the rest ourselves?
    }

    /** Get the internal keycloak user id */
    public String getUserId(String accessToken) throws ParseException, MalformedURLException {
        JWTClaimsSet jwt = tokenToJwt(accessToken);
        if (jwt == null) {


        }

        jwt.

        String email = user.getUserId(); // we need to standardize on this.
        // is there any way to do this otherwise.
        // either the username is the username, or the userid is the email.

        try {
            HttpUriRequest req = RequestBuilder.create("get")
                    .setUri(new URI(client.getServerConfiguration().getUserinfoEndpoint() + "/admin/realms/" + realm + "/users"))
                    .addParameter(this.userIdProperty, user.getUserId()) // other options are username, firstName, lastName, search . The default is infix search.
                    .addParameter("exact", "true") // exact match only, disable infix searching.
                    .addHeader("Authorization", "Bearer "
                            + client.obtainAccessToken()) // TODO use something that caches this token and rotates it. There is some rotating token provider in the authz lib I think.
                    .build();

            try (CloseableHttpResponse response = HttpClients.createDefault().execute(req)) {
                UMA.KeycloakUserRepresentation[] users = new JsonMapper().readValue(response.getEntity().getContent(),
                        UMA.KeycloakUserRepresentation[].class);
                return Arrays.stream(users).findFirst(u -> u.email.equals(email)).map(u -> u.id);
            }

            return Optional.empty();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    private boolean isJwt(String token) {
        try {
            JWTParser.parse(token);
            return true;
        } catch (ParseException e) {
            return false;
        }
    }

    private JWTClaimsSet introspectAccessToken(String accessToken) throws MalformedURLException {
        client.
    }

    /** Token without Bearer prefix */
    private JWTClaimsSet tokenToJwt(String accessTokenOrRPT) throws MalformedURLException {
        // Create a JWT processor for the access tokens
        ConfigurableJWTProcessor<SecurityContext> jwtProcessor = new DefaultJWTProcessor<>();

        // Set the required "typ" header "at+jwt" for access tokens
        jwtProcessor.setJWSTypeVerifier(new DefaultJOSEObjectTypeVerifier<>(JOSEObjectType.JWT));

        // This will cache and re-retrieve internally (15 minutes by defualt I think)
        // In future versions, migrate to JWKSourceBuilder.
        // See https://connect2id.com/products/nimbus-jose-jwt/examples/validating-jwt-access-tokens
        RemoteJWKSet<SecurityContext> keySource = new RemoteJWKSet<>(
                new URL(client.getServerConfiguration().getJwksUri()),
                new DefaultResourceRetriever(5000, 5000, 4));

        // Configure the JWT processor with a key selector to feed matching public
        // RSA keys sourced from the JWK set URL
        // We only allow RS256-signed tokens for now (which is the default anyway).
        jwtProcessor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256,keySource));

        // Set the required JWT claims for access tokens
        jwtProcessor.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<>(
                new JWTClaimsSet.Builder()
                        .issuer(client.getServerConfiguration().getIssuer())
                        .audience(client.getConfiguration().getResource()) // clientId is called resource in the keycloak config.
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
            return  jwtProcessor.process(accessTokenOrRPT, null);
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

    // DRY
    private void doPermission(String ownerAccessToken, String resourceId, String otherUserId, String permission, boolean grant) throws JsonProcessingException, UnsupportedEncodingException, IOException {
        HttpUriRequest req = RequestBuilder.create("post")
                .setUri(client.getServerConfiguration().getPermissionEndpoint() + "/ticket")
                .addHeader("Authorization", "Bearer " + ownerAccessToken)
                .addHeader("Content-Type", "application/json")
                // Could also pass "requestName" and the username of the other. But we don't know if we're using usernames or emails (probably emails to be honest).
                .setEntity(new StringEntity(new JsonMapper().writeValueAsString(Map.of(
                        "resource", resourceId,
                        "granted", grant,
                        "requester", otherUserId,
                        "scopeName", permission
                ))))
                .build();

        // TODO reuse client?
        try (CloseableHttpClient c = HttpClients.createDefault()) {
            CloseableHttpResponse response = c.execute(req);
            response.close();
        }
    }

}
