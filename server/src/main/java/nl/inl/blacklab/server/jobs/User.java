package nl.inl.blacklab.server.jobs;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Represents either a unique (logged-in) user, or a unique session
 *  (when not logged in). */
public class User {
	/** The user id if logged in; null otherwise */
	private String userId;

	/** The session id */
	private String sessionId;

	/**
	 * Create a new logged-in user.
	 *
	 * @param userId unique id identifying this user
	 * @param sessionId the session id
	 * @return the new user
	 */
	public static User loggedIn(String userId, String sessionId) {
		return new User(userId, sessionId);
	}

	/**
	 * Create a new anonymous user.
	 *
	 * @param sessionId the session id
	 * @return the new user
	 */
	public static User anonymous(String sessionId) {
		return new User(null, sessionId);
	}

	private User(String userId, String sessionId) {
		this.userId = null;
		if (userId != null) {
			// Replace any non-URL-safe characters from userid with _.
			// Also leave out colon because we use colon as a separator
			// between userid and index name.
			userId = userId.replaceAll("[^a-zA-Z0-9\\-\\._!\\$&'\\(\\)\\*\\+,;=@]", "_");
			if (userId.length() == 0)
				userId = null;
			this.userId = userId;
		}
		this.sessionId = sessionId;
	}

	@Override
	public String toString() {
		return userId != null ? userId : "SESSION:" + sessionId;
	}

	public String uniqueId() {
		return userId != null ? userId : "S:" + sessionId;
	}

	public String uniqueIdShort() {
		String str = uniqueId();
		return str.length() > 6 ? str.substring(0, 6) : str;
	}

	public boolean isLoggedIn() {
		return userId != null;
	}

	public String getUserId() {
		return userId;
	}

	public String getUserDirName() {
		return getUserDirNameFromId(userId);
	}

	public String getSessionId() {
		return sessionId;
	}

	public static String getUserDirNameFromId(String id) {
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
			String zeroes = "";
			for (int i = 0; i < 32 - hashHex.length(); i++){
				zeroes += "0";
			}
			return stripped + "_" + zeroes + hashHex;
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
		//return FileUtil.sanitizeFilename(userId);
	}


}
