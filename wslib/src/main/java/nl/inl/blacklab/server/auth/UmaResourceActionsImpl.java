package nl.inl.blacklab.server.auth;

import java.io.Closeable;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.text.ParseException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;

import com.fasterxml.jackson.core.JsonProcessingException;

import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.keycloak.authorization.client.AuthzClient;
import org.keycloak.authorization.client.Configuration;
import org.keycloak.representations.idm.authorization.PermissionRequest;
import org.keycloak.representations.idm.authorization.PermissionTicketRepresentation;
import org.keycloak.representations.idm.authorization.ResourceRepresentation;

import com.fasterxml.jackson.core.type.TypeReference;
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

public class UmaResourceActionsImpl implements UmaResourceActions, Closeable {

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

    public static class Permission {
        // ids
        String id; // id of the permission ticket (see this as the instance of the permission)
        String owner; // id of the owner of the resource
        String resource; // id of the resource
        String scope; // id of the scope (i.e. permission)
        String requester; // id of the user who requested the permission.

        String scopeName;
        String resourceName;
        String ownerName;
        String requesterName;

        boolean granted;
    }

    public static class NameOrId {
        private final String id;
        private final String name;

        public NameOrId(String id, String name) {
            this.id = id;
            this.name = name;
            if (id == null && name == null) {
                throw new IllegalArgumentException("Either id or name must be non-null");
            }
        }

        public static NameOrId id(String id) {
            return new NameOrId(id, null);
        }
        public static NameOrId name(String name) {
            return new NameOrId(null, name);
        }

        public boolean isId() {
            return id != null;
        }
        public boolean isName() {
            return name != null;
        }

        public String getId() throws NullPointerException {
            Objects.requireNonNull(id);
            return id;
        }

        public String getName() throws NullPointerException {
            Objects.requireNonNull(name);
            return name;
        }

