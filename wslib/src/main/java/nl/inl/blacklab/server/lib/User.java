package nl.inl.blacklab.server.lib;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

import nl.inl.blacklab.indexers.config.ConfigInputFormat;
import nl.inl.blacklab.server.index.Index;

public interface User {
    static String getUserDirNameFromId(String id) {
        // NOTE: we want a safe directory name, so instead of trying to
        // get rid of non-safe characters, we just strip everything that
        // isn't a regular letter and append an MD5 of the original id
        // for uniqueness.
        String stripped = id.replaceAll("[^a-zA-Z]", "_");
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            md5.reset();
            md5.update(id.getBytes());
            byte[] hashBytes = md5.digest();
            BigInteger hashInt = new BigInteger(1, hashBytes);
            String hashHex = hashInt.toString(16);
            StringBuilder zeroes = new StringBuilder();
            for (int i = 0; i < 32 - hashHex.length(); i++) {
                zeroes.append("0");
            }
            return stripped + "_" + zeroes + hashHex;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        //return FileUtil.sanitizeFilename(userId);
    }

    static String sanitize(String originalUserId) {
        if (originalUserId == null || originalUserId.isEmpty())
            return null;

        return originalUserId.replaceAll("[^a-zA-Z0-9\\-._!$&'()*+,;=@]", "_");
    }

    static boolean isValidUserId(String userId) {
        return userId.matches("^[a-zA-Z0-9\\-._!$&'()*+,;=@]+$");
    }

    /**
     * Create a new logged-in user.
     *
     * @param userId unique id identifying this user
     * @param sessionId the session id
     * @return the new user
     */
    static User loggedIn(String userId, String sessionId) {
        return new UserGeneric(userId, sessionId);
    }

    /**
     * Create a new superuser. This user has all permissions but owns nothing.
     *
     * @param sessionId
     * @return the new user.
     */
    static User superuser(String sessionId) {
        return new UserAdmin(sessionId);
    }

    /**
     * Create a new anonymous user.
     *
     * @param sessionId the session id
     * @return the new user
     */
    static User anonymous(String sessionId) {
        return new UserGeneric(null, sessionId);
    }

    default boolean isLoggedIn() { return getUserId() != null; }

    String getSessionId();

    /** May be null if not logged in. */
    String getUserId();

    default String uniqueId() {
        return getUserId() != null ? getUserId() : "S:" + getSessionId();
    }

    /** A short ID for use in logging and such. */
    default String uniqueIdShort() {
        String str = uniqueId();
        return str.length() > 6 ? str.substring(0, 6) : str;
    }

    default String getUserDirName() { return User.getUserDirNameFromId(getUserId()); }

    default boolean mayReadIndex(Index index) { return !index.isUserIndex() || isOwnerOfIndex(index) || index.getShareWithUsers().contains(getUserId()); }
    default boolean mayWriteIndex(Index index) { return isOwnerOfIndex(index); }
    default boolean mayShareIndex(Index index) { return isOwnerOfIndex(index) || index.getShareWithUsers().contains(getUserId()); }
    default boolean mayDeleteIndex(Index index) { return isOwnerOfIndex(index); }
    default boolean isOwnerOfIndex(Index index) { return index.getUserId().equals(getUserId()); }

    default boolean mayManageFormatsFor(User user) { return mayManageFormatsFor(user.getUserId()); }
    default boolean mayManageFormatsFor(String userId) { return Objects.equals(userId, getUserId()); }
}
