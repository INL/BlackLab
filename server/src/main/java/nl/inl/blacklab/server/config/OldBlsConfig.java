package nl.inl.blacklab.server.config;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.databind.JsonNode;

import nl.inl.blacklab.config.BLConfigLog;
import nl.inl.blacklab.config.BLConfigTrace;
import nl.inl.blacklab.config.OldConfigReader;
import nl.inl.blacklab.exceptions.InvalidConfiguration;
import nl.inl.blacklab.indexers.config.YamlJsonReader;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndexImpl;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.results.Results;
import nl.inl.blacklab.search.results.SearchSettings;
import nl.inl.blacklab.server.datastream.DataFormat;
import nl.inl.blacklab.server.requesthandlers.ElementNames;
import nl.inl.blacklab.server.util.JsonUtil;
import nl.inl.blacklab.server.util.ServletUtil;

public class OldBlsConfig extends YamlJsonReader {

    protected static final Logger logger = LogManager.getLogger(BlackLabIndexImpl.class);

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

    /** Default number of words around hit. [5] */
    private int defaultContextSize;

    /**
     * Value of Access-Control-Allow-Origin header to send (or no header if empty or
     * null)
     */
    private String allowOrigin = "*";

    /** IP addresses for which debug mode will be turned on. */
    private Set<String> debugModeIps = new HashSet<>();

    /** The default output type, JSON or XML. */
    private DataFormat defaultOutputType;

    /** Omit empty properties in concordances (only in XML for now)? */
    private boolean omitEmptyProperties = false;

    /**
     * Which IPs are allowed to override the userId using a parameter.
     */
    private Set<String> overrideUserIdIps = new HashSet<>();

    /** Maximum allowed value for maxretrieve parameter (-1 = no limit). */
    private int maxHitsToRetrieveAllowed;

    /** Maximum allowed value for maxcount parameter (-1 = no limit). */
    private int maxHitsToCountAllowed;

    /**
     * Are we allowed to query the list of all document? (might be slow for large
     * corpora, seems ok though) TODO: make configurable
     */
    private boolean allDocsQueryAllowed = true;

    private OldBlsConfigCacheAndPerformance cacheConfig;

    private String authClass;

    Map<String, Object> authParam;

    private MatchSensitivity defaultMatchSensitivity;

    private File logDatabase = null;

    /** Log detailed debug messages about search cache management? */
    public static boolean traceCache = false;

    private JsonNode properties;

    public OldBlsConfig(JsonNode properties) {
        this.properties = properties;
        getDebugProperties(properties);
        getRequestsProperties(properties);
        getPerformanceProperties(properties);
        getAuthProperties(properties);
        if (properties.has("indexing"))
            OldConfigReader.readIndexing(obj(properties.get("indexing"), "indexing"));
    }

    private void getDebugProperties(JsonNode properties) {

        // Old location of debugModeIps: top-level
        // DEPRECATED
        if (properties.has("debugModeIps")) {
            logger.warn("DEPRECATED setting debugModeIps found at top-level. Use debug.addresses instead.");
            JsonNode jsonDebugModeIps = properties.get("debugModeIps");
            for (int i = 0; i < jsonDebugModeIps.size(); i++) {
                String addr = jsonDebugModeIps.get(i).textValue();
                debugModeIps.add(addr);
                logger.debug("[OLD-STYLE] Debug address found: " + addr);
            }
        }

        // Debugging settings
        if (properties.has("debug")) {
            JsonNode debugProp = properties.get("debug");

            // New location of debugIps: inside debug block
            if (debugProp.has("addresses")) {
                JsonNode jsonDebugModeIps = debugProp.get("addresses");
                for (int i = 0; i < jsonDebugModeIps.size(); i++) {
                    String addr = jsonDebugModeIps.get(i).textValue();
                    debugModeIps.add(addr);
                    logger.debug("Debug address found: " + addr);
                }
            }
            
            if (debugProp.has("sqliteLogDatabase")) {
                logDatabase = new File(debugProp.get("sqliteLogDatabase").textValue());
            }

            if (debugProp.has("trace")) {
                JsonNode traceProp = debugProp.get("trace");
                BlackLabIndexImpl.setTraceIndexOpening(JsonUtil.getBooleanProp(traceProp, "indexOpening", false));
                BlackLabIndexImpl.setTraceOptimization(JsonUtil.getBooleanProp(traceProp, "optimization", false));
                BlackLabIndexImpl.setTraceQueryExecution(JsonUtil.getBooleanProp(traceProp, "queryExecution", false));
                traceCache = JsonUtil.getBooleanProp(traceProp, "cache", false);
            }
        }
    }

