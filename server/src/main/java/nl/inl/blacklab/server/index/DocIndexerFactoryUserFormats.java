package nl.inl.blacklab.server.index;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.exceptions.InvalidInputFormatConfig;
import nl.inl.blacklab.index.DocIndexerFactoryConfig;
import nl.inl.blacklab.indexers.config.ConfigInputFormat;
import nl.inl.blacklab.indexers.config.InputFormatReader;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.InternalServerError;
import nl.inl.blacklab.server.exceptions.NotAuthorized;
import nl.inl.blacklab.server.exceptions.NotFound;
import nl.inl.blacklab.server.jobs.User;

/**
 * Implementation of DocIndexerFactoryConfig that is able to load user's config
 * directories and generally manages creating/deleting custom format configs.
 * <p>
 * Configs owned by users can be identified by their naming scheme of
 * userName:formatName Therefor, usernames and formatnames on their own may not
 * contain colons.
 * <p>
 * Formats are stored in the following file structure
 * 
 * <pre>
 * formatDirectory
 *     userDirectory1
 *         _input_formats
 *             format1.yaml
 *             format2.yaml
 *             ...
 *     userDirectory2
 *         _input_formats
 *             ...
 *     ...
 * </pre>
 * 
 * The names of user directories is determined using
 * {@link User#getUserDirName()} This allows user's formats to be stored next to
 * their indices in the user's own dedicated directory.
 * <p>
 * This class performs dynamic locating and loading of user's format configs
 * when {@link DocIndexerFactoryUserFormats#isSupported(String)} is called with
 * a formatIdentifier resembling a user-defined format.
 */
public class DocIndexerFactoryUserFormats extends DocIndexerFactoryConfig {
    public static class IllegalUserFormatIdentifier extends Exception {
        public IllegalUserFormatIdentifier(String message) {
            super(message);
        }
    }

    private static Logger logger = LogManager.getLogger(DocIndexerFactoryUserFormats.class);

    private static final String FORMATS_SUBDIR_NAME = "_input_formats";
    private static final Pattern formatNamePattern = Pattern.compile("[\\w_\\-]+");
    private static final Pattern fileNamePattern = Pattern
            .compile(formatNamePattern.pattern() + "(\\.blf)?\\.(ya?ml|json)");
    /**
     * Only matches user formatIdentifiers, not the builtin formatIdentifiers,
     * captures the userId as group 1 and formatName as group 2. Does not validate
     * the userId or formatName however.
     */
    private static final Pattern formatIdentifierPattern = Pattern.compile("^([^:]+):([^:]+)$");

    private File formatDir = null;

    private Set<String> loadedUsers = new HashSet<>();

    /**
     * @param formatDir directory under which to place user's files (see
     *            {@link DocIndexerFactoryUserFormats} for details).
     */
    public DocIndexerFactoryUserFormats(File formatDir) {
        if (formatDir == null || !Files.isDirectory(formatDir.toPath()) || !formatDir.isDirectory())
            throw new IllegalArgumentException("User format directory does not exist or unreadable.");

        this.formatDir = formatDir;
    }

    @Override
    public boolean isSupported(String formatIdentifier) {
        if (super.isSupported(formatIdentifier))
            return true;

        // It might be a user format, try to load it
        try {
            String userId = getUserIdOrFormatName(formatIdentifier, false);
            loadUserFormats(userId);
            return super.isSupported(formatIdentifier);
        } catch (IllegalUserFormatIdentifier e) {
            // not an identifier following the user format id spec
            return false;
        }
    }

    /**
     * Load all user formats already present in this user's directory, if this
     * user's formats haven't been loaded already.
     *
     * @param userId
     */
    public synchronized void loadUserFormats(String userId) {
        if (loadedUsers.contains(userId) || !User.isValidUserId(userId))
            return;

        loadedUsers.add(userId);
        File[] formats;
        try {
            formats = getUserFormatDir(formatDir, userId).listFiles();
        } catch (IOException e) {
            logger.warn("Could not load formats for user " + userId + ": " + e.getMessage());
            return;
        }

        for (File formatFile : formats) {
            try {
                String formatIdentifier = getFormatIdentifier(userId, formatFile.getName());
                unloaded.put(formatIdentifier, formatFile);
            } catch (IllegalUserFormatIdentifier e) {
                logger.warn("Skipping file " + formatFile + "in user format directory; " + e.getMessage());
            }
        }

        // Loading of user formats must be performed after initialization, or we won't have the base formats loaded yet
        if (isInitialized)
            loadUnloaded();
    }

