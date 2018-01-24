package nl.inl.blacklab.server.index;

import java.io.File;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;

import nl.inl.blacklab.index.DocIndexerFactoryConfig;
import nl.inl.blacklab.index.config.ConfigInputFormat;
import nl.inl.blacklab.index.config.InputFormatConfigException;
import nl.inl.blacklab.server.jobs.User;

/**
 * Implementation of DocIndexerFactoryConfig that is able to load user's config directories and have formats added/overwritten and removed after initialization.
 */
// TODO implement dynamic loading of user formats by detecting requests for user formats (the colon in a format's id) and loading the user's directory
// still allow the loading of the entire directory later when the user wants to see their configurations
public class DocIndexerFactoryUserFormats extends DocIndexerFactoryConfig {

	private Set<String> loadedUsers = new HashSet<>();

	// format is <username>:<formatname>
	/* These will be useful when we add dynamic loading of user formats (i.e. when they're needed, instead of when the user first performs an action)
	private static boolean isUserFormat(String formatId) {
		if (formatId == null)
			return false;

		int index = formatId.indexOf(':');

		if (index != -1 && index != formatId.lastIndexOf(':') || index == 0 || index == formatId.length() -1)
			return false;

		return index != -1;
	}

	private static String getUserName(String formatId) {
		return isUserFormat(formatId) ? formatId.substring(0, formatId.indexOf(':')) : null;
	}
	*/

	public synchronized DocIndexerFactoryUserFormats ensureUserFormatsRegistered(User user, File dir) {
		if (loadedUsers.contains(user.getUserId()))
			return this;
		loadedUsers.add(user.getUserId());

        for (File formatFile: dir.listFiles()) {
        	// IMPORTANT: substitute the name of this config by the properly formatted format name in the form of <userId>:<formatId>
            String formatName = ConfigInputFormat.stripExtensions(formatFile.getName());
            formatName = IndexManager.userFormatName(user.getUserId(), formatName);
            unloaded.put(formatName, formatFile);
        }

        // Loading of user formats must be performed after initialization, or we won't have the base formats loaded yet
        if (!isInitialized)
        	return this;

        // Load new configs,
		// use !isEmpty so we can remove entries during iteration (using the finder above) without messing up iterators
		while (!unloaded.isEmpty()) {
			Entry<String, File> e = unloaded.entrySet().iterator().next();
			unloaded.remove(e.getKey());

			load(e.getKey(), e.getValue());
		}

		return this;
	}

	/**
	 * Register a single new format for this user, loading it from a file.
	 * This function does not place any restrictions on the actual location of the file.
	 *
	 * This function is in charge of loading the user's format, because the format might refer a "baseFormat",
	 * which should be provided at load-time by this factory.
	 *
	 * Registering an already registered format will reload that format, allowing modifications to the format.
	 *
	 * @param user user's id is prefixed to the format file's name to create a unique id
	 * @param formatFile the file containing the format, the file's name, minus the .blf.* extension is used as the format's name.
	 * @return this
	 */
	public DocIndexerFactoryUserFormats registerFormat(User user, File formatFile) {
		String formatName = ConfigInputFormat.stripExtensions(formatFile.getName());
		String formatIdentifier = IndexManager.userFormatName(user.getUserId(), formatName);

		if (isSupported(formatIdentifier))
			throw new InputFormatConfigException("Duplicate user format " + formatName + " for user " + user.getUserId());

		try {
			load(formatIdentifier, formatFile);
			return this;
		} catch (InputFormatConfigException e) {
			throw new InputFormatConfigException("Error while reading config file " + formatFile, e);
		}
	}

	// TODO what happens when this is called while an index is currently using the removed format?
	// perhaps we do need to memoize the factory...hum
	public DocIndexerFactoryUserFormats unregisterFormat(String formatIdentifier) {
		this.supported.remove(formatIdentifier);
		return this;
	}

	// Overridden to remove duplicate check, we just overwrite the format with the new one
	// Reason is that RequestHandlerAddFormat should exhibit overwrite behavior when posting to the same format
	@Override
	protected void addFormat(ConfigInputFormat format) throws InputFormatConfigException {
		format.validate();
		supported.put(format.getName(), format);
		unloaded.remove(format.getName());
	}

	// TODO isSupported(<someUnloadedUser>:<someValidFormat>) will return false, as user formats are not loaded until their respective user has been loaded
	// so referring to another user's format won't currently work (this was already the case before the DocIndexerFactory refactor)
	// we'll need to intercept isSupported calls to check if they're a valid user, and if so, load that user's formats.
}