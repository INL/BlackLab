package nl.inl.blacklab.server.lib;

import nl.inl.blacklab.server.index.Index;

public class UserAdmin implements User {
    protected final String sessionId;

    protected UserAdmin(String sessionId) {
        this.sessionId = sessionId;
    }

    @Override
    public String getSessionId() {
        return sessionId;
    }
    public String getUserId() { return "_superuser_"; }
    public String getUserDirName() { return User.getUserDirNameFromId("_superuser_"); }
    
    public boolean mayReadIndex(Index index) { return true; }
    public boolean mayWriteIndex(Index index) { return true; }
    public boolean mayShareIndex(Index index) { return true; }
    public boolean mayDeleteIndex(Index index) { return true; }
    public boolean isOwnerOfIndex(Index index) { return false; }

    public boolean mayManageFormatsFor(String userId) { return true; }
    public boolean mayManageFormatsFor(User user) { return true; }
}