    /**
     * Store a new format in the user's format directory.
     *
     * The name and content of the new format are validated before it is saved. If a
     * format with this name already exists for this user, the format is
     * overwritten.
     *
     * The new format will immediately be available after it has been saved.
     *
     * @param user user for which to store the format
     * @param fileName name of the format, including the .json/.yaml extension Note
     *            that this is different from the formatIdentifier under which this
     *            format will be made available, which is formed from a combination
     *            of the userId and formatName. You can also specify a formatIdentifier 
     *            (including a userId); this is useful if a superuser wants to create a 
     *            format for another user. 
     * @param is content of the file, assumed to be a text file with UTF_8 encoding.
     *            This stream is not closed by this function
     * @throws NotAuthorized if you try to create formats for another user (and are not the superuser)
     * @throws BadRequest if the name or format config is invalid
     * @throws InternalServerError if the file couldn't be written for some reason
     */
    public void createUserFormat(User user, String fileName, InputStream is) throws NotAuthorized, BadRequest, InternalServerError {
        try {
            String formatIdentifier = fileName.contains(":") ? fileName : getFormatIdentifier(user.getUserId(), fileName);
            
            String userIdFromFormatIdentifier = getUserIdOrFormatName(formatIdentifier, false);
            if (user.canManageFormatsFor(userIdFromFormatIdentifier))
                throw new NotAuthorized("You can only create formats for yourself.");

            // This is a little stupid, but we need to read the stream twice:
            // once to validate the file's contents, then again to store the file once the validation passes
            byte[] content = IOUtils.toByteArray(is);

            ConfigInputFormat config = new ConfigInputFormat(formatIdentifier);
            InputFormatReader.read(new InputStreamReader(new ByteArrayInputStream(content), StandardCharsets.UTF_8),
                    fileName.endsWith(".json"), config, this.finder);
            config.validate();

            File userFormatDir = getUserFormatDir(this.formatDir, userIdFromFormatIdentifier);
            File formatFile = new File(userFormatDir, fileName);

            FileUtils.writeByteArrayToFile(formatFile, content, false);
            config.setReadFromFile(formatFile);
            addFormat(config);
        } catch (IllegalUserFormatIdentifier e) {
            throw new BadRequest("ILLEGAL_INDEX_NAME", e.getMessage());
        } catch (InvalidInputFormatConfig e) {
            throw new BadRequest("CONFIG_ERROR", e.getMessage());
        } catch (IOException e) {
            throw new InternalServerError("Could not create user format: " + e.getMessage(), "INTERR_CREATING_USER_FORMAT");
        }
    }

    /**
     * Delete a user format, both on disk and from this factory. Will attempt to
     * locate unknown formats before deleting, allowing you to delete formats that
     * have not been loaded yet.
     * 
     * @param user
     * @param formatIdentifier a valid identifier encoding the user and format
     * @throws NotAuthorized if the format is a builtin format, or is not owned by
     *             the user
     * @throws NotFound if the format doesn't exist
     * @throws InternalServerError if the format can't be deleted properly for some
     *             reason
     * @throws BadRequest on invalid formatIdentifier
     */
    public void deleteUserFormat(User user, String formatIdentifier)
            throws NotAuthorized, NotFound, InternalServerError, BadRequest {
        try {
            if (isBuiltinFormat(formatIdentifier) || !user.canManageFormatsFor(getUserIdOrFormatName(formatIdentifier, false)))
                throw new NotAuthorized("Can only delete your own formats");
        } catch (IllegalUserFormatIdentifier e) {
            throw new BadRequest("ILLEGAL_INDEX_NAME", e.getMessage());
        }

        // Load the format if it's unknown
        if (!isSupported(formatIdentifier))
            throw new NotFound("FORMAT_NOT_FOUND", "Specified format was not found");

        ConfigInputFormat config = supported.get(formatIdentifier);
        File file = config.getReadFromFile();
        if (!file.delete())
            throw new InternalServerError("Could not delete format " + formatIdentifier, "INTERR_DELETING_FORMAT");
        supported.remove(formatIdentifier);
    }

