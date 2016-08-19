package nl.inl.blacklab.server.search;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

import nl.inl.blacklab.search.Searcher;
import nl.inl.blacklab.server.datastream.DataFormat;
import nl.inl.blacklab.server.util.JsonUtil;
import nl.inl.blacklab.server.util.ServletUtil;

public class BlsConfig {

	/** Maximum context size allowed */
	private int maxContextSize;

	/** Maximum snippet size allowed */
	private int maxSnippetSize;

	private int defaultMaxHitsToRetrieve;

	private int defaultMaxHitsToCount;

	/** Default number of hits/results per page [20] */
	private int defaultPageSize;

	/** Maximum value allowed for number parameter */
	private int maxPageSize;

	/** Default case-sensitivity to use. [insensitive] */
	private boolean defaultCaseSensitive;

	/** Default diacritics-sensitivity to use. [insensitive] */
	private boolean defaultDiacriticsSensitive;

	/** Default number of words around hit. [5] */
	private int defaultContextSize;

	/** IP addresses for which debug mode will be turned on. */
	private Set<String> debugModeIps = new HashSet<>();

	/** The default output type, JSON or XML. */
	private DataFormat defaultOutputType;

	/**
	 * Which IPs are allowed to override the userId using a parameter.
	 */
	private Set<String> overrideUserIdIps;

	/** Maximum allowed value for maxretrieve parameter (-1 = no limit). */
	private int maxHitsToRetrieveAllowed;

	/** Maximum allowed value for maxcount parameter (-1 = no limit). */
	private int maxHitsToCountAllowed;

	/**
	 * Are we allowed to query the list of all document?
	 * (might be slow for large corpora, seems ok though)
	 * TODO: make configurable
	 */
	private boolean allDocsQueryAllowed = true;

	private BlsConfigCacheAndPerformance cacheConfig;

	private String authClass;

	Map<String, Object> authParam;

	public BlsConfig(JSONObject properties) {
		getDebugProperties(properties);
		getRequestsProperties(properties);
		getPerformanceProperties(properties);
		getAuthProperties(properties);
	}

	private void getDebugProperties(JSONObject properties) {
		if (properties.has("debugModeIps")) {
			JSONArray jsonDebugModeIps = properties
					.getJSONArray("debugModeIps");
			for (int i = 0; i < jsonDebugModeIps.length(); i++) {
				debugModeIps.add(jsonDebugModeIps.getString(i));
			}
		}
	}

	private void getPerformanceProperties(JSONObject properties) {
		JSONObject perfProp = null;
		if (properties.has("performance"))
			perfProp = properties.getJSONObject("performance");
		this.cacheConfig = new BlsConfigCacheAndPerformance(perfProp);
	}