        /** Get the id if it exists, otherwise the name */
        public String get() {
            return isId() ? id : name;
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
        public BlPermission[] mayGrant() {
            switch (this) {
            case SHARE: return new BlPermission[] { BlPermission.READ, BlPermission.SHARE };
            case ADMIN: return new BlPermission[] { BlPermission.READ, BlPermission.WRITE, BlPermission.DELETE, BlPermission.SHARE, BlPermission.ADMIN };
            default: return new BlPermission[] {};
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

    private final CloseableHttpClient http = HttpClients.createDefault();

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
    public String createResource(User owner, String resourceName, String resourceDisplayName, String resourcePath, String... permissionsName) {
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
        String id = client.protection().resource().findByName(resourceName).getId();
        client.protection().resource().delete(id);
    }

    /**
     * TODO implement sharing.
     * We need to impersonate the owner if the current access token is not the owner.
     * We also need to check whether the current user has the required permissions to assign the permissions for others.
     */
    @Override
    public void updatePermissions(String ownerAccessToken, String resourceName, String resourcePath, String otherUser, boolean grant,
            String... permissionName)
            throws IOException, ParseException {
        String ownerName = tokenToJwt(ownerAccessToken).getStringClaim(this.userIdProperty.toString());
        // Unfortunately this will result in a call to the Authorization Server.
        String resourceId = getResourceId(User.loggedIn(ownerName, null), resourceName, resourcePath);
        String otherUserId = getUserId(User.loggedIn(otherUser, null));

        for (String permission : permissionName) {
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

            CloseableHttpResponse response = http.execute(req);
            response.close();
        }
    }

    @Override
    public Map<UMAResource, Map<String, List<String>>> getPermissionsOnMyResources(String ownerAccessToken)
            throws IOException, ParseException {

        HttpUriRequest req = RequestBuilder.create("get")
                .setUri(client.getServerConfiguration().getPermissionEndpoint() + "/ticket")
                .addHeader("Authorization", "Bearer " + ownerAccessToken)
                .addParameter("returnNames", "true") // return names of the requester, resource, and scope, instead of just ids.
                .addParameter("granted", "true")
                .addParameter("owner", tokenToJwt(ownerAccessToken).getSubject()) // ownerName doesn't work, but passing username into "owner" would also work.
                .build();

        CloseableHttpResponse response = http.execute(req);
        // parse as Permission[]
        List<Permission> permissions = new JsonMapper().readValue(response.getEntity().getContent(), new TypeReference<List<Permission>>(){});
        response.close();

        Map<UMAResource, Map<String, List<String>>> ret = new HashMap<>();
        for (Permission permission : permissions) {
            UMAResource resource = new UMAResource(permission.resource, permission.resourceName, null, permission.owner, permission.ownerName, null);
            ret.computeIfAbsent(resource, k -> new HashMap<>()).computeIfAbsent(permission.requesterName, k -> new ArrayList<>()).add(permission.scopeName);
        }

        return ret;
    }

    @Override
    public Map<UMAResource, Set<BlPermission>> getMyPermissionsOnResources(String userAccessToken)
            throws IOException {
        // TODO check if permissions already encoded inside the access/rpt token.
        HttpUriRequest req = RequestBuilder.create("get")
                .setUri(client.getServerConfiguration().getPermissionEndpoint() + "/ticket")
                .addHeader("Authorization", "Bearer " + userAccessToken)
                .addParameter("returnNames", "true") // return names of the requester, resource, and scope, instead of just ids.
                .addParameter("granted", "true")
                // requesterName doesn't work, but passing the username into "requester" does seem to work. Either way use the user id, since it's contained in the token anyway.
                .addParameter("requester", tokenToJwt(userAccessToken).getSubject())
                .build();

        CloseableHttpResponse response = http.execute(req);
        // parse as Permission[]
        List<Permission> permissions = new JsonMapper().readValue(response.getEntity().getContent(), new TypeReference<List<Permission>>(){});
        response.close();

        Map<UMAResource, Set<BlPermission>> ret = new HashMap<>();
        for (Permission permission : permissions) {
            UMAResource resource = new UMAResource(permission.resource, permission.resourceName, null, permission.owner, permission.ownerName, null);
            BlPermission p = BlPermission.valueOf(permission.scopeName.toUpperCase());
            for (BlPermission implied : p.implies()) {
                ret.computeIfAbsent(resource, k -> new HashSet<>()).add(implied);
            }
        }

        return ret;
    }

    /**
     * @param owner the owner of the resource.
     * @param resource name or id of the resource on the Authorization Server.
     * @param permissions
     * @return
     * @throws IOException
     */
    @Override
    public String createPermissionTicket(NameOrId owner, NameOrId resource, BlPermission... permissions)
            throws IOException {
        HttpUriRequest req = RequestBuilder.create("post")
                .setUri(client.getServerConfiguration().getPermissionEndpoint())
                .addHeader("Authorization", "Bearer " + client.obtainAccessToken())
                .addHeader("Content-Type", "application/json")
                .setEntity(new StringEntity(new JsonMapper().writeValueAsString(Map.of(
                        "resource_id", getResourceId(owner, resource),
                        "resource_scopes", List.of(permissions)
                ))))
                .build();

        // Returns { "ticket": "ticket_id" }
        CloseableHttpResponse response = http.execute(req);
        response.close();

        return JsonMapper.builder().build().readTree(response.getEntity().getContent()).get("ticket").asText();
    }

    public String getUserId(NameOrId user) throws IOException {
        if (user.isId()) return user.getId();

        HttpUriRequest req = RequestBuilder.create("get")
                .setUri(client.getServerConfiguration().getUserinfoEndpoint())
                .setUri(client.getServerConfiguration().getUserinfoEndpoint() + "/admin/realms/" + client.getServerConfiguration().getRealm() + "/users")
                .addParameter(this.userIdProperty.toString(), user.getName()) // other options are username, firstName, lastName, search . The default is infix search.
                .addParameter("exact", "true") // exact match only, disable infix searching.
                .addHeader("Authorization", "Bearer " + client.obtainAccessToken())
                .build();

        CloseableHttpResponse response = http.execute(req);
        /* Returns Array<{id: string, username: string, enabled: boolean, emailVerified: boolean, email: string}> */
        // Read into array of maps
        return new JsonMapper().readValue(response.getEntity().getContent(), new TypeReference<List<Map<String, String>>>(){}).stream()
            .filter(u -> u.get(this.userIdProperty.toString()).equals(user.getName()))
            .findFirst()
            .orElseThrow(() -> new IOException("User not found: " + user.getName()))
            .get("id");
    }

    public String getUserId(String accessToken) throws MalformedURLException {
        // unpack token, if it fails, introspect it.
        JWTClaimsSet claims = this.tokenToJwt(accessToken);
        if (claims != null) return claims.getSubject();

        // introspect token
        claims = this.introspectAccessToken(accessToken);
        return claims.getSubject();
    }


    @Override
    public String getResourceId(NameOrId owner, NameOrId resource) throws IOException {
        if (resource.isId()) return resource.id;

        HttpUriRequest req = RequestBuilder.create("get")
                .setUri(client.getServerConfiguration().getResourceRegistrationEndpoint())
                .addHeader("Authorization", "Bearer " + client.obtainAccessToken())
                .addParameter("owner", owner.isId() ? owner.id : owner.name)
                .addParameter("name", resource.name)
                .addParameter("type", RESOURCE_TYPE)
//                .addParameter("deep", "true") can be used to get the entire resource object, false (default) only returns the ids as a string[]
                .addParameter("exactName", "true")
                .build();

        CloseableHttpResponse response = http.execute(req);

        String[] resourceIds = new JsonMapper().readValue(response.getEntity().getContent(), String[].class);
        return resourceIds.length == 0 ? null : resourceIds[0];
    }

    /**
     *
     * @param userAccessToken token for one of the two parties
     * @param owner the owner of the resource
     * @param resource the resource
     * @param otherUser the other party
     * @param permission the permission
     * @return
     */
    @Override
    public String getPermissionId(String userAccessToken, NameOrId owner, NameOrId resource, NameOrId otherUser, BlPermission permission) throws IOException {
        HttpUriRequest req = RequestBuilder.create("get")
                .setUri(client.getServerConfiguration().getPermissionEndpoint() + "/ticket")
                .addHeader("Authorization", "Bearer " + userAccessToken)
                .addParameter("owner", owner.get()) // name or id are both fine for this request (it's inconsistent with some other requests)
                .addParameter("requester", otherUser.get()) // name or id are both fine for this request (it's inconsistent with some other requests)
                .addParameter(resource.isId() ? "resourceId" : "resource", resource.get()) // this one does however differ between name and id...
                .addParameter("scopeName", permission.toString())
//                .addParameter("deep", "true") can be used to get the entire resource object, false (default) only returns the ids as a string[]
                .addParameter("exactName", "true")
                .build();

        CloseableHttpResponse response = http.execute(req);

        String[] resourceIds = new JsonMapper().readValue(response.getEntity().getContent(), String[].class);
        return resourceIds.length == 0 ? null : resourceIds[0];
    }

    /**
     * Grant permission to a user on a resource owned by another user.
     *
     * Normally this would require the ticket of the owner of the resource.
     * But in order to support transitive permissions (user B can share resource of user A with user C), we need to be able to grant permissions on behalf of the owner.
     * We use the impersonation api for this.
     * This is specific to Keycloak, as there is no transitive permission granting mechanism defined for base UMA.
     *
     * For this, the client's Service Account should have the "impersonation" role (from the realm-management client).
     */
    @Override
    public void grantPermission(NameOrId owner, NameOrId resource, NameOrId otherUser, BlPermission... permissions)
            throws UnsupportedEncodingException {
        /*
        curl -X POST \
         http://${host}:${port}/realms/${realm_name}/authz/protection/permission/ticket \
         -H 'Authorization: Bearer '$access_token \
         -H 'Content-Type: application/json' \
         -d '{
           "resource": "{resource_id}",
           "requester": "{user_id}",
           "granted": true,
           "scopeName": "view"
         }'
             */


        // Get impersonation token
        /*
        curl --location --request POST 'http://localhost:8180/auth/realms/tenant/protocol/openid-connect/token' \
        --header 'Content-Type: application/x-www-form-urlencoded' \
        --data-urlencode 'client_id=<source-client-id>' \
        --data-urlencode 'client_secret=<source-client-secret>' \
        --data-urlencode 'grant_type=urn:ietf:params:oauth:grant-type:token-exchange' \
        --data-urlencode 'subject_token=<access token got in step one>' \
        --data-urlencode 'requested_token_type=urn:ietf:params:oauth:token-type:access_token' \
        --data-urlencode 'requested_subject=<user id of tony123>'
        */
        HttpUriRequest req = RequestBuilder.create("post")
                .setUri(client.getServerConfiguration().getTokenEndpoint())
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .setEntity(new UrlEncodedFormEntity(List.of(
                    new BasicNameValuePair("client_id", client.getConfiguration().getResource()),
                    new BasicNameValuePair("client_secret", (String) client.getConfiguration().getCredentials().get("secret")),
                    new BasicNameValuePair("grant_type", "urn:ietf:params:oauth:grant-type:token-exchange"),
                    new BasicNameValuePair("subject_token", client.obtainAccessToken().getToken()),
                    new BasicNameValuePair("requested_token_type", "urn:ietf:params:oauth:token-type:access_token"),
                    new BasicNameValuePair("requested_subject", getUserId(owner))
                ), "UTF-8")
        );

        CloseableHttpResponse response = http.execute(req);

        String[] resourceIds = new JsonMapper().readValue(response.getEntity().getContent(), String[].class);
        return resourceIds.length == 0 ? null : resourceIds[0];
    }
    }

    @Override
    public void revokePermission(String resourceName, String resourcePath, String otherUser,
            String... permissionsName) {

    }

    @Override
    public boolean hasPermission(String accessToken, String resourceName, String resourceOwnerName, String resourcePath,
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

    private boolean isJwt(String token) {
        try {
            JWTParser.parse(token);
            return true;
        } catch (ParseException e) {
            return false;
        }
    }

    /*
    jwt:
    {
      "exp": 1702284753,
      "iat": 1702284453,
      "jti": "e508d09a-d9cc-4205-8ac7-df4536d23d6c",
      "iss": "https://login.ivdnt.org/realms/blacklab-test",
      "aud": "blacklab",
      "sub": "cc286e7b-3cfb-4965-ac31-2ebd8c6258e0",
      "typ": "Bearer",
      "azp": "blacklab",
      "session_state": "1604ccfa-bdf9-4880-9253-8fb454ff2c93",
      "acr": "1",
      "allowed-origins": [
        "http://localhost:8080"
      ],
      "realm_access": {
        "roles": [
          "default-roles-blacklab-test",
          "offline_access",
          "uma_authorization"
        ]
      },
      "resource_access": {
        "account": {
          "roles": [
            "manage-account",
            "manage-account-links",
            "view-profile"
          ]
        }
      },
      "authorization": {
        "permissions": [
          {
            "scopes": [
              "corpus:read"
            ],
            "rsid": "faac2c7c-66a5-4db0-9c85-e6298921b5b7",
            "rsname": "test-resource-koen"
          }
        ]
      },
      "scope": "email profile",
      "sid": "1604ccfa-bdf9-4880-9253-8fb454ff2c93",
      "email_verified": true,
      "name": "Jan Niestadt",
      "preferred_username": "jan",
      "given_name": "Jan",
      "family_name": "Niestadt",
      "email": "jan.niestadt@ivdnt.org"
    }


    introspection result:
    {
        "exp": 1702284753,
        "nbf": 0,
        "iat": 1702284453,
        "jti": "e508d09a-d9cc-4205-8ac7-df4536d23d6c",
        "aud": "blacklab",
        "typ": "Bearer",
        "acr": "1",
        "permissions": [
            {
                "scopes": [
                    "corpus:read"
                ],
                "rsid": "faac2c7c-66a5-4db0-9c85-e6298921b5b7",
                "rsname": "test-resource-koen",
                "resource_id": "faac2c7c-66a5-4db0-9c85-e6298921b5b7",
                "resource_scopes": [
                    "corpus:read"
                ]
            }
        ],
        "active": true
    } | { "active": false }
     */
    private JWTClaimsSet introspectRpt(String rpt) throws IOException {
        HttpUriRequest req = RequestBuilder.create("post")
                .setUri(client.getServerConfiguration().getIntrospectionEndpoint())
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .setEntity(new UrlEncodedFormEntity(List.of(
                    new BasicNameValuePair("token", accessToken),
                    new BasicNameValuePair("token_type_hint", isRpt ? "requesting_party_token" : "access_token"),
                    new BasicNameValuePair("client_id", client.getConfiguration().getResource()),
                    new BasicNameValuePair("client_secret", (String) client.getConfiguration().getCredentials().get("secret"))
                ), "UTF-8"))
                .build();

        CloseableHttpResponse response = http.execute(req);
    }

    private JWTCLaimsSet decodeRpt(String rpt) {

    }

    /** Token without Bearer prefix */
    // TODO move to utils
    // TODO when parsing fails (opaque token), try to introspect it and return the result of that
    // TODO cache these until the token expires.
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
    private void doPermission(String ownerAccessToken, String resourceId, String otherUserId, String permission, boolean grant) throws IOException {
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

    @Override
    public void close() throws IOException {
        http.close();
    }
}
