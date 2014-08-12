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
	 * Get or create a JSONObject child of the specified parent.
	 *
	 * @param parent parent node to get the object from
	 * @param name name of the JSONObject to get
	 * @return the object
	 * @throws RuntimeException if a non-JSONObject child with this name exists
	 */
	protected JSONObject getJSONObject(JSONObject parent, String name) {
		Object object = parent.get(name);
		if (object != null) {
			if (!(object instanceof JSONObject))
				throw new RuntimeException("Not a JSONObject: " + name);
		} else {
			object = new JSONObject();
			parent.put(name, object);
		}
		return (JSONObject) object;
	}

	/**
	 * Write the index metadata to a JSON file.
	 * @param metadataFile the file to write
	 */
	public void write(File metadataFile) {
		Json.write(jsonRoot, metadataFile);
	}

	/**
	 * Get the configuration for a metadata field
	 * @param name field name
	 * @return the configuration
	 */
	public JSONObject getMetaFieldConfig(String name) {
		return getJSONObject(getJSONObject(getJSONObject(jsonRoot, "fieldInfo"), "metadataFields"), name);
	}

	/**
	 * Get the configuration for a complex field
	 * @param name field name
	 * @return the configuration
	 */
	public JSONObject getComplexFieldConfig(String name) {
		return getJSONObject(getJSONObject(getJSONObject(jsonRoot, "fieldInfo"), "complexFields"), name);
	}

	/**
	 * Get the configuration for the indexer
	 * @return the configuration
	 */
	public JSONObject getIndexerConfig() {
		return getJSONObject(jsonRoot, "indexerConfig");
	}


}
