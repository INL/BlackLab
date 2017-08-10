package nl.inl.blacklab.search.indexstructure;

import java.io.File;
import java.io.IOException;
import java.util.Map.Entry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import nl.inl.blacklab.index.DocIndexerFactory;
import nl.inl.blacklab.index.DocIndexerFactoryConfig;
import nl.inl.blacklab.index.config.ConfigAnnotatedField;
import nl.inl.blacklab.index.config.ConfigAnnotation;
import nl.inl.blacklab.index.config.ConfigCorpus;
import nl.inl.blacklab.index.config.ConfigInputFormat;
import nl.inl.blacklab.index.config.ConfigLinkedDocument;
import nl.inl.blacklab.index.config.ConfigMetadataBlock;
import nl.inl.blacklab.index.config.ConfigMetadataField;
import nl.inl.blacklab.index.config.ConfigMetadataFieldGroup;
import nl.inl.blacklab.index.config.ConfigStandoffAnnotations;
import nl.inl.blacklab.search.Searcher;
import nl.inl.util.Json;

/**
 * Reads/writes the indexmetadata.json file, and provides
 * an interface to the information contained within it.
 */
public class IndexMetadata {

	/** Our configuration data */
	ObjectNode jsonRoot;

	/**
	 * Construct an index metadata object from a JSON file.
	 * @param metadataFile the file to read
	 * @throws IOException if the file was not found
	 * @throws JsonProcessingException if there's a syntax error in the file
	 */
	public IndexMetadata(File metadataFile) throws JsonProcessingException, IOException {
        boolean isJson = metadataFile.getName().endsWith(".json");
	    ObjectMapper mapper = isJson ? Json.getJsonObjectMapper() : Json.getYamlObjectMapper();
		jsonRoot = (ObjectNode)mapper.readTree(metadataFile);
	}

	/**
	 * Construct a new index metadata object.
	 * @param indexName name of the index
	 */
	public IndexMetadata(String indexName) {
        ObjectMapper mapper = Json.getJsonObjectMapper();
	    jsonRoot = mapper.createObjectNode();
	    jsonRoot.put("displayName", indexName);
	    jsonRoot.put("description", "");
	    addVersionInfo();
	    ObjectNode fieldInfo = jsonRoot.putObject("fieldInfo");
	    fieldInfo.putObject("metadataFields");
	    fieldInfo.putObject("complexFields");
	}

    public IndexMetadata(String indexName, ConfigInputFormat config) {
        ConfigCorpus corpusConfig = config.getCorpusConfig();
        ObjectMapper mapper = Json.getJsonObjectMapper();
        jsonRoot = mapper.createObjectNode();
        String displayName = corpusConfig.getDisplayName();
        if (displayName.isEmpty())
            displayName = indexName;
        jsonRoot.put("displayName", displayName);
        jsonRoot.put("description", corpusConfig.getDescription());
        jsonRoot.put("contentViewable", corpusConfig.isContentViewable());
        jsonRoot.put("documentFormat", config.getName());
        addVersionInfo();
        ObjectNode fieldInfo = jsonRoot.putObject("fieldInfo");
        fieldInfo.put("defaultAnalyzer", config.getMetadataDefaultAnalyzer());
        for (Entry<String, String> e: corpusConfig.getSpecialFields().entrySet()) {
            fieldInfo.put(e.getKey(), e.getValue());
        }
        ArrayNode metaGroups = fieldInfo.putArray("metadataFieldGroups");
        ObjectNode metadata = fieldInfo.putObject("metadataFields");
        ObjectNode complex = fieldInfo.putObject("complexFields");

        addFieldInfoFromConfig(metadata, complex, metaGroups, config);
    }