	private void getRequestsProperties(JSONObject properties) {
		if (properties.has("requests")) {
			JSONObject reqProp = properties.getJSONObject("requests");
			 // XML if nothing specified (because of browser's default Accept header)
			defaultOutputType = DataFormat.XML;
			if (reqProp.has("defaultOutputType"))
				defaultOutputType = ServletUtil.getOutputTypeFromString(
						reqProp.getString("defaultOutputType"), DataFormat.XML);
			defaultPageSize = JsonUtil.getIntProp(reqProp, "defaultPageSize", 20);
			maxPageSize = JsonUtil.getIntProp(reqProp, "maxPageSize", 1000);
			String defaultSearchSensitivity = JsonUtil.getProperty(reqProp,
					"defaultSearchSensitivity", "insensitive");
			switch(defaultSearchSensitivity) {
			case "sensitive":
				defaultCaseSensitive = defaultDiacriticsSensitive = true;
				break;
			case "case":
				defaultCaseSensitive = true;
				defaultDiacriticsSensitive = false;
				break;
			case "diacritics":
				defaultDiacriticsSensitive = true;
				defaultCaseSensitive = false;
				break;
			default:
				defaultCaseSensitive = defaultDiacriticsSensitive = false;
				break;
			}
			defaultContextSize = JsonUtil.getIntProp(reqProp,
					"defaultContextSize", 5);
			maxContextSize = JsonUtil.getIntProp(reqProp, "maxContextSize", 20);
			maxSnippetSize = JsonUtil
					.getIntProp(reqProp, "maxSnippetSize", 100);
			defaultMaxHitsToRetrieve = JsonUtil.getIntProp(reqProp, "defaultMaxHitsToRetrieve", Searcher.DEFAULT_MAX_RETRIEVE);
			defaultMaxHitsToCount = JsonUtil.getIntProp(reqProp, "defaultMaxHitsToCount", Searcher.DEFAULT_MAX_COUNT);
			maxHitsToRetrieveAllowed = JsonUtil.getIntProp(reqProp,
					"maxHitsToRetrieveAllowed", 10000000);
			maxHitsToCountAllowed = JsonUtil.getIntProp(reqProp,
					"maxHitsToCountAllowed", -1);
			JSONArray jsonOverrideUserIdIps = reqProp
					.getJSONArray("overrideUserIdIps");
			overrideUserIdIps = new HashSet<>();
			for (int i = 0; i < jsonOverrideUserIdIps.length(); i++) {
				overrideUserIdIps.add(jsonOverrideUserIdIps.getString(i));
			}
		} else {
			defaultOutputType = DataFormat.XML;
			defaultPageSize = 20;
			maxPageSize = 1000;
			defaultCaseSensitive = defaultDiacriticsSensitive = false;
			defaultContextSize = 5;
			maxContextSize = 20;
			maxSnippetSize = 100;
			defaultMaxHitsToRetrieve = Searcher.DEFAULT_MAX_RETRIEVE;
			defaultMaxHitsToCount = Searcher.DEFAULT_MAX_COUNT;
			maxHitsToRetrieveAllowed = 10000000;
			maxHitsToCountAllowed = -1;
			overrideUserIdIps = new HashSet<>();
		}
	}

	private void getAuthProperties(JSONObject properties) {
		JSONObject authProp = null;
		if (properties.has("authSystem"))
			authProp = properties.getJSONObject("authSystem");
		authClass = "";
		if (authProp != null) {
			authParam = JsonUtil.mapFromJsonObject(authProp);
			if (authParam.containsKey("class")) {
				authClass = authParam.get("class").toString();
			}
		} else {
			authParam = new HashMap<>();
		}
	}

	public BlsConfigCacheAndPerformance getCacheConfig() {
		return cacheConfig;
	}

	public int maxContextSize() {
		return maxContextSize;
	}

	public int maxSnippetSize() {
		return maxSnippetSize;
	}

	public int getDefaultMaxHitsToRetrieve() {
		return defaultMaxHitsToRetrieve;
	}

	public int getDefaultMaxHitsToCount() {
		return defaultMaxHitsToCount;
	}

	public int defaultPageSize() {
		return defaultPageSize;
	}

	public int maxPageSize() {
		return maxPageSize;
	}

	public boolean isDefaultCaseSensitive() {
		return defaultCaseSensitive;
	}

	public boolean isDefaultDiacriticsSensitive() {
		return defaultDiacriticsSensitive;
	}

	public int getDefaultContextSize() {
		return defaultContextSize;
	}

	public Set<String> getDebugModeIps() {
		return debugModeIps;
	}

	public DataFormat defaultOutputType() {
		return defaultOutputType;
	}

	public Set<String> getOverrideUserIdIps() {
		return overrideUserIdIps;
	}

	public int clientCacheTimeSec() {
		return cacheConfig.getClientCacheTimeSec();
	}

	public int maxHitsToRetrieveAllowed() {
		return maxHitsToRetrieveAllowed;
	}

	public int maxHitsToCountAllowed() {
		return maxHitsToCountAllowed;
	}

	public boolean isDebugMode(String ip) {
		return getDebugModeIps().contains(ip);
	}

	public boolean overrideUserId(String ip) {
		return getOverrideUserIdIps().contains(ip);
	}

	public boolean isAllDocsQueryAllowed() {
		return allDocsQueryAllowed;
	}

	public String getAuthClass() {
		return authClass;
	}

	public Map<String, Object> getAuthParam() {
		return authParam;
	}

}