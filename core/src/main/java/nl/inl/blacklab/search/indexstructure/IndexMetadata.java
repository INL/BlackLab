package nl.inl.blacklab.search.indexstructure;

import java.io.File;
import java.io.IOException;

import org.json.JSONException;
import org.json.JSONObject;

import nl.inl.blacklab.search.Searcher;
import nl.inl.util.Json;

/**
 * Reads/writes the indexmetadata.json file, and provides
 * an interface to the information contained within it.
 */
public class IndexMetadata {

	/** Our configuration data */
	JSONObject jsonRoot;

	/**
	 * Construct an index metadata object from a JSON file.
	 * @param metadataFile the file to read
	 * @throws IOException if the file was not found
	 * @throws JSONException if there's a syntax error in the file
	 */
	public IndexMetadata(File metadataFile) throws JSONException, IOException {
		jsonRoot = Json.read(metadataFile);
	}

	/**
	 * Construct a new index metadata object.
	 * @param indexName name of the index
	 */
	public IndexMetadata(String indexName) {
		jsonRoot = Json.object(
			"displayName", indexName,
			"description", "",
			"versionInfo", Json.object(
				"blackLabBuildTime", Searcher.getBlackLabBuildTime(),
				"blackLabVersion", Searcher.getBlackLabVersion(),
				"timeCreated", IndexStructure.getTimestamp(),
				"timeModified", IndexStructure.getTimestamp(),
				"indexFormat", IndexStructure.LATEST_INDEX_FORMAT,
				"alwaysAddClosingToken", true,
				"tagLengthInPayload", true),
			"fieldInfo", Json.object(
				"metadataFields", new JSONObject(),
				"complexFields", new JSONObject())
		);
	}

	/**
	 * Write the index metadata to a JSON file.
	 * @param metadataFile the file to write
	 */
	public void write(File metadataFile) {
		Json.write(jsonRoot, metadataFile);
	}

	/**
	 * Get the field info (metadata and complex fields).
	 * @return the configuration
	 */
	public JSONObject getFieldInfo() {
		return Json.getObject(jsonRoot, "fieldInfo");
	}

	/**
	 * Get the configuration for all metadata fields.
	 * @return the configuration
	 */
	public JSONObject getMetaFieldConfigs() {
		return Json.getObject(getFieldInfo(), "metadataFields");
	}

	/**
	 * Get the configuration for a metadata field.
	 * @param name field name
	 * @return the configuration
	 */
	public JSONObject getMetaFieldConfig(String name) {
		return Json.getObject(getMetaFieldConfigs(), name);
	}

	/**
	 * Get the configuration for all complex fields.
	 * @return the configuration
	 */
	public JSONObject getComplexFieldConfigs() {
		return Json.getObject(getFieldInfo(), "complexFields");
	}

	/**
	 * Get the configuration for a complex field.
	 * @param name field name
	 * @return the configuration
	 */
	public JSONObject getComplexFieldConfig(String name) {
		return Json.getObject(getComplexFieldConfigs(), name);
	}

	/**
	 * Get version information about the index.
	 *
	 * Includes indexFormat (3 or higher), indexTime (time of index creation,
	 * YYY-MM-DD hh:mm:ss), lastModified (optional) and
	 * blackLabBuildTime (optional; YYY-MM-DD hh:mm:ss).
	 *
	 * @return the configuration
	 */
	public JSONObject getVersionInfo() {
		return Json.getObject(jsonRoot, "versionInfo");
	}

	public String getDisplayName() {
		if (!jsonRoot.has("displayName"))
			return "";
		return jsonRoot.getString("displayName");
	}

	public String getDescription() {
		if (!jsonRoot.has("description"))
			return "";
		return jsonRoot.getString("description");
	}

	public boolean getContentViewable() {
		if (!jsonRoot.has("contentViewable"))
			return false;
		return jsonRoot.getBoolean("contentViewable");
	}

	public String getDocumentFormat() {
		if (!jsonRoot.has("documentFormat"))
			return "";
		return jsonRoot.getString("documentFormat");
	}

	public long getTokenCount() {
		if (!jsonRoot.has("tokenCount"))
			return 0;
		return jsonRoot.getLong("tokenCount");
	}

	/**
	 * Get the configuration for the indexer.
	 * @return the configuration
	 */
	public JSONObject getIndexerConfig() {
		return Json.getObject(jsonRoot, "indexerConfig");
	}

	/**
	 * Get the field naming scheme if specified.
	 * @return the field naming scheme, or null if not specified.
	 */
	public String getFieldNamingScheme() {
		JSONObject metaFieldInfo = getFieldInfo();
		if (metaFieldInfo.has("namingScheme")) {
			String namingScheme = metaFieldInfo.getString("namingScheme");
			if (!namingScheme.equals("DEFAULT") && !namingScheme.equals("NO_SPECIAL_CHARS")) {
				throw new RuntimeException("Unknown value for namingScheme: " + namingScheme);
			}
			return namingScheme;
		}
		return null;
	}

	/**
	 * Get the name of the default analyzer to use
	 * @return the analyzer name (DEFAULT if not specified)
	 */
	public String getDefaultAnalyzer() {
		JSONObject metaFieldInfo = getFieldInfo();
		if (metaFieldInfo.has("defaultAnalyzer")) {
			return metaFieldInfo.getString("defaultAnalyzer");
		}
		return "DEFAULT";
	}

	/**
	 * Is there field info in this index metadata file?
	 * @return true if there is, false if not
	 */
	public boolean hasFieldInfo() {
		boolean hasMetaFields = getMetaFieldConfigs().length() > 0;
		boolean hasComplexFields = getComplexFieldConfigs().length() > 0;
		return hasMetaFields || hasComplexFields;
	}

	public JSONObject getRoot() {
		return jsonRoot;
	}

	public String getDefaultUnknownCondition() {
		JSONObject fieldInfo = getFieldInfo();
		if (!fieldInfo.has("unknownCondition"))
			return "NEVER";
		return fieldInfo.getString("unknownCondition");
	}

	public String getDefaultUnknownValue() {
		JSONObject fieldInfo = getFieldInfo();
		if (!fieldInfo.has("unknownValue"))
			return "unknown";
		return fieldInfo.getString("unknownValue");
	}

}
