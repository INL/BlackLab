package nl.inl.blacklab.server.auth;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;

import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.Forbidden;

import nl.inl.blacklab.server.exceptions.NotFound;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.keycloak.AuthorizationContext;
import org.keycloak.authorization.client.AuthzClient;
import org.keycloak.authorization.client.Configuration;
import org.keycloak.authorization.client.representation.TokenIntrospectionResponse;
import org.keycloak.authorization.client.resource.AuthorizationResource;
import org.keycloak.authorization.client.resource.PermissionResource;
import org.keycloak.authorization.client.resource.ProtectedResource;
import org.keycloak.authorization.client.resource.ProtectionResource;
import org.keycloak.authorization.client.util.Http;
import org.keycloak.protocol.oidc.client.authentication.ClientIdAndSecretCredentialsProvider;
import org.keycloak.representations.idm.authorization.AuthorizationRequest;
import org.keycloak.representations.idm.authorization.AuthorizationResponse;
import org.keycloak.representations.idm.authorization.Permission;
import org.keycloak.representations.idm.authorization.PermissionTicketRepresentation;
import org.keycloak.representations.idm.authorization.ResourceOwnerRepresentation;
import org.keycloak.representations.idm.authorization.ResourceRepresentation;
import org.keycloak.representations.idm.authorization.ScopeRepresentation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.nimbusds.jose.util.Base64;
import com.nimbusds.jwt.EncryptedJWT;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.oauth2.sdk.auth.JWTAuthentication;

import nl.inl.blacklab.server.lib.User;

/**
 * Some terminology:
 * <br>
 * <ul>
 *     <li>Resource Server: the server that hosts the resources (i.e. BlackLab)</li>
 *     <li>Authorization Server: the server that hosts the authorization server (i.e. Keycloak)</li>
 *     <li>Client: the application that wants to access the resources (i.e. corpus-frontend/some other webservice)</li>
 *     <li>Resource: the resource that is being accessed (i.e. the corpus)</li>
 *     <li>Resource Owner: the user that owns the resources (i.e. the corpus owner)</li>
 *     <li>Requesting Party: the user that wants to access the resources (i.e. the user that wants to search the corpus)</li>
 *     <li>Permission: the permission(s)(+the resource on which) that is being requested (i.e. read, write, etc.)</li>
 *
 *     <li>Access Token: a token that represents everything the Requesting Party (the non-owner user) is currently allowed to do</li>
 *     <li>Permission Ticket: A special security token type representing a permission request.
            It (opaquely) holds which new permissions are being requested on behalf of a (non-owner) user to be added to their Access Token, if the Access Token does not currently contain the correct permissions.
 *          It needs to be exchanged for a new updated Access Token at the Token Endpoint. After this step, the Access Token is called a Requesting Party Token (RPT).
 *          In keycloak, this is optional. As keycloak allows specifying the requested permissions directly using the "permission" (form-content/body) parameter.
 *          We are responsible for generating these (through the AS) and returning them to the client.
 *     </li>
 *     <li>Requesting Party Token (RPT): alias for the Access Token received by exchanging a Permission Ticket.
 *          The Client is responsible for processing our PT into an RPT.
 *     </li>
 *     <li>PAT - Protection API Access Token: a token that is required to access the Resource system (on behalf of the resource owner) in keycloak. I.e. create/delete resources, update who has access to it, etc.
 *          It's not required to retrieve current permissions on behalf of another user.
 *     </li>
 */
// TODO extract interface.
public class UMA {


    // See https://www.keycloak.org/docs-api/22.0.1/rest-api/index.html#UserRepresentation
    private static class KeycloakUserRepresentation {
//        @JsonProperty String self;
        @JsonProperty String id;
//        @JsonProperty String origin;
//        @JsonProperty long createdTimestamp;
        @JsonProperty String username;
        @JsonProperty boolean enabled;
//        @JsonProperty boolean totp;
        @JsonProperty boolean emailVerified;
//        @JsonProperty String firstName;
//        @JsonProperty String lastName;
        @JsonProperty String email;
//        @JsonProperty String federationLink;
//        @JsonProperty String serviceAccountClientId;
//        @JsonProperty Map<String, List<Object>> attributes;
//        @JsonProperty List<Object> credentials;
//        @JsonProperty Set<String> disableableCredentialTypes;
//        @JsonProperty List<String> requiredActions;
//        there is more, but we'll ignore it for now and hope the json parsing works.
    }

