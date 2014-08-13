package nl.inl.blacklab.search;

import java.io.File;
import java.io.IOException;

import nl.inl.util.DateUtil;
import nl.inl.util.Json;
import nl.inl.util.json.JSONException;
import nl.inl.util.json.JSONObject;

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
		jsonRoot = new JSONObject();
		jsonRoot.put("displayName", indexName);
		jsonRoot.put("description", indexName);
		JSONObject versionInfo = new JSONObject();
		jsonRoot.put("versionInfo", versionInfo);
		//versionInfo.put("blackLabBuildDate", "2014-01-01 00:00:00"); // TODO: use actual build date
		versionInfo.put("indexFormat", "3");
		versionInfo.put("indexTime", DateUtil.getSqlDateTimeString());
		JSONObject fieldInfo = new JSONObject();
		jsonRoot.put("fieldInfo", fieldInfo);
		JSONObject metadataFields = new JSONObject();
		fieldInfo.put("metadataFields", metadataFields);
		JSONObject complexFields = new JSONObject();
		fieldInfo.put("complexFields", complexFields);
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
		return Json.obj(jsonRoot, "fieldInfo");
	}

	/**
	 * Get the configuration for all metadata fields.
	 * @return the configuration
	 */
	public JSONObject getMetaFieldConfigs() {
		return Json.obj(getFieldInfo(), "metadataFields");
	}

	/**
	 * Get the configuration for a metadata field.
	 * @param name field name
	 * @return the configuration
	 */
	public JSONObject getMetaFieldConfig(String name) {
		return Json.obj(getMetaFieldConfigs(), name);
	}

	/**
	 * Get the configuration for all complex fields.
	 * @return the configuration
	 */
	public JSONObject getComplexFieldConfigs() {
		return Json.obj(getFieldInfo(), "complexFields");
	}

	/**
	 * Get the configuration for a complex field.
	 * @param name field name
	 * @return the configuration
	 */
	public JSONObject getComplexFieldConfig(String name) {
		return Json.obj(getComplexFieldConfigs(), name);
	}

	/**
	 * Get version information about the index.
	 *
	 * Includes indexFormat (3 or higher), indexTime (time of index creation,
	 * YYY-MM-DD hh:mm:ss), lastModified (optional) and
	 * blackLabBuildDate (optional; YYY-MM-DD hh:mm:ss).
	 *
	 * @return the configuration
	 */
	public JSONObject getVersionInfo() {
		return Json.obj(jsonRoot, "versionInfo");
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

	/**
	 * Get the configuration for the indexer.
	 * @return the configuration
	 */
	public JSONObject getIndexerConfig() {
		return Json.obj(jsonRoot, "indexerConfig");
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
	 * Is there field info in this index metadata file?
	 * @return true if there is, false if not
	 */
	public boolean hasFieldInfo() {
		boolean hasMetaFields = getMetaFieldConfigs().length() > 0;
		boolean hasComplexFields = getComplexFieldConfigs().length() > 0;
		return hasMetaFields || hasComplexFields;
	}


}