    // Overridden to remove duplicate check, we just overwrite the format with the new one
    @Override
    protected void addFormat(ConfigInputFormat format) throws InvalidInputFormatConfig {
        format.validate();
        supported.put(format.getName(), format);
        unloaded.remove(format.getName());
    }

    /**
     * Create or get the user's input format configuration dir, verifying that it's
     * readable and an actual directory.
     *
     * @param formatDir the top-level directory in which the user directories are
     *            located
     * @param userId the user
     * @return user's input format config dir
     * @throws IOException when the dir couldn't be created or is unreadable for
     *             whatever reason
     */
    private static File getUserFormatDir(File formatDir, String userId) throws IOException {
        // step down 2 levels, global dir > user dir > userformat dir
        File userDir = new File(new File(formatDir, User.getUserDirNameFromId(userId)), FORMATS_SUBDIR_NAME);

        Files.createDirectories(userDir.toPath()); // does nothing if dir already exists, throws if dir is actually file or can't create
        if (!Files.isReadable(userDir.toPath()))
            throw new IOException("Could not read user format directory " + userDir);

        return userDir;
    }

    /**
     * Concat the user's id, and the name of a format to generate a unique
     * formatIdentifier. This function validates the username and format name to
     * ensure the resulting identifier can be properly separated again.
     *
     * @param userId the user
     * @param fileName name of the file from which this config/format is created,
     *            including .json/.yaml extension. the name of this file, minus the
     *            extension, will be used to create the final identifier.
     * @return the id created by joining the two values
     * @throws IllegalUserFormatIdentifier when userId or fileName contain illegal
     *             characters, or fileName does not end in a supported extension
     */
    private static String getFormatIdentifier(String userId, String fileName) throws IllegalUserFormatIdentifier {
        if (!fileNamePattern.matcher(fileName).matches())
            throw new IllegalUserFormatIdentifier(
                    "Format file name may only contain letters, digits, underscore and dash, and must end with .yaml or .json (or .blf.yaml/.blf.json)");

        if (!User.isValidUserId(userId) || userId.contains(":"))
            throw new IllegalUserFormatIdentifier("Illegal username " + userId);

        return userId + ":" + ConfigInputFormat.stripExtensions(fileName);
    }

    /**
     * Extract the userId portion of a formatIdentifier as created by
     * {@link DocIndexerFactoryUserFormats#getFormatIdentifier(String, String)}
     *
     * @param formatIdentifier
     * @param getFormatName return the formatName instead of the userId
     * @return the userId or formatName
     * @throws IllegalUserFormatIdentifier when the formatIdentifier does not follow
     *             the pattern of userId:formatName or the userId or formatName
     *             contain illegal characters
     */
    public static String getUserIdOrFormatName(String formatIdentifier, boolean getFormatName)
            throws IllegalUserFormatIdentifier {
        Matcher m = formatIdentifierPattern.matcher(formatIdentifier);
        if (!m.matches())
            throw new IllegalUserFormatIdentifier(formatIdentifier + " is not a valid user format id");

        String userId = User.sanitize(m.group(1));
        String formatName = m.group(2);
        if (!User.isValidUserId(userId))
            throw new IllegalUserFormatIdentifier(userId + " is not a valid username");
        if (!formatNamePattern.matcher(formatName).matches())
            throw new IllegalUserFormatIdentifier(
                    "Format configuration name may only contain letters, digits, underscore and dash");

        return getFormatName ? formatName : userId;
    }

    private boolean isBuiltinFormat(String formatIdentifier) {
        try {
            // if this succeeds, the identifier follows the user format idenfitier spec
            getUserIdOrFormatName(formatIdentifier, false);
            // so it isn't built in by definition
            return false;
        } catch (IllegalUserFormatIdentifier e) {
            // it's not a valid userFormat evidently, so if we support it, then it must be builtin
            return isSupported(formatIdentifier);
        }
    }
}