    // See https://login.ivdnt.org/realms/blacklab-test/.well-known/uma2-configuration
    private static class UMAMetadata {
        @JsonProperty String authorization_endpoint;
        /** Introspect token */
        @JsonProperty String introspection_endpoint;
        @JsonProperty String issuer;
        @JsonProperty String jwks_uri;
        /**
         * Permission ticket endpoint.
         * Create, read, update, and delete permission tickets.
         * A permission ticket is a token that contains which permissions are being requested. With it a new access token is generated that includes the requested permissions (if the permissions are granted).
         * So the flow is access_token + a set of required permissions -> permission_ticket -> access_token_with_permissions
         */
        @JsonProperty String permission_endpoint;
        @JsonProperty String policy_endpoint;
        /** Client registration endpoint. Not useful for us. */
        @JsonProperty String registration_endpoint;
        @JsonProperty String resource_registration_endpoint;
        @JsonProperty String token_endpoint;
    }

    private AuthzClient client;
    UMAMetadata endpointMetadata;

    String clientId;
    String clientSecret;

    // Keycloak specific.
    String endpoint;
    String realm;

    public UMA(String endpoint, String realm, String clientId, String clientSecret) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;

        try {
            client = AuthzClient.create(new Configuration(endpoint, realm, clientId, Map.of("secret", clientSecret), null));
            // might have to set the ClientCredentialsProvider if this fails (though providing the secret in the constructor should work I think?)
//            blacklabAccessToken = client.obtainAccessToken().getToken();
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(endpoint + "/.well-known/uma2-configuration"))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            endpointMetadata = new JsonMapper().readValue(response.body(), UMAMetadata.class);

