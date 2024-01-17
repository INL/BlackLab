package nl.inl.blacklab.server.auth;

import java.util.Set;

import nl.inl.blacklab.server.index.Index;
import nl.inl.blacklab.server.lib.User;

/**
 * A user that checks with the UMA server for permissions.
 * UMA stands for User Managed Access, i.e. a system where users may manage who and what can access their resources through an external Identity Provider.
 */
public class UserUMA implements User {
    UmaResourceActions<BlPermission> uma;
    String token;
    String sessionId;
    boolean superuser;

    public UserUMA(UmaResourceActions<BlPermission> uma, String accessToken, String sessionId, boolean superuser) {
        this.uma = uma;
        this.token = accessToken;
        this.sessionId = sessionId;
    }

    @Override
    public String getSessionId() {
        return sessionId;
    }

    @Override
    public String getUserId() {
        return uma.getUserId(this.token);
    }

    @Override
    public boolean isSuperuser() {
        return superuser;
    }
    @Override
    public boolean mayReadIndex(Index index) {
        return uma.hasPermission(this.token, NameOrId.id(index.getId()), NameOrId.id(index.getUserId()), Set.of(BlPermission.READ));
    }
    @Override
    public boolean mayWriteIndex(Index index) {
        return uma.hasPermission(this.token, NameOrId.id(index.getId()), NameOrId.id(index.getUserId()), Set.of(BlPermission.WRITE));
    }
    @Override
    public boolean mayShareIndex(Index index) {
        return uma.hasPermission(this.token, NameOrId.id(index.getId()), NameOrId.id(index.getUserId()), Set.of(BlPermission.SHARE));
    }
    @Override
    public boolean mayDeleteIndex(Index index) {
        return uma.hasPermission(this.token, NameOrId.id(index.getId()), NameOrId.id(index.getUserId()), Set.of(BlPermission.DELETE));
    }
    @Override
    public boolean isOwnerOfIndex(Index index) { return index.getUserId().equals(getUserId()); }

    // formats don't use permissions currently, only the owner (correct userId may manage)
}
