package nl.inl.blacklab.server.auth;

import com.fasterxml.jackson.core.JsonProcessingException;

import nl.inl.blacklab.server.lib.User;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.text.ParseException;
import java.util.List;
import java.util.Map;

public interface UmaResourceActions {
    class UMAResource {
        String id;
        String name;
        String displayName;
        String ownerId;
        String ownerName;
        String path;
        public UMAResource(String id, String name, String displayName, String ownerId, String ownerName, String path) {
            this.id = id;
            this.name = name;
            this.displayName = displayName;
            this.ownerId = ownerId;
            this.ownerName = ownerName;
            this.path = path;
        }
    }

    /** Returns the ID assigned to the resource */
    String createResource(User owner, String resourceName, String resourceDisplayName, String resourcePath, String... permissionsName);

    void deleteResource(User owner, String resourceName);

    /**
     * This is a non-standard UMA action, but keycloak supports it.
     * Normally the flow is that manual intervention by the resource owner (user) is required.
     * Keycloak has a mechanism where we can perform this manual intervention programmatically.
     * We however still need the access token of the resource owner.
     *
     * This means that, if the current user is the resource owner, we can perform this action.
     * if the current user is the requesting party (the user that wants to access the resource), we can't.
     */
    void updatePermissions(String ownerAccessToken, String resourceName, String resourcePath, String otherUser, boolean grant, String... permissionsName)
            throws IOException, ParseException;

    /**
     * List all permissions on all resources owned by the user.
     * I.E. who else has what access to my resources?
     *
     * Returns { [resourceName]: { [userName]: permission[] } }
     * @param user the user
     * @return the permissions
     */
    Map<UMAResource, Map<String, List<String>>> getPermissionsOnMyResources(User user);

    /**
     * List all permissions the current user has on all resources.
     * Returns { [resource]: permission[] }
     * @param user
     * @return
     */
    Map<UMAResource, List<String>> getMyPermissionsOnResources(User user);

    String createPermissionTicket(User forUser, String forResource, String forResourcePath, String otherUser, boolean requestIfNotGranted, String... permissionsName);

    // Utils
    String getResourceId(User owner, String resourceName, String resourcePath);
    String[] getPermissionId(User owner, String resourceName, String resourcePath, String otherUser, String... permissionsName);

    /** Map the user in the application/client to the ID of that same user in the authentication server */
    String getUserId(User user);

    // Specific stuff that will need work to implement
    // use impersonation to have blacklab-server act as the owner
    // https://github.com/CarrettiPro/keycloak-uma-delegation-poc
    void grantPermission(String resourceName, String resourcePath, String otherUser, String... permissionsName);
    void revokePermission(String resourceName, String resourcePath, String otherUser, String... permissionsName);

    boolean hasPermission(String accessToken, String resourceName, String resourcePath, String... permissionsName);
}