            verifyServerSettings();
        } catch (IOException | InterruptedException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private void verifyServerSettings() {
        // todo check whether we have the correct roles in the realm, and things like that.
        // i.e. query-users and view-users, and some uma-related roles.
    }

    /**
     * Get the Protection API Access Token (PAT).
     * This is required whenever we want to create/update/delete a resource (i.e. corpus).
     * <pre>
     * Manual mode:
     * curl -X POST \
     *     -H "Content-Type: application/x-www-form-urlencoded" \
     *     -d 'grant_type=client_credentials&client_id=${client_id}&client_secret=${client_secret}' \
     *     "${server}/${realm_name}/protocol/openid-connect/token"
     * </pre>
     */
    public String getPAT() {
        return client.obtainAccessToken().getToken();
    }

    /**
     * Register the new corpus with keycloak. Returns the internal ID inside the keycloak database.
     * @param owner the user that owns the corpus
     * @param resourceName the name of the resource, this is the friendly name, not the internal ID, which we do not control.
     * @param corpusDisplayName the display name of the corpus, this is the name that is shown to the user.
     */
    // TODO determine strategy for public corpora. Perhaps null user (owner blacklab/clientId) with specific rule that allows all to read?
    public String createResource(User owner, String resourceName, String corpusDisplayName) {
        // TODO validate the resource does not already exist (though I'd expect keycloak to return an error, best to be safe).
        ResourceOwnerRepresentation ownerRepresentation = new ResourceOwnerRepresentation();
        // Hmm, users in Keycloak have an automatically generated ID, but for legacy compat we need the email address.
        // In the AuthOIDC implementation we map the email to the user ID. But that's not *actual* user id in the keycloak database.
        // So the email is just the name here?
        // We'd need to use the actual ID, and maybe use a backwards-compat elsewhere that uses the email as fallback when the ID doesn't match (and then update the corpus owner to the new ID in the existing corpus on disk).

        ownerRepresentation.setName(owner.getUserId());
        ownerRepresentation.setId(owner.getUserId());

        ResourceRepresentation newResourceSettings = new ResourceRepresentation(resourceName);
        newResourceSettings.addScope("corpus:read", "corpus:write", "corpus:delete", "corpus:share");

        // The resource owner should be able to manage permissions for other users, but not manage the resource (i.e. the corpus) itself.
        // This setting disables that.
        // Permission management is actually not part of core uma spec, but keycloak has it as an extension.
        newResourceSettings.setOwnerManagedAccess(false);
        newResourceSettings.setType("corpus");
        newResourceSettings.setOwner(ownerRepresentation);
        newResourceSettings.setDisplayName(corpusDisplayName);
        newResourceSettings.setUris(Set.of("/corpora/" + resourceName)); // TODO use actual paths? Though that's dependent on the deployment... also perhaps url-escape this?

        // implicitly gets the PAT (I think).
        ResourceRepresentation response = client.protection().resource().create(newResourceSettings);
        return response.getId();
    }

    public String deleteResource(User owner, String corpus) {
        ResourceRepresentation resource = new ResourceRepresentation(corpus);
        String id = client.protection().resource().findByName(corpus).getId();
        client.protection().resource().delete(id);
        return id;
    }

    // The blacklab client needs 2 permissions to perform this operation:
    // query-users and view-users
    // without query we get a 403, without view-users we get empty results.

    /**
     * This function exists because normally sharing works on a pull basis: a user asks whether someone else will share their data
     * But we also want to enable push-based sharing: a user can share their data with someone else, without the other user having to ask for it.
     * In order to do that though, we need the user-id of the other user. The ID is internal to keycloak, and we do not control it.
     * it also doesn't make sense to expect someone to know the internal id (a long hash-link string) of others.
     * So we just search for the user by email address (TODO use email or username?), and use the id we get back.
     * @param email
     * @return
     * @throws URISyntaxException
     * @throws IOException
     */
    private Optional<String> getUserIdFromEmail(String email) {
        // We need access to the admin interface. For that the blacklab client needs to be a realm admin.
        // We need this function to enable the sharing functionality (i.e. share with user by email address).

        try {
            if (StringUtils.isBlank(email) || !email.contains("@")) {
                return Optional.empty();
            }

            HttpUriRequest req = RequestBuilder.create("get")
                    .setUri(new URI(endpoint + "/admin/realms/" + realm + "/users"))
                    .addParameter("email",
                            email) // other options are username, firstName, lastName, search . The default is infix search.
                    .addParameter("exact", "true") // exact match only, disable infix searching.
                    .addHeader("Authorization", "Bearer "
                            + client.obtainAccessToken()) // TODO use something that caches this token and rotates it. There is some rotating token provider in the authz lib I think.
                    .build();

            try (CloseableHttpResponse response = HttpClients.createDefault().execute(req)) {
                KeycloakUserRepresentation[] users = new JsonMapper().readValue(response.getEntity().getContent(),
                        KeycloakUserRepresentation[].class);
                return Arrays.stream(users).findFirst(u -> u.email.equals(email)).map(u -> u.id);
            }

            return Optional.empty();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Keycloak extension
     * This is not strictly in the UMA spec, but keycloak has this extension.
     * The path is also not specified in .well-known/uma2-configuration, but it's in the docs.
     *
     * curl -X POST \
     *      http://${host}:${port}/realms/${realm_name}/authz/protection/permission/ticket \
     *      -H 'Authorization: Bearer '$access_token \
     *      -H 'Content-Type: application/json' \
     *      -d '{
     *        "resource": "{resource_id}",
     *        "requester": "{user_id}",
     *        "granted": true,
     *        "scopeName": "view"
     *      }'
     * */

    // Maybe use this mechanism? Then push a permission that checks the email address claim? https://www.keycloak.org/docs/latest/authorization_services/index.html#_service_authorization_uma_policy_api

    /**
     * As someone with some current form of access, add access to other users.
     * This will fail if the user is not allowed to add the requested permissions.
     *
     * @param userAccessToken access token of the user that wants to change the permissions of others.
     * @param corpus
     * @param otherUsers emails of the other users.
     * @param permissions
     * @return
     */
    public void addPermissions(String userAccessToken, User owner, String corpus, Set<String> otherUsers, Set<String> permissions)
            throws URISyntaxException, IOException {
        // TODO some form of validation whether the user may add this permission.
        // configure the settings in keycloak (maybe?)
        // or retrieve the current permissions on the resource (introspect token or something?)
        // and check whether the user has the required permissions to add the new permissions.
        // since we're already hardcoding the permission names, we might as well hardcode these relations as well.
        // (it's also simpler to setup a new installation this way since you don't have to configure the external server).


        // we probably need some soft admin roles:
        // admin -> add * for others
        // delete -> implies read+write -> add nothing for others
        // write -> implies read -> add nothing for others
        // share -> implies read -> add read for others
        // read -> lowest level. add nothing for others

        String resourceId = client.protection().resource().findByName(corpus).getId();
        if (StringUtils.isBlank(resourceId)) {
            throw new NotFound("UMA_RESOURCE_NOT_FOUND", "Resource not found in UMA: " + corpus);
        }

        try (var http = HttpClients.createDefault()) {
            for (String email : otherUsers) {
                Optional<String> userId = getUserIdFromEmail(email);
                if (userId.isEmpty()) {
                    System.out.println("User not found: " + email);
                    continue;
                }

                for (String permission: permissions) {
                    PermissionTicketRepresentation ticket = new PermissionTicketRepresentation();
                    ticket.setResource(resourceId);
                    ticket.setRequester(userId.get());
                    ticket.setGranted(true);
                    ticket.setScopeName(permission);

                    try {
                        HttpUriRequest req = RequestBuilder.create("post")
                                .setUri(client.getServerConfiguration().getPermissionEndpoint() + "/ticket")
                                .setEntity(new StringEntity(new JsonMapper().writeValueAsString(ticket)))
                                // TODO right now we're using some random user's access token, but that might not work (might require the owner's access token)
                                // In that case we probably need to use the PAT instead. (our client token) - I hope that works.
                                .addHeader("Authorization", "Bearer " + userAccessToken)
                                .addHeader("Content-Type", "application/json")
                                .build();

                        String response = IOUtils.toString(http.execute(req).getEntity().getContent(),
                                StandardCharsets.UTF_8);
                        System.out.println("Response of sharing post: " + response);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }

    public String introspectPermissionTicket(String permissionTicket) {
        try {
            // see if the rpt is a jwt, if so, we can just decode it.
            // use java nimbus to parse the token
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(endpointMetadata.introspection_endpoint))
                    .header("Authorization", "Basic " + Base64.encode(clientId + ":" + clientSecret))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString("token_type_hint=requesting_party_token&token=" + permissionTicket))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();
        } catch (IOException | InterruptedException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean hasPermission(String accessToken, String corpus, String action) {
//        these both do the same?
//        List<Permission> permissions = client.authorization(accessToken).getPermissions(new AuthorizationRequest());
        TokenIntrospectionResponse i = client.protection().introspectRequestingPartyToken(accessToken);
        return i.getPermissions().stream().anyMatch(p -> p.getResourceName().equals(corpus) && p.getScopes().contains(action));
    }

    /**
     * Create a request for the permissions. The owner of the resource will have to approve this request in the keycloak ui.
     *
     * @param accessToken the access token of the user that wants to request access to the resource.
     * @param corpus the name of the resource (i.e. the corpus)
     * @param permissions the permissions that are being requested (i.e. read, write, etc.)
     * @return
     * @throws URISyntaxException
     * @throws IOException
     * @throws InterruptedException
     */
    public boolean requestPermission(String accessToken, String corpus, String... permissions)
            throws URISyntaxException, IOException, InterruptedException {
        // pretend no access, get permission ticket (PAT) from Keycloak
        // permission ticket needs to be sent to client.
        // the client can then use the permission ticket to get an RPT
        // next request should include the RPT, which we can use to validate access.

        // authorizationresource === get existing permissions
        // protectionresource === create/update/delete permissions, resources, etc. ???



        ResourceRepresentation existingResource = client.protection().resource().findByName(corpus);
        if (existingResource == null) {
            throw new BadRequest("UMA_RESOURCE_NOT_FOUND", "Resource not found: " + corpus);
        }

        // weird, this also doesn't exist in the default keycloak-authz-client implementation???
        // Multiple articles speak about an async flow where a user asks for permission, and the owner can asynchronously grant it
        // but I can't seem to find how to actually post that request to the server without using the submit_request parameter
        // and that parameter doesn't seem to be part of the official spec.

        client.protection(accessToken).permission().update();



        // create a new instance based on the configuration defined in keycloak.json
        TokenIntrospectionResponse i = client.protection().introspectRequestingPartyToken(rpt);



        return false;
    }
}
