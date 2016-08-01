package nl.inl.blacklab.server.search;

import org.apache.log4j.Logger;

import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.ConfigurationException;
import nl.inl.blacklab.server.jobs.Job;
import nl.inl.blacklab.server.jobs.JobDescription;
import nl.inl.blacklab.server.jobs.User;
import nl.inl.blacklab.server.requesthandlers.SearchParameters;
import nl.inl.util.json.JSONException;
import nl.inl.util.json.JSONObject;

public class SearchManager {

	private static final Logger logger = Logger.getLogger(SearchManager.class);

	/** Our config */
	private BlsConfig config;

	/** All running searches as well as recently run searches */
	private SearchCache cache;

	/** System for determining the current user. */
	private AuthManager authSystem;

	/** Manages all the indices we have available and/or open */
	private IndexManager indexMan;

	public SearchManager(JSONObject properties) throws ConfigurationException {
		logger.debug("SearchManager created");

		try {
			// The main config object
			config = new BlsConfig(properties);

			// Create the cache
			// Use the performance properties [optional, defaults will be used if missing]
			JSONObject perfProp = null;
			if (properties.has("performance"))
				perfProp = properties.getJSONObject("performance");
			cache = new SearchCache(this, perfProp);

			// Find the indices
			indexMan = new IndexManager(this, properties);

			// Init auth system
			JSONObject authProp = null;
			if (properties.has("authSystem"))
				authProp = properties.getJSONObject("authSystem");
			authSystem = new AuthManager(authProp);
		} catch (JSONException e) {
			e.printStackTrace();
			throw new ConfigurationException("Invalid JSON in blacklab-server.json; please validate: " + e.getMessage());
		}

		// Set up the parameter default values
		SearchParameters.setDefault("number", "" + config.defaultPageSize());
		SearchParameters.setDefault("wordsaroundhit", "" + config.getDefaultContextSize());
		SearchParameters.setDefault("maxretrieve", "" + config.getDefaultMaxHitsToRetrieve());
		SearchParameters.setDefault("maxcount", "" + config.getDefaultMaxHitsToCount());
		SearchParameters.setDefault("sensitive", config.isDefaultCaseSensitive() && config.isDefaultDiacriticsSensitive() ? "yes" : "no");
	}

	/**
	 * Clean up resources.
	 *
	 * In particular, stops the load manager thread and
	 * cancels any running searches.
	 */
	public synchronized void cleanup() {
		// Stop any running searches
		cache.cleanup();

		// Set variables to null in case it helps GC
		cache = null;
		config = null;
		authSystem = null;
		indexMan = null;
	}

	public SearchCache getCache() {
		return cache;
	}

	public BlsConfig config() {
		return config;
	}

	public AuthManager getAuthSystem() {
		return authSystem;
	}

	public IndexManager getIndexManager() {
		return indexMan;
	}

	public Job search(User user, JobDescription jobDesc, boolean block) throws BlsException {
		return cache.search(user, jobDesc, block);
	}

}
