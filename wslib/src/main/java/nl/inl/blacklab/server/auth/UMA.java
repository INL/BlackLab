package nl.inl.blacklab.server.auth;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.ParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.keycloak.AuthorizationContext;
import org.keycloak.authorization.client.AuthzClient;
import org.keycloak.authorization.client.Configuration;
import org.keycloak.authorization.client.representation.TokenIntrospectionResponse;
import org.keycloak.authorization.client.resource.AuthorizationResource;
import org.keycloak.authorization.client.resource.PermissionResource;
import org.keycloak.authorization.client.resource.ProtectedResource;
import org.keycloak.authorization.client.resource.ProtectionResource;
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

    private static class UMAMetadata {
        @JsonProperty
        String authorization_endpoint;

        /**
         * Introspect token
         */
        @JsonProperty
        String introspection_endpoint;

        @JsonProperty
        String issuer;

        @JsonProperty
        String jwks_uri;

        /**
         * Permission ticket endpoint.
         * Create, read, update, and delete permission tickets.
         * A permission ticket is a token that contains which permissions are being requested. With it a new access token is generated that includes the requested permissions (if the permissions are granted).
         * So the flow is access_token + a set of required permissions -> permission_ticket -> access_token_with_permissions
         */
        @JsonProperty
        String permission_endpoint;

        @JsonProperty
        String policy_endpoint;

        /**
         * Client registration endpoint
         */
        @JsonProperty
        String registration_endpoint;

        @JsonProperty
        String resource_registration_endpoint;

        @JsonProperty
        String token_endpoint;
    }

    private AuthzClient client;
    UMAMetadata endpointMetadata;

    String clientId;
    String clientSecret;

    public UMA(String endpoint, String realm, String clientId, String clientSecret) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;

        try {
            client = AuthzClient.create(new Configuration(endpoint, realm, clientId, Map.of("secret", clientSecret), null));


            // might have to set the ClientCredentialsProvider if this fails (though providing the secret in the constructor should work I think?)
            blacklabAccessToken = client.obtainAccessToken().getToken();


            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(endpoint + "/.well-known/uma2-configuration"))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            endpointMetadata = new JsonMapper().readValue(response.body(), UMAMetadata.class);
        } catch (IOException | InterruptedException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
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

    /** Register the new corpus with keycloak. Returns the internal ID inside the keycloak database. */
    public String createResource(User owner, String corpus, String corpusDisplayName) {

        // TODO validate the resource does not already exist (though I'd expect keycloak to return an error, best to be safe).

        ResourceOwnerRepresentation ownerRepresentation = new ResourceOwnerRepresentation();
        // Hmm, users in Keycloak have an automatically generated ID, but for legacy compat we need the email address.
        // In the AuthOIDC implementation we map the email to the user ID. But that's not *actual* user id in the keycloak database.
        // So the email is just the name here?
        // We'd need to use the actual ID, and maybe use a backwards-compat elsewhere that uses the email as fallback when the ID doesn't match (and then update the corpus owner to the new ID in the existing corpus on disk).

        ownerRepresentation.setName(owner.getUserId());
        ownerRepresentation.setId(owner.getUserId());

        ResourceRepresentation newResourceSettings = new ResourceRepresentation(corpus);
        newResourceSettings.addScope("corpus:read", "corpus:write", "corpus:delete", "corpus:share");

        // The resource owner should be able to manage permissions for other users, but not manage the resource (i.e. the corpus) itself.
        // This setting disables that.
        // Permission management is actually not part of core uma spec, but keycloak has it as an extension.
        newResourceSettings.setOwnerManagedAccess(false);
        newResourceSettings.setType("corpus");
        newResourceSettings.setOwner(ownerRepresentation);
        newResourceSettings.setDisplayName(corpusDisplayName);
        newResourceSettings.setUris(Set.of("/corpora/" + corpus)); // TODO use actual paths? Though that's dependent on the deployment...

        // implicitly gets the PAT (I think).
        ResourceRepresentation response = client.protection().resource().create(new ResourceRepresentation(corpus));
        return response.getId();
    }

    public String deleteResource(User owner, String corpus) {
        ResourceRepresentation resource = new ResourceRepresentation(corpus);
        String id = client.protection().resource().findByName(corpus).getId();
        client.protection().resource().delete(id);
        return id;
    }

    /** Keycloak extension
     * This is not strictly in the UMA spec, but keycloak has this extension.
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
    public String addPermissionsOnResource(String userAccessToken, User owner, String corpus, Set<String> otherUsers, Set<String> permissions) {
        // we have a problem where we need the user id inside keycloak, but we only have the email address.
        // how do we search the users.
        // (probably not allowed?) - when we create the corpus we should probably store the userid along with it (and not just the email address).
        // assume we did this, and we have the id here.


        String resourceId = client.protection().resource().findByName(corpus).getId();


        for (String userName : otherUsers) {
            // trying to find a way around having to know the user id of the third party user.
            // but we need to know the user id to create the permission ticket.

        }

        PermissionTicketRepresentation ticket = new PermissionTicketRepresentation();
        ticket.setResourceName(corpus);
        ticket.setResource(resourceId);
        ticket.setGranted(true);
        ticket.setScope();
        client.protection(userAccessToken).permission().create()
    }

    public void requestPermissionsOnResource(String corpus, Set<String> otherUsers, Set<String> permissions) {
        // TODO
    }

    public boolean hasPermission(String rptOrAccessToken, String resourceName, String... scopes) {
        // these both do the same?
//        TokenIntrospectionResponse i = client.protection().introspectRequestingPartyToken(token);

        // use evaluate option to receive boolean response (in keycloak's case)
        TokenIntrospectionResponse i = client.protection().introspectRequestingPartyToken(rptOrAccessToken);

        for (Permission p: i.getPermissions()) {
            if (p.getResourceName().equals(resourceName) && p.getScopes().containsAll(List.of(scopes))) {
                return true;
            }
        }

        return false;
    }

    public boolean requestPermissions(String userAccessToken, String corpus, String... permissions) {
//        curl -X POST \
//        http://${host}:${port}/realms/${realm}/protocol/openid-connect/token \
//        -H "Authorization: Bearer ${access_token}" \
//        --data "grant_type=urn:ietf:params:oauth:grant-type:uma-ticket" \
//        --data "ticket=${permission_ticket} \
//        --data "submit_request=true"


    }

    /** Permission tickets are supposed to be opaque, and there is an introspection endpoint to get the contents. but keycloak sends a signed jwt, so we don't have to call the introspection endpoint if we detect the ticket is a JWT. */
    public String getPermissionTicket(String userAccessToken, String corpus, String... permissions) {

    }

    public JWT tryParseJWT(String token) throws ParseException {

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

    public String getPermissionTicketForAllResources(String userAccessToken) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(endpointMetadata.token_endpoint))
                    .POST(HttpRequest.BodyPublishers.ofString("grant_type=urn:ietf:params:oauth:grant-type:uma-ticket"))
                    .build();
        }
    }

    public boolean hasPermission(String accessToken, String corpus, String action) {
        // these both do the same?
        TokenIntrospectionResponse i = client.protection().introspectRequestingPartyToken(accessToken);
//        List<Permission> permissions = client.authorization(accessToken).getPermissions(new AuthorizationRequest());

        for (Permission p: i.getPermissions()) {
            if (p.getResourceName().equals(corpus) && p.getScopes().contains(action)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Can the user represented by the access token perform the action on the resource?
     *
     * @param accessToken the accessToken or requesting party token (which is just an access token with an extra "security" property), i.e. the token that the user has for a specific resource
     * @param actions     the actions to be requested for the resource
     * @return true if the user has the permission, false otherwise
     */
    public String requestPermission(String accessToken, String corpus, String... actions)
            throws URISyntaxException, IOException, InterruptedException {
        // pretend no access, get permission ticket (PAT) from Keycloak
        // permission ticket needs to be sent to client.
        // the client can then use the permission ticket to get an RPT
        // next request should include the RPT, which we can use to validate access.

        // authorizationresource === get existing permissions
        // protectionresource === create/update/delete permissions, resources, etc. ???

        ResourceRepresentation existingResource = client.protection(blacklabAccessToken).resource().findById(corpus);
        if (existingResource == null) {
            throw new FileNotFoundException("Resource not found: " + corpus);
        }
        existingResource.addScope(actions);


        client.authorization(blacklabAccessToken).


                .findById(corpus);
        newResourceSettings.addScope(actions);
        newResourceSettings.update();

//
//        HttpResponse<String> response = HttpClient.newHttpClient()
//                .send(HttpRequest.newBuilder(new URI(endpointMetadata.token_endpoint))
//                        .POST(HttpRequest.BodyPublishers.ofString("grant_type=urn:ietf:params:oauth:grant-type:uma-ticket&audience=" + corpus + "&permission=" + action))
//                                .build(),
//                        HttpResponse.BodyHandlers.ofString());
//

        // create a new instance based on the configuration defined in keycloak.json
        TokenIntrospectionResponse i = client.protection().introspectRequestingPartyToken(rpt);

        for (Permission p: i.getPermissions()) {
            if (p.getResourceName().equals(corpus) && p.getScopes().contains(action)) {
                return true;
            }
        }

        return false;
    }

    public static boolean createResource(User user, String corpus) {
        // here we would validate internal stuff, such as access token is valid, user has < 10 corpora already.

        // get user info from access token
//        ResourceOwnerRepresentation user = client.

        ResourceRepresentation res = new ResourceRepresentation(corpus);
        res.addScope("corpus:read", "corpus:write", "corpus:delete", "corpus:share");
        res.setOwner(user.getUserId());
        res.setId(corpus);
        res.setUris(Set.of("/corpora/" + corpus));
        res.setOwnerManagedAccess(true);
        res.setType("corpus");

        ResourceRepresentation response = client.protection().resource().create(res);

        ResourceRepresentation response = getAuthzClient().protection().resource().create(stuffResource);
        stuff.setExternalId(response.getId());

        return response.getId();

    }

    public static void doThing2() {
        // create a new instance based on the configuration defined in keycloak.json
        AuthzClient authzClient = AuthzClient.create();

        // create an authorization request
        AuthorizationRequest request = new AuthorizationRequest();

        // add permissions to the request based on the resources and scopes you want to check access
        request.addPermission("Default Resource");

        // send the entitlement request to the server in order to
        // obtain an RPT with permissions for a single resource
        AuthorizationResponse response = authzClient.authorization("alice", "alice").authorize(request);
        String rpt = response.getToken();

        System.out.println("You got an RPT: " + rpt);

        // now you can use the RPT to access protected resources on the resource server
    }

    public static void createResource(String name) {
        // create a new instance based on the configuration defined in keycloak.json
        AuthzClient authzClient = AuthzClient.create();

        // create a new resource representation with the information we want
        ResourceRepresentation newResource = new ResourceRepresentation();

        newResource.setName("New Resource");
        newResource.setType("urn:hello-world-authz:resources:example");

        newResource.addScope(new ScopeRepresentation("urn:hello-world-authz:scopes:view"));

        ProtectedResource resourceClient = authzClient.protection().resource();
        ResourceRepresentation existingResource = resourceClient.findByName(newResource.getName());

        if (existingResource != null) {
            resourceClient.delete(existingResource.getId());
        }

        // create the resource on the server
        ResourceRepresentation response = resourceClient.create(newResource);
        String resourceId = response.getId();

        // query the resource using its newly generated id
        ResourceRepresentation resource = resourceClient.findById(resourceId);

        System.out.println(resource);
    }

    public static void introspect() {
        // create a new instance based on the configuration defined in keycloak.json
        AuthzClient authzClient = AuthzClient.create();

        // send the authorization request to the server in order to
        // obtain an RPT with all permissions granted to the user
        AuthorizationResponse response = authzClient.authorization("alice", "alice").authorize();
        String rpt = response.getToken();

        // introspect the token
        TokenIntrospectionResponse requestingPartyToken = authzClient.protection().introspectRequestingPartyToken(rpt);

        System.out.println("Token status is: " + requestingPartyToken.getActive());
        System.out.println("Permissions granted by the server: ");

        for (Permission granted: requestingPartyToken.getPermissions()) {
            System.out.println(granted);
        }
    }
}
