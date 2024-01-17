package nl.inl.blacklab.server.auth;

import java.io.IOException;
import java.net.MalformedURLException;
import java.text.ParseException;
import java.util.Map;
import java.util.Set;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;


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
 *          It (opaquely) holds which new permissions are being requested on behalf of a (non-owner) user to be added to their Access Token,
 *          if the Access Token does not currently contain the correct permissions.
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
public interface UmaResourceActions<T extends PermissionEnum<T>> {
    /**
     * Create/register a new resource in the UMA server.
     * A resource is identified by either: the ID in the Authorization Server, or the name + owner.
     * A user cannot have two resources with the same name.
     * Ids are UUIDs, so they are unique, and they are automatically assigned on creation.
     *
     * @param owner               the owner of the resource. This is the user in the application, not the Authorization Server.
     * @param resourceName        the name for the resource in the Authorization Server.
     * @param resourceDisplayName A display name for the resource in the Authorization Server.
     * @param resourcePath        the (url) path of the resource. This is the path in the application, not the authentication server. This can be used to identify the resource later, or create rules in the authentication server.
     * @return the ID of the created resource in the Authorization Server.
     */
    String createResource(NameOrId owner, String resourceName, String resourceDisplayName, String resourcePath);

    void deleteResource(NameOrId owner, NameOrId resource);

    /** Grant or revoke permissions as the owner of a resource. */
    void updatePermissions(String ownerAccessToken, NameOrId resource, NameOrId otherUser, boolean grant, Set<T> permissions);

    /**
     * Grant permission to a user on a resource owned by another user.
     * Normally this would require the Access Token of the owner of the resource.
     * But in order to support transitive permissions (user B can share resource of user A with user C), we need to be able to grant permissions on behalf of the owner.
     * We use the impersonation api for this.
     * This is specific to Keycloak, as there is no transitive permission granting mechanism defined for base UMA.
     * For this, the client's Service Account should have the "impersonation" role (from the 'realm-management' client).
     */
    void updatePermissionsAsApplication(NameOrId owner, NameOrId resource, NameOrId otherUser, boolean grant, Set<T> permissions);

    /** Returns { [resource]: { [user]: permission[] } } */
    Map<NameOrId, Map<NameOrId, Set<T>>> getPermissionsOnMyResources(String ownerAccessToken);

    /** Return { [resource]: permission[] } */
    Map<NameOrId, Set<T>> getMyPermissionsOnResources(String userAccessToken);

    /**
     * @param owner       the owner of the resource.
     * @param resource    name or id of the resource on the Authorization Server.
     * @param permissions which permissions (scopes) to request.
     * @return the ticket.
     * @throws IOException
     */
    String createPermissionTicket(NameOrId owner, NameOrId resource, Set<T> permissions);

    /** Get the user ID in the Authorization Server for the given user in the application. */
    String getUserId(NameOrId user);

    /** Get the user ID in the Authorization Server for the given user's access token in the application. */
    String getUserId(String accessToken);

    /** Parse the access token into a JWT, or use the server to introspect it if it's an opaque token. */
    JWTClaimsSet introspectAccessToken(String accessToken);

    /** Get the resource ID in the Authorization Server for the given resource in the application. */
    String getResourceId(NameOrId owner, NameOrId resource);

    /**
     * Get the ID of a specific permission for a specific resource for a specific user.
     * This is needed to update or delete a specific permission.
     *
     * @param userAccessToken token for one of the two parties
     * @param owner           the owner of the resource
     * @param resource        the resource
     * @param otherUser       the other party
     * @param permission      the permission
     * @return the permission ID
     */
    String getPermissionId(String userAccessToken, NameOrId owner, NameOrId resource, NameOrId otherUser, T permission);


    /** Does the access token grant all the given permissions on the given resource? */
    boolean hasPermission(String accessToken, NameOrId resource, NameOrId owner, Set<T> permissions);

    /**
     * What does the application's user ID correspond to in the Authorization Server?
     * This is a bit of interop code, previously we didn't store user's data under the subject claim (the user's ID) in the JWT, but under the username or email.
     * So when we get a token, we need to know what field to read our local user ID from (email/username).
     *
     * In Keycloak, the user ID is a UUID, in Blacklab it's an email (or maybe later a username).
     */
    enum UserIdProperty {
        USERNAME,
        EMAIL;

        @Override
        public String toString() {
            return this == USERNAME ? "username" : "email";
        }
    }
}