    private void getPerformanceProperties(JsonNode properties) {
        JsonNode perfProp = null;
        if (properties.has("performance"))
            perfProp = properties.get("performance");
        this.cacheConfig = new OldBlsConfigCacheAndPerformance(perfProp);
    }

    private void getRequestsProperties(JsonNode properties) {
        if (properties.has("requests")) {
            JsonNode reqProp = properties.get("requests");
            // XML if nothing specified (because of browser's default Accept header)
            allowOrigin = JsonUtil.getProperty(reqProp, "accessControlAllowOrigin", "*");
            defaultOutputType = DataFormat.XML;
            if (reqProp.has("defaultOutputType"))
                defaultOutputType = ServletUtil.getOutputTypeFromString(
                        reqProp.get("defaultOutputType").textValue(), DataFormat.XML);
            if (reqProp.has("omitEmptyProperties"))
                omitEmptyProperties = reqProp.get("omitEmptyProperties").booleanValue();
            if (reqProp.has("useOldElementNames")) {
                // Use the old names for elements (complexField, property, etc. instead of annotatedField, annotation)?
                boolean useOldElementNames = reqProp.get("useOldElementNames").booleanValue();
                ElementNames.setUseOldElementNames(useOldElementNames);
            }
            defaultPageSize = JsonUtil.getIntProp(reqProp, "defaultPageSize", 20);
            maxPageSize = JsonUtil.getIntProp(reqProp, "maxPageSize", 100_000);
            String defaultSearchSensitivity = JsonUtil.getProperty(reqProp,
                    "defaultSearchSensitivity", "insensitive");
            switch (defaultSearchSensitivity) {
            case "sensitive":
                defaultMatchSensitivity = MatchSensitivity.SENSITIVE;
                break;
            case "case":
                defaultMatchSensitivity = MatchSensitivity.DIACRITICS_INSENSITIVE;
                break;
            case "diacritics":
                defaultMatchSensitivity = MatchSensitivity.CASE_INSENSITIVE;
                break;
            default:
                defaultMatchSensitivity = MatchSensitivity.INSENSITIVE;
                break;
            }
            defaultContextSize = JsonUtil.getIntProp(reqProp, "defaultContextSize", 5);
            maxContextSize = JsonUtil.getIntProp(reqProp, "maxContextSize", 20);
            maxSnippetSize = JsonUtil
                    .getIntProp(reqProp, "maxSnippetSize", 120);
            defaultMaxHitsToRetrieve = JsonUtil.getIntProp(reqProp, "defaultMaxHitsToRetrieve",
                    SearchSettings.DEFAULT_MAX_PROCESS);
            defaultMaxHitsToCount = JsonUtil.getIntProp(reqProp, "defaultMaxHitsToCount", SearchSettings.DEFAULT_MAX_COUNT);
            maxHitsToRetrieveAllowed = JsonUtil.getIntProp(reqProp,
                    "maxHitsToRetrieveAllowed", 10_000_000);
            maxHitsToCountAllowed = JsonUtil.getIntProp(reqProp, "maxHitsToCountAllowed", Results.NO_LIMIT);
            if (reqProp.has("overrideUserIdIps")) {
                JsonNode jsonOverrideUserIdIps = reqProp.get("overrideUserIdIps");
                overrideUserIdIps = new HashSet<>();
                for (int i = 0; i < jsonOverrideUserIdIps.size(); i++) {
                    overrideUserIdIps.add(jsonOverrideUserIdIps.get(i).textValue());
                }
            }
        } else {
            defaultOutputType = DataFormat.XML;
            defaultPageSize = 20;
            maxPageSize = 100_000;
            defaultMatchSensitivity = MatchSensitivity.INSENSITIVE;
            defaultContextSize = 5;
            maxContextSize = 20;
            maxSnippetSize = 120;
            defaultMaxHitsToRetrieve = SearchSettings.DEFAULT_MAX_PROCESS;
            defaultMaxHitsToCount = SearchSettings.DEFAULT_MAX_COUNT;
            maxHitsToRetrieveAllowed = 10_000_000;
            maxHitsToCountAllowed = -1;
            overrideUserIdIps = new HashSet<>();
        }
    }

    private void getAuthProperties(JsonNode properties) {
        JsonNode authProp = null;
        if (properties.has("authSystem"))
            authProp = properties.get("authSystem");
        authClass = "";
        if (authProp != null) {
            authParam = JsonUtil.mapFromJsonObject(authProp);
            if (authParam.containsKey("class")) {
                authClass = authParam.get("class").toString();
                authParam.remove("class");
            }
        } else {
            authParam = new HashMap<>();
        }
    }

