package nl.inl.blacklab.server.auth;

import java.util.Set;

import nl.inl.blacklab.server.index.Index;
import nl.inl.blacklab.server.lib.User;

public class UserOIDC implements User {
    String token;
    String sessionId;
    UmaResourceActionsKeycloak<BlPermission> uma;

    public UserOIDC(UmaResourceActionsKeycloak<BlPermission> uma, String accessToken, String sessionId) {
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


    public boolean mayReadIndex(Index index) {
        return uma.hasPermission(this.token, NameOrId.id(index.getId()), NameOrId.id(index.getUserId()), Set.of(BlPermission.READ));
    }
    public boolean mayWriteIndex(Index index) {
        return uma.hasPermission(this.token, NameOrId.id(index.getId()), NameOrId.id(index.getUserId()), Set.of(BlPermission.WRITE));
    }
    public boolean mayShareIndex(Index index) {
        return uma.hasPermission(this.token, NameOrId.id(index.getId()), NameOrId.id(index.getUserId()), Set.of(BlPermission.SHARE));
    }
    public boolean mayDeleteIndex(Index index) {
        return uma.hasPermission(this.token, NameOrId.id(index.getId()), NameOrId.id(index.getUserId()), Set.of(BlPermission.DELETE));
    }
    public boolean isOwnerOfIndex(Index index) { return index.getUserId().equals(getUserId()); }

    // formats don't use permissions currently, only the owner (correct userId may manage)
}
