package nl.inl.blacklab.server.auth;

import java.io.IOException;
import java.net.MalformedURLException;
import java.text.ParseException;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;

import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.InternalServerError;

public interface UmaResourceActions<T extends Enum<T>> {
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
    String createResource(NameOrId owner, String resourceName, String resourceDisplayName, String resourcePath) throws IOException;

    void deleteResource(NameOrId owner, NameOrId resource) throws IOException;

    /** Grant or revoke permissions as the owner of a resource. */
    void updatePermissions(String ownerAccessToken, NameOrId resource, NameOrId otherUser, boolean grant,
            EnumSet<T> permissions) throws IOException;

    /**
     * Grant permission to a user on a resource owned by another user.
     * Normally this would require the Access Token of the owner of the resource.
     * But in order to support transitive permissions (user B can share resource of user A with user C), we need to be able to grant permissions on behalf of the owner.
     * We use the impersonation api for this.
     * This is specific to Keycloak, as there is no transitive permission granting mechanism defined for base UMA.
     * For this, the client's Service Account should have the "impersonation" role (from the 'realm-management' client).
     */
    void updatePermissionsAsApplication(NameOrId owner, NameOrId resource, NameOrId otherUser, boolean grant,
            EnumSet<T> permissions)
            throws IOException;

    /** Returns { [resource]: { [user]: permission[] } } */
    Map<NameOrId, Map<NameOrId, EnumSet<T>>> getPermissionsOnMyResources(String ownerAccessToken)
            throws IOException, ParseException;

    /** Return { [resource]: permission[] } */
    Map<NameOrId, EnumSet<T>> getMyPermissionsOnResources(String userAccessToken)
            throws IOException;

    /**
     * @param owner       the owner of the resource.
     * @param resource    name or id of the resource on the Authorization Server.
     * @param permissions which permissions (scopes) to request.
     * @return the ticket.
     * @throws IOException
     */
    String createPermissionTicket(NameOrId owner, NameOrId resource, EnumSet<T> permissions)
            throws IOException;

    /** Get the user ID in the Authorization Server for the given user in the application. */
    String getUserId(NameOrId user) throws IOException;

    /** Get the user ID in the Authorization Server for the given user's access token in the application. */
    String getUserId(String accessToken) throws MalformedURLException;

    /** Parse the access token into a JWT, or use the server to introspect it if it's an opaque token. */
    JWTClaimsSet introspectAccessToken(String accessToken);

    /** Get the resource ID in the Authorization Server for the given resource in the application. */
    String getResourceId(NameOrId owner, NameOrId resource) throws IOException;

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
    String getPermissionId(String userAccessToken, NameOrId owner, NameOrId resource, NameOrId otherUser,
            T permission) throws IOException;


    /** Does the access token grant all the given permissions on the given resource? */
    boolean hasPermission(String accessToken, NameOrId resource, NameOrId owner, EnumSet<T> permissions)
            throws IOException;

    default boolean isJwt(String token) {
        try {
            JWTParser.parse(token);
            return true;
        } catch (ParseException e) {
            return false;
        }
    }

    default JWTClaimsSet decodeRpt(String rpt) {
        // TODO
        return null;
    }

    /**
     * What does the application's user ID correspond to in the Authorization Server?
     * This is a bit of interop code, previously we didn't store user's data under the subject claim (the user's ID) in the JWT, but under the username or email.
     * So when we get a token, we need to know what field to read our local user ID from (email/username).
     *
     * In Keycloak, the user ID is a UUID, in Blacklab it's a username or email.
     */
    enum UserIdProperty {
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

    /**
     * Piece of interop code to convert between the ID and the name of a user or resource.
     * <br>
     * The ID is always the item's ID in the Authorization Server, the Name what identifies the item in the application.
     * These are not always the same, as the ID in the Authorization Server is typically a UUID, so not useful to the application.
     * We store things under a regular name, for users, an email address, for resources, a name the user picked.
     * Lots of operations come in with the name, but we need the ID to talk to the Authorization Server.
     * So we need to be able to convert between the two.
     */
    class NameOrId {
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

        /**
         * Get the id if it exists, otherwise the name
         */
        public String get() {
            return isId() ? id : name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            NameOrId nameOrId = (NameOrId) o;
            return Objects.equals(id, nameOrId.id) && Objects.equals(name, nameOrId.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, name);
        }
    }
}