    public OldBlsConfigCacheAndPerformance getCacheConfig() {
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

    public MatchSensitivity defaultMatchSensitivity() {
        return defaultMatchSensitivity;
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

    public String getAccessControlAllowOrigin() {
        return allowOrigin.length() == 0 ? null : allowOrigin;
    }

    public boolean isOmitEmptyProperties() {
        return omitEmptyProperties;
    }

    public File logDatabase() {
        return logDatabase;
    }
    
    public BLSConfig getNewConfig() {
        try {
            BLSConfig config = new BLSConfig();
            config.setAuthentication(getBlsConfigAuth());
            config.setCache(getCacheConfig().getBlsConfigCache());
            config.setPerformance(getCacheConfig().getBlsConfigPerformance());
            config.setDebug(getBlsConfigDebug());
            config.setIndexLocations(getBlsConfigIndexes());
            config.setUserIndexes(getBlsUserIndexDir());
            config.setLog(getBLConfigLog());
            config.setParameters(getBlsConfigParameters());
            config.setProtocol(getBlsConfigProtocol());
            
            // Load blacklab.yaml (new config doesn't do this anymore, but gets these settings from blacklab-server.yaml as well)
            config.setBLConfig(BlackLab.config());
            
            return config;
        } catch (IOException e) {
            throw new InvalidConfiguration("Error converting old configuration format to new", e);
        }
    }

    private BLSConfigProtocol getBlsConfigProtocol() {
        BLSConfigProtocol config = new BLSConfigProtocol();
        config.setUseOldElementNames(ElementNames.isUseOldElementNames());
        config.setAccessControlAllowOrigin(allowOrigin);
        config.setDefaultOutputType(defaultOutputType.toString());
        config.setOmitEmptyProperties(omitEmptyProperties);
        return config;
    }

    private BLSConfigParameters getBlsConfigParameters() {
        BLSConfigParameters config = new BLSConfigParameters();
        config.setContextSize(DefaultMax.get(defaultContextSize, Math.max(maxContextSize, maxSnippetSize)));
        config.setProcessHits(DefaultMax.get(defaultMaxHitsToRetrieve, maxHitsToRetrieveAllowed));
        config.setCountHits(DefaultMax.get(defaultMaxHitsToCount, maxHitsToCountAllowed));
        config.setPageSize(DefaultMax.get(defaultPageSize, maxPageSize));
        config.setFilterLanguage("luceneql");
        config.setPatternLanguage("corpusql");
        config.setDefaultSearchSensitivity(defaultMatchSensitivity);
        return config;
    }

    private BLConfigLog getBLConfigLog() throws IOException {
        BLConfigLog config = new BLConfigLog();
        config.setSqliteDatabase(logDatabase.getCanonicalPath());
        BLConfigTrace trace = new BLConfigTrace();
        trace.setQueryExecution(BlackLabIndexImpl.traceQueryExecution());
        trace.setIndexOpening(BlackLabIndexImpl.traceIndexOpening());
        trace.setOptimization(BlackLabIndexImpl.traceOptimization());
        trace.setCache(traceCache);
        config.setTrace(trace);
        return config;
    }

    private String getBlsUserIndexDir() throws IOException {
        // User collections dir, these are like collections, but within a user's directory
        File userCollectionsDir = JsonUtil.getFileProp(properties, "userCollectionsDir", null);
        return userCollectionsDir.getCanonicalPath();
    }

    private List<String> getBlsConfigIndexes() throws IOException {
        List<String> config = new ArrayList<>();
        
        if (properties.has("indices")) {
            JsonNode indicesMap = properties.get("indices");
            Iterator<Entry<String, JsonNode>> it = indicesMap.fields();
            while (it.hasNext()) {
                Entry<String, JsonNode> entry = it.next();
                JsonNode indexConfig = entry.getValue();
                File dir = JsonUtil.getFileProp(indexConfig, "dir", null);
                config.add(dir.getCanonicalPath());
            }
        }

        // Collections, these are lazily loaded, and additions/removals within them should be detected.
        if (properties.has("indexCollections")) {
            for (JsonNode collectionNode : properties.get("indexCollections")) {
                File collectionDir = new File(collectionNode.textValue());
                config.add(collectionDir.getCanonicalPath());
            }
        }
        return config;
    }

    private BLSConfigDebug getBlsConfigDebug() {
        BLSConfigDebug config = new BLSConfigDebug();
        config.setAddresses(new ArrayList<>(this.debugModeIps));
        return config;
    }

    private BLSConfigAuth getBlsConfigAuth() {
        BLSConfigAuth config = new BLSConfigAuth();
        config.setOverrideUserIdIps(new ArrayList<>(overrideUserIdIps));
        Map<String, String> system = new HashMap<>();
        for (Entry<String, Object> entry: authParam.entrySet()) {
            system.put(entry.getKey(), entry.getValue().toString());
        }
        system.put("class", authClass);
        config.setSystem(system);
        return config;
    }

}
