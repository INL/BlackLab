package nl.inl.blacklab.server.auth;

import java.io.Closeable;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

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
import org.keycloak.representations.idm.authorization.ResourceRepresentation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.nimbusds.jwt.JWTClaimsSet;

import nl.inl.blacklab.server.exceptions.InternalServerError;

public class UmaResourceActionsKeycloak<T extends PermissionEnum<T>> implements Closeable, UmaResourceActions<T> {
    public static class UMAPermission {
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

    // technically this should be a URN, though keycloak seems to accept anything.
    // I don't see the benefit of using a URN, so we'll just use a string for now.
    private static final String RESOURCE_TYPE = "corpus";

    private AuthzClient client;

    final private UmaResourceActions.UserIdProperty userIdProperty;

    final private Function<String, T> getPermissionValue;
    final private Supplier<T[]> getPermissionValues;

    final private CloseableHttpClient http = HttpClients.createDefault();

    public UmaResourceActionsKeycloak(String endpoint, String realm, String clientId, String clientSecret, UmaResourceActions.UserIdProperty userNameProperty, Function<String, T> getPermissionValue, Supplier<T[]> getPermissionValues) {
        client = AuthzClient.create(new Configuration(endpoint, realm, clientId, Map.of("secret", clientSecret), null));
        this.userIdProperty = userNameProperty;
        this.getPermissionValue = getPermissionValue;
        this.getPermissionValues = getPermissionValues;
    }

    @Override
    public String createResource(UmaResourceActions.NameOrId owner, String resourceName, String resourceDisplayName, String resourcePath)
            throws IOException {
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
        resource.setOwnerManagedAccess(true); // This doesn't seem to actually do anything so far? Might just not have found out exactly what it does.
        resource.setUris(Set.of(resourcePath));
        // in UMA, a permission is called a Scope, but we abstract that away.
        // add all permissions
        resource.addScope(Arrays.stream(getPermissionValues.get()).map(T::toString).toArray(String[]::new));

        ResourceRepresentation response = client.protection().resource().create(resource);
        return response.getId();
    }

    @Override
    public void deleteResource(NameOrId owner, NameOrId resource) throws IOException {
        /* curl -v -X DELETE \
         http://${host}:${port}/realms/${realm_name}/authz/protection/resource_set/{resource_id} \
         -H 'Authorization: Bearer '$pat
         */
        String id = getResourceId(owner, resource);
        client.protection().resource().delete(id);
    }

    @Override
    public void updatePermissions(String ownerAccessToken, NameOrId resource, NameOrId otherUser, boolean grant,
            Set<T> permissionName) throws IOException {
        // Unfortunately this will result in a call to the Authorization Server.
        String resourceId = getResourceId(NameOrId.id(getUserId(ownerAccessToken)), resource);
        String otherUserId = getUserId(otherUser);

        for (T permission : permissionName) {
            HttpUriRequest req = RequestBuilder.create("post")
                    .setUri(client.getServerConfiguration().getPermissionEndpoint() + "/ticket")
                    .addHeader("Authorization", "Bearer " + ownerAccessToken)
                    .addHeader("Content-Type", "application/json")
                    // Could also pass "requestName" and the username of the other. But we don't know if we're using usernames or emails (probably emails to be honest).
                    .setEntity(new StringEntity(new JsonMapper().writeValueAsString(Map.of(
                            "resource", resourceId,
                            "granted", grant,
                            "requester", otherUserId,
                            "scopeName", permission.toString()
                    ))))
                    .build();

            try (CloseableHttpResponse response = http.execute(req)) {
                // TODO check response
            }
        }
    }