    protected void addFieldInfoFromConfig(ObjectNode metadata, ObjectNode complex, ArrayNode metaGroups, ConfigInputFormat config) {

        // Add metadata field groups info
        ConfigCorpus corpusConfig = config.getCorpusConfig();
        for (ConfigMetadataFieldGroup g: corpusConfig.getMetadataFieldGroups().values()) {
            ObjectNode h = metaGroups.addObject();
            h.put("name", g.getName());
            if (g.getFields().size() > 0) {
                ArrayNode i = h.putArray("fields");
                for (String f: g.getFields()) {
                    i.add(f);
                }
            }
            if (g.isAddRemainingFields())
                h.put("addRemainingFields", true);
        }

        // Add metadata info
        String defaultAnalyzer = config.getMetadataDefaultAnalyzer();
        for (ConfigMetadataBlock b: config.getMetadataBlocks()) {
            for (ConfigMetadataField f: b.getFields()) {
                if (f.isForEach())
                    continue;
                ObjectNode g = metadata.putObject(f.getName());
                g.put("displayName", f.getDisplayName());
                g.put("description", f.getDescription());
                g.put("type", f.getType().stringValue());
                if (!f.getAnalyzer().equals(defaultAnalyzer))
                    g.put("analyzer", f.getAnalyzer());
                g.put("uiType", f.getUiType());
                g.put("unknownCondition", f.getUnknownCondition().stringValue());
                g.put("unknownValue", f.getUnknownValue());
                ObjectNode h = g.putObject("displayValues");
                for (Entry<String, String> e: f.getDisplayValues().entrySet()) {
                    h.put(e.getKey(), e.getValue());
                }
                ArrayNode i = g.putArray("displayOrder");
                for (String v: f.getDisplayOrder()) {
                    i.add(v);
                }
            }
        }

        // Add complex field info
        for (ConfigAnnotatedField f: config.getAnnotatedFields().values()) {
            ObjectNode g = complex.putObject(f.getName());
            g.put("displayName", f.getDisplayName());
            g.put("description", f.getDescription());
            g.put("mainProperty", f.getAnnotations().values().iterator().next().getName());
            ArrayNode h = g.putArray("displayOrder");
            ArrayNode noForwardIndexProps = g.putArray("noForwardIndexProps");
            for (ConfigAnnotation a: f.getAnnotations().values()) {
                h.add(a.getName());
                if (!a.createForwardIndex())
                    noForwardIndexProps.add(a.getName());
            }
            for (ConfigStandoffAnnotations standoff: f.getStandoffAnnotations()) {
                for (ConfigAnnotation a: standoff.getAnnotations().values()) {
                    h.add(a.getName());
                    if (!a.createForwardIndex())
                        noForwardIndexProps.add(a.getName());
                }
            }
        }

        // Also (recursively) add metadata and complex field config from any linked documents
        for (ConfigLinkedDocument ld: config.getLinkedDocuments().values()) {
            DocIndexerFactory factory = ld.getInputFormat();
            if (factory instanceof DocIndexerFactoryConfig)
                addFieldInfoFromConfig(metadata, complex, metaGroups, factory.getConfig());
        }
    }

    protected void addVersionInfo() {
        ObjectNode versionInfo = jsonRoot.putObject("versionInfo");
	    versionInfo.put("blackLabBuildTime", Searcher.getBlackLabBuildTime());
	    versionInfo.put("blackLabVersion", Searcher.getBlackLabVersion());
	    versionInfo.put("timeCreated", IndexStructure.getTimestamp());
	    versionInfo.put("timeModified", IndexStructure.getTimestamp());
	    versionInfo.put("indexFormat", IndexStructure.LATEST_INDEX_FORMAT);
	    versionInfo.put("alwaysAddClosingToken", true);
	    versionInfo.put("tagLengthInPayload", true);
    }

