package nl.inl.blacklab.server.lib;

/**
 * Represents either a unique (logged-in) user, or a unique session (when not
 * logged in).
 */
public class UserGeneric implements User {
    /** The user id if logged in; null otherwise */
    private final String userId;

    /** The session id */
    private final String sessionId;

    protected UserGeneric(String userId, String sessionId) {
        // Replace any non-URL-safe characters from userid with _.
        // Also leave out colon because we use colon as a separator
        // between userid and index name.
        this.userId = userId != null ? User.sanitize(userId) : null;
        this.sessionId = sessionId;
    }

    @Override
    public String getUserId() {
        return userId;
    }

    @Override
    public String getSessionId() { return sessionId; }

    @Override
    public String toString() {
        return getUserId() != null ? getUserId() : "SESSION:" + sessionId;
    }
}