    @Override
    public void updatePermissionsAsApplication(NameOrId owner, NameOrId resource, NameOrId otherUser, boolean grant,
            Set<T> permissions)
            throws IOException {
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

        returns
        {
            "access_token": "123abc",
            "expires_in": 300,
            "refresh_expires_in": 0,
            "token_type": "Bearer",
            "not-before-policy": 0,
            "session_state": "6a336180-5d66-405b-875b-7c538fe07138",
            "scope": "email profile"
        }
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
                ), StandardCharsets.UTF_8))
                .build();

        final String impersonationToken;
        try (CloseableHttpResponse response = http.execute(req)) {
            impersonationToken = new JsonMapper().readValue(response.getEntity().getContent(), Map.class).get("access_token").toString();
        }

        // Grant permission

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
        for (T permission: permissions) {
            req = RequestBuilder.create("post")
                    .setUri(client.getServerConfiguration().getPermissionEndpoint() + "/ticket")
                    .addHeader("Authorization", "Bearer " + impersonationToken)
                    .addHeader("Content-Type", "x-www-form-urlencoded")
                    .setEntity(new StringEntity(new JsonMapper().writeValueAsString(Map.of(
                            "resource", getResourceId(owner, resource),
                            "granted", grant,
                            otherUser.isId() ? "requester" : "requesterName", otherUser.get(),
                            "scopeName", permission.toString()
                    ))))
                    .build();

            try (CloseableHttpResponse response = http.execute(req)) {
                // TODO check response
            }
        }
    }

    @Override
    public Map<NameOrId, Map<NameOrId, Set<T>>> getPermissionsOnMyResources(String ownerAccessToken)
            throws IOException {
        HttpUriRequest req = RequestBuilder.create("get")
                .setUri(client.getServerConfiguration().getPermissionEndpoint() + "/ticket")
                .addHeader("Authorization", "Bearer " + ownerAccessToken)
                .addParameter("returnNames", "true") // return names of the requester, resource, and scope, instead of just ids.
                .addParameter("granted", "true")
                .addParameter("owner", introspectAccessToken(ownerAccessToken).getSubject()) // ownerName doesn't work, but passing username into "owner" would also work.
                .build();

        try (CloseableHttpResponse response = http.execute(req)) {
            // parse as Permission[]
            List<UMAPermission> permissions = new JsonMapper().readValue(response.getEntity().getContent(), new TypeReference<List<UMAPermission>>() {});

            Map<NameOrId, Map<NameOrId, Set<T>>> ret = new HashMap<>();
            for (UMAPermission permission: permissions) {
                if (!permission.granted) continue; // skip denied permissions
                NameOrId resource = new NameOrId(permission.resource, permission.resourceName);
                ret
                    .computeIfAbsent(resource, k -> new HashMap<>())
                    .computeIfAbsent(new NameOrId(permission.requester, permission.requesterName), k -> new HashSet<>())
                    .add(getPermissionValue.apply(permission.scopeName));
            }

            return ret;
        }
    }

    @Override
    public Map<NameOrId, Set<T>> getMyPermissionsOnResources(String userAccessToken)
            throws IOException {
        // TODO check if permissions already encoded inside the access/rpt token.
        HttpUriRequest req = RequestBuilder.create("get")
                .setUri(client.getServerConfiguration().getPermissionEndpoint() + "/ticket")
                .addHeader("Authorization", "Bearer " + userAccessToken)
                .addParameter("returnNames", "true") // return names of the requester, resource, and scope, instead of just ids.
                .addParameter("granted", "true")
                // requesterName doesn't work, but passing the username into "requester" does seem to work. Either way use the user id, since it's contained in the token anyway.
                .addParameter("requester", introspectAccessToken(userAccessToken).getSubject())
                .build();

        try (CloseableHttpResponse response = http.execute(req)) {
            // parse as Permission[]
            List<UMAPermission> permissions = new JsonMapper().readValue(response.getEntity().getContent(),new TypeReference<List<UMAPermission>>() {});
            Map<NameOrId, Set<T>> ret = new HashMap<>();
            // add all implied permissions.
            for (UMAPermission permission: permissions) {
                if (!permission.granted) continue; // skip denied permissions
                NameOrId resource = new NameOrId(permission.resource, permission.resourceName);
                ret.computeIfAbsent(resource, k -> new HashSet<>())
                        .addAll(getPermissionValue.apply(permission.scopeName).implies());
            }
            return ret;
        }
    }

    @Override
    public String createPermissionTicket(NameOrId owner, NameOrId resource, Set<T> permissions)
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
        try (CloseableHttpResponse response = http.execute(req)) {
            return JsonMapper.builder().build().readTree(response.getEntity().getContent()).get("ticket").asText();
        }
    }

    @Override
    public String getUserId(NameOrId user) throws IOException {
        if (user.isId()) return user.getId();

        /*
        For keycloak, returns an array looking like:
            [{
                "id": "1245",
                "createdTimestamp": 1234,
                "username": "john.doe",
                "enabled": true,
                "totp": false,
                "emailVerified": true,
                "firstName": "John",
                "lastName": "Doe",
                "email": "j.doe@example.com",
                "disableableCredentialTypes": [],
                "requiredActions": [],
                "notBefore": 0,
                "access": {
                    "manageGroupMembership": false,
                    "view": true,
                    "mapRoles": false,
                    "impersonate": true,
                    "manage": false
                }
            }]
         */
        HttpUriRequest req = RequestBuilder.create("get")
                .setUri(client.getConfiguration().getAuthServerUrl() + "/admin/realms/" + client.getConfiguration().getRealm() + "/users")
                .addParameter(this.userIdProperty.toString(), user.getName()) // other options are username, firstName, lastName, search . The default is infix search.
                .addParameter("exact", "true") // exact match only, disable infix searching.
                .addHeader("Authorization", "Bearer " + client.obtainAccessToken())
                .build();

        try (CloseableHttpResponse response = http.execute(req)) {
            /* Returns Array<{id: string, username: string, enabled: boolean, emailVerified: boolean, email: string}> */
            // Read into array of maps
            return new JsonMapper().readValue(response.getEntity().getContent(),
                            new TypeReference<List<Map<String, String>>>() {
                            }).stream()
                    .filter(u -> u.get(this.userIdProperty.toString()).equals(user.getName()))
                    .findFirst()
                    .orElseThrow(() -> new IOException("User not found: " + user.getName()))
                    .get("id");
        }
    }

    @Override
    public String getUserId(String accessToken) throws MalformedURLException {
        // unpack token, if it fails, introspect it.
        JWTClaimsSet claims = this.introspectAccessToken(accessToken);
        if (claims != null) return claims.getSubject();

        // introspect token
        claims = this.introspectAccessToken(accessToken);
        return claims.getSubject();
    }

    @Override
    public JWTClaimsSet introspectAccessToken(String accessToken) {
        HttpUriRequest req = RequestBuilder.create("post")
                .setUri(client.getServerConfiguration().getIntrospectionEndpoint())
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .addHeader("Accept", "application/json")
                .setEntity(new UrlEncodedFormEntity(List.of(
                        new BasicNameValuePair("token", accessToken),
                        new BasicNameValuePair("client_id", client.getConfiguration().getResource()),
                        new BasicNameValuePair("client_secret", (String) client.getConfiguration().getCredentials().get("secret"))
                ), StandardCharsets.UTF_8))
                .build();

        /*
            "exp": 1702469667,
            "iat": 1702469367,
            "jti": "d5749367-5812-4aeb-9c6f-b54830dbca1f",
            "iss": "login.ivdnt.org",
            "aud": "account",
            "sub": "123abc",
            "typ": "Bearer",
            "azp": "our_client_id",
            "session_state": "123abc",
            "name": "John Doe",
            "given_name": "John",
            "family_name": "Doe",
            "preferred_username": "jdoe",
            "email": "j.doe@example.com",
            "email_verified": true,
            "acr": "1",
            "allowed-origins": [
                "..."
            ],
            "realm_access": {
                "roles": [
                    "offline_access",
                    "uma_authorization",
                    ...
                ]
            },
            "resource_access": {
                "blacklab": {
                    "roles": [
                        "uma_protection"
                    ]
                },
                "account": {
                    "roles": [
                        "manage-account",
                        "manage-account-links",
                        "view-profile"
                    ]
                }
            },
            "scope": "email profile",
            "sid": "123abc",
            "client_id": "our_client_id"",
            "username": "jdoe",
            "active": true
        }
         */
        try (CloseableHttpResponse response = http.execute(req)) {
            JsonMapper.builder().build().readTree(response.getEntity().getContent());

            if (response.getStatusLine().getStatusCode() != 200) {
                throw new InternalServerError("Failed to introspect access token: " + response.getStatusLine().getStatusCode() + " " + response.getStatusLine().getReasonPhrase());
            }
            return new JsonMapper().readValue(response.getEntity().getContent(), JWTClaimsSet.class);
        } catch (IOException e) {
            throw new InternalServerError("Failed to introspect access token", "INTROSPECT_ACCESS_TOKEN_ERROR", e);
        }


//        curl -L -X POST 'http://192.0.2.5:8080/realms/nginx/protocol/openid-connect/token/introspect' \
//        -H "Authorization: Basic bmdpbngtcGx1czo1M2Q2YzdlNy1iNDJjLTRiNjktODQwNC0zODIwMzg1ZWQ0MWE=" \
//        -H "Accept: application/json" \
//        -H "Content-Type: application/x-www-form-urlencoded" \
//        --data-urlencode 'token=eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJVOFVDY2MtWnppNW9xYVhPZVZnWmdsLUxURmpfYXJ3dlJ2dl91Mjc4ZWNrIn0.eyJleHAiOjE2NjU1ODQ5NTIsImlhdCI6MTY2NTU4NDY1MiwianRpIjoiNmJhNDY1ZDktNmVmYi00Mzk5LTgyMTUtZjcxNjk0MzdhYzZhIiwiaXNzIjoiaHR0cDovLzE5Mi4xNjguMTkzLjQ6ODA4MC9yZWFsbXMvbmdpbngiLCJhdWQiOiJhY2NvdW50Iiwic3ViIjoiYTk1MTE3YmYtMWEyZS00ZDQ2LTljNDQtNWZkZWU4ZGRkZDExIiwidHlwIjoiQmVhcmVyIiwiYXpwIjoibmdpbngtcGx1cyIsInNlc3Npb25fc3RhdGUiOiI5ODM2ZjVmZC05ODdmLTQ4NzUtYWM3NS1mN2RkNTMyNTA0N2MiLCJhY3IiOiIxIiwicmVhbG1fYWNjZXNzIjp7InJvbGVzIjpbImRlZmF1bHQtcm9sZXMtbmdpbngiLCJvZmZsaW5lX2FjY2VzcyIsIm5naW54LWtleWNsb2FrLXJvbGUiLCJ1bWFfYXV0aG9yaXphdGlvbiJdfSwicmVzb3VyY2VfYWNjZXNzIjp7ImFjY291bnQiOnsicm9sZXMiOlsibWFuYWdlLWFjY291bnQiLCJtYW5hZ2UtYWNjb3VudC1saW5rcyIsInZpZXctcHJvZmlsZSJdfX0sInNjb3BlIjoib3BlbmlkIHByb2ZpbGUgZW1haWwiLCJzaWQiOiI5ODM2ZjVmZC05ODdmLTQ4NzUtYWM3NS1mN2RkNTMyNTA0N2MiLCJlbWFpbF92ZXJpZmllZCI6ZmFsc2UsInByZWZlcnJlZF91c2VybmFtZSI6Im5naW54LXVzZXIiLCJnaXZlbl9uYW1lIjoiIiwiZmFtaWx5X25hbWUiOiIifQ.MIqg3Q-pXvVG04leKBiVaDGCjv4gfsp3JywCumQ3CIk8cck9Q6tptM2CWIznmQLi4K6RUu7i7TodTnZAMDids0c-igX8oEe6ZLuR_Ub9SQSdVLymforfGYcSNJfnVVGLF8KHqPeLOp0TVPXxf56Qv6BO7B6fDGBxUvDsWEsw_5ko5v1pRiSHK-VS3zjw5weoJBD4rnYo9ZdhqYwyzL_nrUEWd05uWs4H-zCLKjTHw0AVPFO9MJ6OawJ7sc8AKeLq4FOKg2A_mIDF7SDds43UUvfUAK5a2zoy5PYhhESx0C5V7YTaaJDtiGFH1iY27_Yj3DcEQDZBBhDTRKrs3K7wxA' \
//   | jq
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

    /**
     * This works with both an access token and an RPT.
     * It seems the only difference in keycloak is whether you pass the token_type_hint parameter.
     * It returns the normal shape you'd expect for whatever type of token you throw at it.
     * So you can introspect and RPT as if it were an access token, and vice versa.
     *
     * @param rpt
     * @return
     * @throws IOException
     */
    JWTClaimsSet introspectRpt(String rpt) throws IOException {
        HttpUriRequest req = RequestBuilder.create("post")
                .setUri(client.getServerConfiguration().getIntrospectionEndpoint())
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .setEntity(new UrlEncodedFormEntity(List.of(
                        new BasicNameValuePair("token", rpt),
                        new BasicNameValuePair("token_type_hint", "requesting_party_token"),
                        new BasicNameValuePair("client_id", client.getConfiguration().getResource()),
                        new BasicNameValuePair("client_secret",
                                (String) client.getConfiguration().getCredentials().get("secret"))
                ), "UTF-8"))
                .build();

        try (CloseableHttpResponse response = http.execute(req)) {
            JsonMapper.builder().build().readTree(response.getEntity().getContent());

            if (response.getStatusLine().getStatusCode() != 200) {
                throw new InternalServerError(
                        "Failed to introspect access token: " + response.getStatusLine().getStatusCode() + " "
                                + response.getStatusLine().getReasonPhrase());
            }
            return new JsonMapper().readValue(response.getEntity().getContent(), JWTClaimsSet.class);
        } catch (IOException e) {
            throw new InternalServerError("Failed to introspect access token", "RPT_INTROSPECT_ERROR", e);
        }
    }

    @Override
    public String getResourceId(NameOrId owner, NameOrId resource) throws IOException {
        if (resource.isId()) return resource.getId();

        HttpUriRequest req = RequestBuilder.create("get")
                .setUri(client.getServerConfiguration().getResourceRegistrationEndpoint())
                .addHeader("Authorization", "Bearer " + client.obtainAccessToken())
                .addParameter("owner", owner.get()) // either name or id is okay.
                .addParameter("name", resource.getName())
                .addParameter("type", RESOURCE_TYPE)
//                .addParameter("deep", "true") can be used to get the entire resource object, false (default) only returns the ids as a string[]
                .addParameter("exactName", "true")
                .build();

        try (CloseableHttpResponse response = http.execute(req)) {
            String[] resourceIds = new JsonMapper().readValue(response.getEntity().getContent(), String[].class);
            return resourceIds.length == 0 ? null : resourceIds[0];
        }
    }

    @Override
    public String getPermissionId(String userAccessToken, NameOrId owner, NameOrId resource, NameOrId otherUser,
            T permission) throws IOException {
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

        try (CloseableHttpResponse response = http.execute(req)) {
            String[] resourceIds = new JsonMapper().readValue(response.getEntity().getContent(), String[].class);
            return resourceIds.length == 0 ? null : resourceIds[0];
        }
    }


    @Override
    public boolean hasPermission(String accessToken, NameOrId resource, NameOrId owner, Set<T> permissions)
            throws IOException {

        // TODO check if permissions already encoded inside the access/rpt token.
        JWTClaimsSet jwt = this.introspectAccessToken(accessToken);
        if (jwt != null && jwt.getClaim("authorization") != null) {
            // TODO inspect what object we actually have.
            System.out.println("jwt.getClaim(\"authorization\") = " + jwt.getClaim("authorization"));
        }

        // Do we even need this, we can just request all permissions and check if the requested permission is in there.
        // but whatever
        /*
        curl -X POST \
        http://${host}:${port}/realms/${realm}/protocol/openid-connect/token \
        -H "Authorization: Bearer ${access_token}" \
        --data "grant_type=urn:ietf:params:oauth:grant-type:uma-ticket" \
        --data "audience={resource_server_client_id}" \
        --data "permission=Resource A#Scope A" \
        --data "permission=Resource B#Scope B"
        */

        // In order to request permission, a Permission Ticket is required.
        // The permission can then actually be requested by trading it for an RPT with submit_request.
        // That should only happen on request of the user.
        // Normally, we just check which permissions are attached to the accessToken
        // and go off that. Returning a 403 if the required permission is not present.
        // The client can then request the permission from the user, and try again.

        // NOTE: it seems there is a bug in keycloak where requesting with the resource name instead of ID will always grant permission
        // even if the user doesn't have permission on that resource.
        // Using the resource ID works fine though.
        // https://github.com/keycloak/keycloak/issues/25057
        String resourceId = getResourceId(owner, resource);
        List<BasicNameValuePair> parameters = new ArrayList<>();
        for (T p : permissions) parameters.add(new BasicNameValuePair("permission", resourceId + "#" + p.toString()));
        parameters.add(new BasicNameValuePair("grant_type", "urn:ietf:params:oauth:grant-type:uma-ticket"));
        parameters.add(new BasicNameValuePair("audience", client.getConfiguration().getResource()));
        parameters.add(new BasicNameValuePair("response_mode", "decision"));

        HttpUriRequest req = RequestBuilder.create("post")
                .setUri(client.getServerConfiguration().getTokenEndpoint())
                .addHeader("Authorization", "Bearer " + accessToken)
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .setEntity(new UrlEncodedFormEntity(parameters, StandardCharsets.UTF_8))
                .build();

        // return {"result":true} or 403 {"error":"access_denied","error_description":"not_authorized"}
        try (CloseableHttpResponse response = http.execute(req)) {
            if (response.getStatusLine().getStatusCode() == 200) {
                return JsonMapper.builder().build().readTree(response.getEntity().getContent()).get("result").asBoolean();
            } else if (response.getStatusLine().getStatusCode() == 403) {
                return false;
            } else {
                throw new InternalServerError("Failed to check permissions: " + response.getStatusLine().getStatusCode() + " " + response.getStatusLine().getReasonPhrase());
            }
        }
    }

    @Override
    public void close() throws IOException {
        http.close();
    }
}