    /**
	 * Write the index metadata to a JSON file.
	 * @param metadataFile the file to write
	 */
	public void write(File metadataFile) {
		try {
		    boolean isJson = metadataFile.getName().endsWith(".json");
		    ObjectMapper mapper = isJson ? Json.getJsonObjectMapper() : Json.getYamlObjectMapper();
		    mapper.writeValue(metadataFile, jsonRoot);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
	}

	/**
	 * Get the field info (metadata and complex fields).
	 * @return the configuration
	 */
	public ObjectNode getFieldInfo() {
		return Json.getObject(jsonRoot, "fieldInfo");
	}

    /**
     * Get the configuration for all metadata field groups.
     * @return the configuration
     */
    public ArrayNode getMetaFieldGroupConfigs() {
        return (ArrayNode)getFieldInfo().get("metadataFieldGroups");
    }

	/**
	 * Get the configuration for all metadata fields.
	 * @return the configuration
	 */
	public ObjectNode getMetaFieldConfigs() {
		return Json.getObject(getFieldInfo(), "metadataFields");
	}

	/**
	 * Get the configuration for a metadata field.
	 * @param name field name
	 * @return the configuration
	 */
	public ObjectNode getMetaFieldConfig(String name) {
		return Json.getObject(getMetaFieldConfigs(), name);
	}

	/**
	 * Get the configuration for all complex fields.
	 * @return the configuration
	 */
	public ObjectNode getComplexFieldConfigs() {
		return Json.getObject(getFieldInfo(), "complexFields");
	}

	/**
	 * Get the configuration for a complex field.
	 * @param name field name
	 * @return the configuration
	 */
	public ObjectNode getComplexFieldConfig(String name) {
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
	public ObjectNode getVersionInfo() {
		return Json.getObject(jsonRoot, "versionInfo");
	}

	public String getDisplayName() {
		if (!jsonRoot.has("displayName"))
			return "";
		return jsonRoot.get("displayName").textValue();
	}

	public String getDescription() {
		if (!jsonRoot.has("description"))
			return "";
		return jsonRoot.get("description").textValue();
	}

	public boolean getContentViewable() {
		if (!jsonRoot.has("contentViewable"))
			return false;
		return jsonRoot.get("contentViewable").booleanValue();
	}

	public String getDocumentFormat() {
		if (!jsonRoot.has("documentFormat"))
			return "";
		return jsonRoot.get("documentFormat").textValue();
	}

	public long getTokenCount() {
		if (!jsonRoot.has("tokenCount"))
			return 0;
		return jsonRoot.get("tokenCount").longValue();
	}

	/**
	 * Get the configuration for the indexer.
	 * @return the configuration
	 */
	public JsonNode getIndexerConfig() {
		return Json.getObject(jsonRoot, "indexerConfig");
	}

	/**
	 * Get the field naming scheme if specified.
	 * @return the field naming scheme, or null if not specified.
	 */
	public String getFieldNamingScheme() {
		JsonNode metaFieldInfo = getFieldInfo();
		if (metaFieldInfo.has("namingScheme")) {
			String namingScheme = metaFieldInfo.get("namingScheme").textValue();
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
		JsonNode metaFieldInfo = getFieldInfo();
		if (metaFieldInfo.has("defaultAnalyzer")) {
			return metaFieldInfo.get("defaultAnalyzer").textValue();
		}
		return "DEFAULT";
	}

	/**
	 * Is there field info in this index metadata file?
	 * @return true if there is, false if not
	 */
	public boolean hasFieldInfo() {
		boolean hasMetaFields = getMetaFieldConfigs().size() > 0;
		boolean hasComplexFields = getComplexFieldConfigs().size() > 0;
		return hasMetaFields || hasComplexFields;
	}

	public ObjectNode getRoot() {
		return jsonRoot;
	}

	public String getDefaultUnknownCondition() {
		JsonNode fieldInfo = getFieldInfo();
		if (!fieldInfo.has("unknownCondition"))
			return "NEVER";
		return fieldInfo.get("unknownCondition").textValue();
	}

	public String getDefaultUnknownValue() {
		JsonNode fieldInfo = getFieldInfo();
		if (!fieldInfo.has("unknownValue"))
			return "unknown";
		return fieldInfo.get("unknownValue").textValue();
	}

    public boolean hasMetaFieldGroupInfo() {
        return hasFieldInfo() && getFieldInfo().has("metadataFieldGroups");
    }

}
