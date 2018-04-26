package nl.inl.blacklab.search.indexstructure;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.Terms;
import org.apache.lucene.util.Bits;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import nl.inl.blacklab.index.DocIndexerFactory.Format;
import nl.inl.blacklab.index.DocumentFormats;
import nl.inl.blacklab.index.Indexer;
import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.index.config.ConfigAnnotatedField;
import nl.inl.blacklab.index.config.ConfigAnnotation;
import nl.inl.blacklab.index.config.ConfigCorpus;
import nl.inl.blacklab.index.config.ConfigCorpus.TextDirection;
import nl.inl.blacklab.index.config.ConfigInputFormat;
import nl.inl.blacklab.index.config.ConfigLinkedDocument;
import nl.inl.blacklab.index.config.ConfigMetadataBlock;
import nl.inl.blacklab.index.config.ConfigMetadataField;
import nl.inl.blacklab.index.config.ConfigMetadataFieldGroup;
import nl.inl.blacklab.index.config.ConfigStandoffAnnotations;
import nl.inl.blacklab.search.Searcher;
import nl.inl.blacklab.search.indexstructure.MetadataFieldDesc.UnknownCondition;
import nl.inl.util.FileUtil;
import nl.inl.util.Json;
import nl.inl.util.StringUtil;

/**
 * Determines the structure of a BlackLab index.
 */
public class IndexStructure {
	private static final Charset INDEX_STRUCT_FILE_ENCODING = Indexer.DEFAULT_INPUT_ENCODING;

	protected static final Logger logger = LogManager.getLogger(IndexStructure.class);

	private static final String METADATA_FILE_NAME = "indexmetadata";

	/**
	 * The latest index format. Written to the index metadata file.
	 *
	 * 3:   first version to include index metadata file
	 * 3.1: tag length in payload
	 */
	static final String LATEST_INDEX_FORMAT = "3.1";

	/** What keys may occur at top level? */
    static final Set<String> KEYS_TOP_LEVEL = new HashSet<>(Arrays.asList(
            "displayName", "description", "contentViewable", "textDirection",
            "documentFormat", "tokenCount", "versionInfo", "fieldInfo"));

    /** What keys may occur under versionInfo? */
    static final Set<String> KEYS_VERSION_INFO = new HashSet<>(Arrays.asList(
            "indexFormat", "blackLabBuildTime", "blackLabVersion", "timeCreated",
            "timeModified", "alwaysAddClosingToken", "tagLengthInPayload"));

    /** What keys may occur under fieldInfo? */
    static final Set<String> KEYS_FIELD_INFO = new HashSet<>(Arrays.asList(
            "namingScheme", "unknownCondition", "unknownValue",
            "metadataFields", "complexFields", "metadataFieldGroups",
            "defaultAnalyzer", "titleField", "authorField", "dateField", "pidField"));

    /** What keys may occur under metadataFieldGroups group? */
    static final Set<String> KEYS_METADATA_GROUP = new HashSet<>(Arrays.asList(
            "name", "fields", "addRemainingFields"));

    /** What keys may occur under metadata field config? */
    static final Set<String> KEYS_META_FIELD_CONFIG = new HashSet<>(Arrays.asList(
            "type", "displayName", "uiType",
            "description", "group", "analyzer",
            "unknownValue", "unknownCondition", "values",
            "displayValues", "displayOrder", "valueListComplete"));

    /** What keys may occur under complex field config? */
    static final Set<String> KEYS_COMPLEX_FIELD_CONFIG = new HashSet<>(Arrays.asList(
            "displayName", "description", "mainProperty",
            "noForwardIndexProps", "displayOrder", "annotations"));

	public static final DateFormat DATETIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	/** A named group of ordered metadata fields */
	public static class MetadataGroup {

	    String name;

	    List<String> fields;

	    boolean addRemainingFields = false;

	    public void setName(String name) {
            this.name = name;
        }

        public void setFields(List<String> fields) {
            this.fields = fields;
        }

        public MetadataGroup(String name, List<String> fields) {
	        this.name = name;
	        this.fields = new ArrayList<>(fields);
	    }

        public String getName() {
            return name;
        }

        public List<String> getFields() {
            return fields;
        }

        public boolean addRemainingFields() {
            return addRemainingFields;
        }

        public void setAddRemainingFields(boolean addRemainingFields) {
            this.addRemainingFields = addRemainingFields;
        }

	}

    /** Logical groups of metadata fields, for presenting them in the user interface. */
	protected Map<String, MetadataGroup> metadataGroups = new LinkedHashMap<>();

	/** All non-complex fields in our index (metadata fields) and their types. */
	protected Map<String, MetadataFieldDesc> metadataFieldInfos;

	/** When a metadata field value is considered "unknown" (never|missing|empty|missing_or_empty) [never] */
	protected String defaultUnknownCondition;

	/** What value to index when a metadata field value is unknown [unknown] */
	protected String defaultUnknownValue;

	/** The complex fields in our index */
	protected Map<String, ComplexFieldDesc> complexFields;

	/** The main contents field in our index. This is either the complex field with the name "contents",
	 *  or if that doesn't exist, the first complex field found. */
	protected ComplexFieldDesc mainContentsField;

	/** Where to save indexmetadata.json */
	protected File indexDir;

	/** Index display name */
	protected String displayName;

	/** Index description */
	protected String description;

	/** When BlackLab.jar was built */
	protected String blackLabBuildTime;

	/** BlackLab version used to (initially) create index */
	protected String blackLabVersion;

	/** Format the index uses */
	protected String indexFormat;

	/** Time at which index was created */
	protected String timeCreated;

	/** Time at which index was created */
	protected String timeModified;

	/** Metadata field containing document title */
	protected String titleField;

	/** Metadata field containing document author */
	protected String authorField;

	/** Metadata field containing document date */
	protected String dateField;

	/** Metadata field containing document pid */
	protected String pidField;

	/** Default analyzer to use for metadata fields */
	protected String defaultAnalyzerName;

	/** Do we always have words+1 tokens (before we sometimes did, if an XML tag
	 *  occurred after the last word; now we always make sure we have it, so we
	 *  can always skip the last token when matching) */
	protected boolean alwaysHasClosingToken = false;

	/** Do we store tag length in the payload (v3.1) or do we store tag ends
	 *  in a separate property (v3)? */
	protected boolean tagLengthInPayload = true;

	/** May all users freely retrieve the full content of documents, or is that restricted? */
	protected boolean contentViewable = false;

    /** Text direction for this corpus */
	protected TextDirection textDirection = TextDirection.LEFT_TO_RIGHT;

	/** Indication of the document format(s) in this index.
	 *
	 * This is in the form of a format identifier as understood
	 * by the DocumentFormats class (either an abbreviation or a
	 * (qualified) class name).
	 */
	protected String documentFormat;

	protected long tokenCount = 0;

	/**
	 * When we save this file, should we write it as json or yaml?
	 */
	protected boolean saveAsJson = true;

	/**
	 * Construct an IndexStructure object, querying the index for the available
	 * fields and their types.
	 * @param reader the index of which we want to know the structure
	 * @param indexDir where the index (and the metadata file) is stored
	 * @param createNewIndex whether we're creating a new index
	 */
	public IndexStructure(IndexReader reader, File indexDir, boolean createNewIndex) {
		this(reader, indexDir, createNewIndex, (File)null);
	}

    /**
     * Construct an IndexStructure object, querying the index for the available
     * fields and their types.
     *
     * @param reader the index of which we want to know the structure
     * @param indexDir where the index (and the metadata file) is stored
     * @param createNewIndex whether we're creating a new index
     * @param config input format config to use as template for index structure / metadata
     *   (if creating new index)
     */
    public IndexStructure(IndexReader reader, File indexDir, boolean createNewIndex, ConfigInputFormat config) {
        this.indexDir = indexDir;

        metadataFieldInfos = new TreeMap<>();
        complexFields = new TreeMap<>();

        // Find existing metadata file, if any.
        File metadataFile = FileUtil.findFile(Arrays.asList(indexDir), METADATA_FILE_NAME, Arrays.asList("json", "yaml", "yml"));
        if (metadataFile != null && createNewIndex) {
            // Don't leave the old metadata file if we're creating a new index
            metadataFile.delete();
        }

        // If none found, or creating new index: write a .yaml file.
        if (createNewIndex || metadataFile == null) {
            metadataFile = new File(indexDir, METADATA_FILE_NAME + ".yaml");
        }
        saveAsJson = false;
        if (createNewIndex && config != null) {

            // Create an index metadata file from this config.
            ConfigCorpus corpusConfig = config.getCorpusConfig();
            ObjectMapper mapper = Json.getJsonObjectMapper();
            ObjectNode jsonRoot = mapper.createObjectNode();
            String displayName = corpusConfig.getDisplayName();
            if (displayName.isEmpty())
                displayName = determineIndexName();
            jsonRoot.put("displayName", displayName);
            jsonRoot.put("description", corpusConfig.getDescription());
            jsonRoot.put("contentViewable", corpusConfig.isContentViewable());
            jsonRoot.put("textDirection", corpusConfig.getTextDirection().getCode());
            jsonRoot.put("documentFormat", config.getName());
            addVersionInfo(jsonRoot);
            ObjectNode fieldInfo = jsonRoot.putObject("fieldInfo");
            fieldInfo.put("defaultAnalyzer", config.getMetadataDefaultAnalyzer());
            for (Entry<String, String> e: corpusConfig.getSpecialFields().entrySet()) {
                fieldInfo.put(e.getKey(), e.getValue());
            }
            ArrayNode metaGroups = fieldInfo.putArray("metadataFieldGroups");
            ObjectNode metadata = fieldInfo.putObject("metadataFields");
            ObjectNode complex = fieldInfo.putObject("complexFields");

            addFieldInfoFromConfig(metadata, complex, metaGroups, config);
            extractFromJson(jsonRoot, null, false, false);
            writeMetadata();
        } else {
            // Read existing metadata or create empty new one
            readOrCreateMetadata(reader, createNewIndex, metadataFile, false);
        }
    }

	/**
	 * Construct an IndexStructure object, querying the index for the available
	 * fields and their types.
	 * @param reader the index of which we want to know the structure
	 * @param indexDir where the index (and the metadata file) is stored
	 * @param createNewIndex whether we're creating a new index
	 * @param indexTemplateFile JSON file to use as template for index structure / metadata
	 *   (if creating new index)
	 */
	public IndexStructure(IndexReader reader, File indexDir, boolean createNewIndex, File indexTemplateFile) {
		this.indexDir = indexDir;

		metadataFieldInfos = new TreeMap<>();
		complexFields = new TreeMap<>();

		// Find existing metadata file, if any.
        File metadataFile = FileUtil.findFile(Arrays.asList(indexDir), METADATA_FILE_NAME, Arrays.asList("json", "yaml", "yml"));
        if (metadataFile != null && createNewIndex) {
            // Don't leave the old metadata file if we're creating a new index
            metadataFile.delete();
        }

        // If none found, or creating new index: metadata file should be same format as template.
        if (createNewIndex || metadataFile == null) {
            // No metadata file yet, or creating a new index;
            // use same metadata format as the template
            boolean templateIsJson = false;
            if (indexTemplateFile != null && indexTemplateFile.getName().endsWith(".json"))
                templateIsJson = true;
            String templateExt = templateIsJson ? "json" : "yaml";
            if (createNewIndex && metadataFile != null) {
                // We're creating a new index, but also found a previous metadata file.
                // Is it a different format than the template? If so, we would end up
                // with two metadata files, which is confusing and might lead to errors.
                boolean existingIsJson = metadataFile.getName().endsWith(".json");
                if (existingIsJson != templateIsJson) {
                    // Delete the existing, different-format file to avoid confusion.
                    metadataFile.delete();
                }
            }
            metadataFile = new File(indexDir, METADATA_FILE_NAME + "." + templateExt);
        }
        saveAsJson = metadataFile.getName().endsWith(".json");
        boolean usedTemplate = false;
        if (createNewIndex && indexTemplateFile != null) {
        	// Copy the template file to the index dir and read the metadata again.
        	try {
        		String fileContents = FileUtils.readFileToString(indexTemplateFile, INDEX_STRUCT_FILE_ENCODING);
        		FileUtils.write(metadataFile, fileContents, INDEX_STRUCT_FILE_ENCODING);
        	} catch (IOException e) {
        		throw new RuntimeException(e);
        	}
        	usedTemplate = true;
        }

        readOrCreateMetadata(reader, createNewIndex, metadataFile, usedTemplate);
	}

    protected void readOrCreateMetadata(IndexReader reader, boolean createNewIndex, File metadataFile, boolean usedTemplate) {
        // Read and interpret index metadata file
		if ((createNewIndex && !usedTemplate) || !metadataFile.exists()) {
			// No metadata file yet; start with a blank one
			ObjectMapper mapper = Json.getJsonObjectMapper();
            ObjectNode jsonRoot = mapper.createObjectNode();
            jsonRoot.put("displayName", determineIndexName());
            jsonRoot.put("description", "");
            addVersionInfo(jsonRoot);
            ObjectNode fieldInfo = jsonRoot.putObject("fieldInfo");
            fieldInfo.putObject("metadataFields");
            fieldInfo.putObject("complexFields");
            extractFromJson(jsonRoot, reader, false, false);
		} else {
			// Read the metadata file
			try {
				boolean isJson = metadataFile.getName().endsWith(".json");
                ObjectMapper mapper = isJson ? Json.getJsonObjectMapper() : Json.getYamlObjectMapper();
                ObjectNode jsonRoot = (ObjectNode)mapper.readTree(metadataFile);
                extractFromJson(jsonRoot, reader, usedTemplate, false);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		// Detect main contents field and main properties of complex fields
        if (!createNewIndex) { // new index doesn't have this information yet
            // Detect the main properties for all complex fields
            // (looks for fields with char offset information stored)
            mainContentsField = null;
            for (ComplexFieldDesc d: complexFields.values()) {
                if (mainContentsField == null || d.getName().equals("contents"))
                    mainContentsField = d;
                if (tokenCount > 0) // no use trying this on an empty index
                    d.detectMainProperty(reader);
            }
        }
    }

    protected String determineIndexName() {
        String name = indexDir.getName();
        if (name.equals("index"))
        	name = indexDir.getAbsoluteFile().getParentFile().getName();
        return name;
    }

	public Map<String, MetadataGroup> getMetaFieldGroups() {
	    return Collections.unmodifiableMap(metadataGroups);
	}

    /**
	 * Indicate that the index was modified, so that fact
	 * will be recorded in the metadata file.
	 */
	public void setModified() {
		timeModified = IndexStructure.getTimestamp();
	}

	public void writeMetadata() {
        String ext = saveAsJson ? ".json" : ".yaml";
        File metadataFile = new File(indexDir, METADATA_FILE_NAME + ext);
        try {
            boolean isJson = metadataFile.getName().endsWith(".json");
            ObjectMapper mapper = isJson ? Json.getJsonObjectMapper() : Json.getYamlObjectMapper();
            mapper.writeValue(metadataFile, encodeToJson());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** Encode the index structure to an (in-memory) JSON structure.
     * @return json structure
     */
    private ObjectNode encodeToJson() {
        ObjectMapper mapper = Json.getJsonObjectMapper();
        ObjectNode jsonRoot = mapper.createObjectNode();
        jsonRoot.put("displayName", displayName);
        jsonRoot.put("description", description);
        jsonRoot.put("contentViewable", contentViewable);
        jsonRoot.put("textDirection", textDirection.getCode());
        jsonRoot.put("documentFormat", documentFormat);
        jsonRoot.put("tokenCount", tokenCount);
        ObjectNode versionInfo = jsonRoot.putObject("versionInfo");
        versionInfo.put("blackLabBuildTime", blackLabBuildTime);
        versionInfo.put("blackLabVersion", blackLabVersion);
        versionInfo.put("indexFormat", indexFormat);
        versionInfo.put("timeCreated", timeCreated);
        versionInfo.put("timeModified", timeModified);
        versionInfo.put("alwaysAddClosingToken", true); // Indicates that we always index words+1 tokens (last token is for XML tags after the last word)
        versionInfo.put("tagLengthInPayload", true); // Indicates that start tag property payload contains tag lengths, and there is no end tag property

        ObjectNode fieldInfo = jsonRoot.putObject("fieldInfo");
        fieldInfo.put("namingScheme", ComplexFieldUtil.avoidSpecialCharsInFieldNames() ? "NO_SPECIAL_CHARS": "DEFAULT");
        fieldInfo.put("defaultAnalyzer", defaultAnalyzerName);
        if (titleField != null)
            fieldInfo.put("titleField", titleField);
        if (authorField != null)
            fieldInfo.put("authorField", authorField);
        if (dateField != null)
            fieldInfo.put("dateField", dateField);
        if (pidField != null)
            fieldInfo.put("pidField", pidField);
        ArrayNode metadataFieldGroups = fieldInfo.putArray("metadataFieldGroups");
        ObjectNode metadataFields = fieldInfo.putObject("metadataFields");
        ObjectNode jsonComplexFields = fieldInfo.putObject("complexFields");

        // Add metadata field group info
        for (MetadataGroup g: metadataGroups.values()) {
            ObjectNode group = metadataFieldGroups.addObject();
            group.put("name", g.getName());
            if (g.addRemainingFields())
                group.put("addRemainingFields", true);
            ArrayNode arr = group.putArray("fields");
            Json.arrayOfStrings(arr, g.getFields());
        }

        // Add metadata field info
        for (MetadataFieldDesc f: metadataFieldInfos.values()) {
            UnknownCondition unknownCondition = f.getUnknownCondition();
            ObjectNode fi = metadataFields.putObject(f.getName());
            fi.put("displayName", f.getDisplayName());
            fi.put("uiType", f.getUiType());
            fi.put("description", f.getDescription());
            fi.put("type", f.getType().stringValue());
            fi.put("analyzer", f.getAnalyzerName());
            fi.put("unknownValue", f.getUnknownValue());
            fi.put("unknownCondition", unknownCondition == null ? defaultUnknownCondition : unknownCondition.toString());
            fi.put("valueListComplete", f.isValueListComplete());
            Map<String, Integer> values = f.getValueDistribution();
            if (values != null) {
                ObjectNode jsonValues = fi.putObject("values");
                for (Map.Entry<String, Integer> e: values.entrySet()) {
                    jsonValues.put(e.getKey(), e.getValue());
                }
            }
            Map<String, String> displayValues = f.getDisplayValues();
            if (displayValues != null) {
                ObjectNode jsonDisplayValues = fi.putObject("displayValues");
                for (Map.Entry<String, String> e: displayValues.entrySet()) {
                    jsonDisplayValues.put(e.getKey(), e.getValue());
                }
            }
            List<String> displayOrder = f.getDisplayOrder();
            if (displayOrder != null) {
                ArrayNode jsonDisplayValues = fi.putArray("displayOrder");
                for (String value: displayOrder) {
                    jsonDisplayValues.add(value);
                }
            }
        }

        // Add complex field info
        for (ComplexFieldDesc f: complexFields.values()) {
            ObjectNode fieldInfo2 = jsonComplexFields.putObject(f.getName());
            fieldInfo2.put("displayName", f.getDisplayName());
            fieldInfo2.put("description", f.getDescription());
            fieldInfo2.put("mainProperty", f.getMainProperty().getName());
            ArrayNode arr = fieldInfo2.putArray("displayOrder");
            Json.arrayOfStrings(arr, f.getDisplayOrder());
            ArrayNode annots = fieldInfo2.putArray("annotations");
            for (String propName: f.getProperties()) {
                PropertyDesc propDesc = f.getPropertyDesc(propName);
                ObjectNode annot = annots.addObject();
                annot.put("name", propDesc.getName());
                annot.put("displayName", propDesc.getDisplayName());
                annot.put("description", propDesc.getDescription());
                annot.put("uiType", propDesc.getUiType());
            }
        }

        return jsonRoot;

    }

	/**
	 * The main contents field in our index. This is either the complex field with the name "contents",
	 * or if that doesn't exist, the first complex field found.
	 * @return the main contents field
	 */
	public ComplexFieldDesc getMainContentsField() {
		return mainContentsField;
	}

	/** Detect type by finding the first document that includes this
	 * field and inspecting the Fieldable. This assumes that the field type
	 * is the same for all documents.
	 *
	 * @param fieldName the field name to determine the type for
	 * @return type of the field (text or numeric)
	 */
	@SuppressWarnings("static-method") // might not be static in the future
	private FieldType getFieldType(String fieldName) {

		/* NOTE: detecting the field type does not work well.
		 * Querying values and deciding based on those is not the right way
		 * (you can index ints as text too, after all). Lucene does not
		 * store the information in the index (and querying the field type does
		 * not return an IntField, DoubleField or such. In effect, it expects
		 * the client to know.
		 *
		 * We have a simple, bad approach based on field name below.
		 * The "right way" to do it is to keep a schema of field types during
		 * indexing.
		 */

		FieldType type = FieldType.TOKENIZED;
		if (fieldName.endsWith("Numeric") || fieldName.endsWith("Num"))
			type = FieldType.NUMERIC;
		return type;
	}

	/**
	 * Check if a Lucene field has offsets stored.
	 *
	 * @param reader
	 *            our index
	 * @param luceneFieldName
	 *            field to check
	 * @return true iff field has offsets
	 */
	static boolean hasOffsets(IndexReader reader, String luceneFieldName) {
		// Iterate over documents in the index until we find a property
		// for this complex field that has stored character offsets. This is
		// our main property.

		// Note that we can't simply retrieve the field from a document and
		// check the FieldType to see if it has offsets or not, as that information
		// is incorrect at search time (always set to false, even if it has offsets).

		Bits liveDocs = MultiFields.getLiveDocs(reader);
		for (int n = 0; n < reader.maxDoc(); n++) {
			if (liveDocs == null || liveDocs.get(n)) {
				try {
					Terms terms = reader.getTermVector(n, luceneFieldName);
					if (terms == null) {
						// No term vector; probably not stored in this document.
						continue;
					}
					if (terms.hasOffsets()) {
						// This field has offsets stored. Must be the main alternative.
						return true;
					}
					// This alternative has no offsets stored. Don't look at any more
					// documents, go to the next alternative.
					break;
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		}
		return false;
	}

	private ComplexFieldDesc getOrCreateComplexField(String name) {
		ComplexFieldDesc cfd = null;
		if (complexFields.containsKey(name))
			cfd = getComplexFieldDesc(name);
		if (cfd == null) {
			cfd = new ComplexFieldDesc(name);
			complexFields.put(name, cfd);
		}
		return cfd;
	}

	/** Get the names of all the complex fields in our index
	 * @return the complex field names */
	public Collection<String> getComplexFields() {
		return complexFields.keySet();
	}

	/** Get the description of one complex field
	 * @param fieldName name of the field
	 * @return the field description */
	public ComplexFieldDesc getComplexFieldDesc(String fieldName) {
		if (!complexFields.containsKey(fieldName))
			throw new IllegalArgumentException("Complex field '" + fieldName + "' not found!");
		return complexFields.get(fieldName);
	}

	/** Get the names of all the metadata fields in our index
	 * @return the names */
	public Collection<String> getMetadataFields() {
	    // Synchronized because we sometimes register new metadata fields during indexing
	    synchronized (metadataFieldInfos) {
    	    // Return a copy because we might create a new metadata field while
    	    // iterating over this one (because of indexFieldAs)
    		return new ArrayList<>(metadataFieldInfos.keySet());
	    }
	}

	public boolean hasMetadataField(String fieldName) {
		return metadataFieldInfos.containsKey(fieldName);
	}

	public MetadataFieldDesc getMetadataFieldDesc(String fieldName) {
	    MetadataFieldDesc d = null;
        // Synchronized because we sometimes register new metadata fields during indexing
	    synchronized (metadataFieldInfos) {
	        d = metadataFieldInfos.get(fieldName);
	    }
        if (d == null)
            throw new IllegalArgumentException("Metadata field '" + fieldName + "' not found!");
        return d;
	}

	/**
	 * Returns the metadata field containing the document title, if any.
	 *
	 * This field can be configured in the indexmetadata.json file.
	 * If it wasn't specified there, an intelligent guess is used.
	 *
	 * @return name of the title field, or null if none found
	 */
	public String titleField() {
		return titleField;
	}

	/**
	 * Returns the metadata field containing the document author, if any.
	 *
	 * This field can be configured in the indexmetadata.json file.
	 * If it wasn't specified there, an intelligent guess is used.
	 *
	 * @return name of the author field, or null if none found
	 */
	public String authorField() {
		return authorField;
	}

	/**
	 * Returns the metadata field containing the document date, if any.
	 *
	 * This field can be configured in the indexmetadata.json file.
	 * If it wasn't specified there, an intelligent guess is used.
	 *
	 * @return name of the date field, or null if none found
	 */
	public String dateField() {
		return dateField;
	}

	/**
	 * Returns the metadata field containing the document pid, if any.
	 *
	 * This field can be configured in the indexmetadata.json file.
	 * If it wasn't specified there, an intelligent guess is used.
	 *
	 * @return name of the pid field, or null if none found
	 */
	public String pidField() {
		return pidField;
	}

	/**
	 * Name of the default analyzer to use for metadata fields.
	 * @return the analyzer name (or DEFAULT for the BlackLab default)
	 */
	public String getDefaultAnalyzerName() {
		return defaultAnalyzerName;
	}

	/**
	 * Find the first (alphabetically) field whose name contains the search string.
	 *
	 * @param search the string to search for
	 * @return the field name, or null if no fields matched
	 */
	public String findTextField(String search) {
		return findTextField(search, true);
	}

	/**
	 * Find the first (alphabetically) field matching the search string.
	 *
	 * @param search the string to search for
	 * @param partialMatchOkay if false, only field names identical to the search
	 *  string match; if true, all field names containing the search string match.
	 * @return the field name, or null if no fields matched
	 */
	public String findTextField(String search, boolean partialMatchOkay) {
		// Find documents with title in the name
		List<String> fieldsFound = new ArrayList<>();
		for (Map.Entry<String, MetadataFieldDesc> e: metadataFieldInfos.entrySet()) {
			if (e.getValue().getType() == FieldType.TOKENIZED && e.getKey().toLowerCase().contains(search)) {
				if (partialMatchOkay || e.getKey().toLowerCase().equals(search))
					fieldsFound.add(e.getKey());
			}
		}
		if (fieldsFound.isEmpty())
			return null;

		// Sort (so we get titleLevel1 not titleLevel2 for example)
		Collections.sort(fieldsFound);
		return fieldsFound.get(0);
	}

	/**
	 * Print the index structure.
	 * @param out where to print it
	 */
	public void print(PrintWriter out) {
		out.println("COMPLEX FIELDS");
		for (ComplexFieldDesc cf: complexFields.values()) {
			out.println("- " + cf.getName());
			cf.print(out);
		}

		out.println("\nMETADATA FIELDS");
		for (Map.Entry<String, MetadataFieldDesc> e: metadataFieldInfos.entrySet()) {
			if (e.getKey().endsWith("Numeric"))
				continue; // special case, will probably be removed later
			FieldType type = e.getValue().getType();
			String special = "";
			if (e.getKey().equals(titleField))
				special = "TITLEFIELD";
			else if (e.getKey().equals(authorField))
				special = "AUTHORFIELD";
			else if (e.getKey().equals(dateField))
				special = "DATEFIELD";
			else if (e.getKey().equals(pidField))
				special = "PIDFIELD";
			if (special.length() > 0)
				special = " (" + special + ")";
			out.println("- " + e.getKey() + (type == FieldType.TOKENIZED ? "" : " (" + type + ")")
					+ special);
		}
	}

	/**
	 * Get the display name for the index.
	 *
	 * If no display name was specified, returns the name of the index directory.
	 *
	 * @return the display name
	 */
	public String getDisplayName() {
		String dispName = "index";
		if (displayName != null && displayName.length() != 0)
			dispName = displayName;
		if (dispName.equalsIgnoreCase("index"))
			dispName = StringUtils.capitalize(indexDir.getName());
		if (dispName.equalsIgnoreCase("index"))
			dispName = StringUtils.capitalize(indexDir.getAbsoluteFile().getParentFile().getName());
		return dispName;
	}

	/**
	 * Get a description of the index, if specified
	 * @return the description
	 */
	public String getDescription() {
		return description;
	}

	/**
	 * Is the content freely viewable by all users, or is it restricted?
	 * @return true if the full content may be retrieved by anyone
	 */
	public boolean contentViewable() {
		return contentViewable;
	}

    /**
     * What's the text direction of this corpus?
     * @return text direction
     */
	public TextDirection getTextDirection() {
	    return textDirection;
	}

	/**
	 * What format(s) is/are the documents in?
	 *
	 * This is in the form of a format identifier as understood
	 * by the DocumentFormats class (either an abbreviation or a
	 * (qualified) class name).
	 *
	 * @return the document format(s)
	 */
	public String getDocumentFormat() {
		return documentFormat;
	}

	/**
	 * What version of the index format is this?
	 * @return the index format version
	 */
	public String getIndexFormat() {
		return indexFormat;
	}

	/**
	 * When was this index created?
	 * @return date/time stamp
	 */
	public String getTimeCreated() {
		return timeCreated;
	}

	/**
	 * When was this index last modified?
	 * @return date/time stamp
	 */
	public String getTimeModified() {
		return timeCreated;
	}

	/**
	 * When was the BlackLab.jar used for indexing built?
	 * @return date/time stamp
	 */
	public String getIndexBlackLabBuildTime() {
		return blackLabBuildTime;
	}

	/**
	 * When was the BlackLab.jar used for indexing built?
	 * @return date/time stamp
	 */
	public String getIndexBlackLabVersion() {
		return blackLabVersion;
	}

	/**
	 * While indexing, check if a complex field is already registered in the
	 * metadata, and if not, add it now.
	 *
	 * @param fieldName field name
	 * @param mainPropName main property name
	 */
	public void registerComplexField(String fieldName, String mainPropName) {
		if (complexFields.containsKey(fieldName))
			return;
		// Not registered yet; do so now. Note that we only add the main property,
		// not the other properties, but that's okay; they're not needed at index
		// time and will be detected at search time.
		ComplexFieldDesc cf = getOrCreateComplexField(fieldName);
		cf.getOrCreateProperty(mainPropName); // create main property
		cf.setMainPropertyName(mainPropName); // set main property
	}

	public void registerMetadataField(String fieldName) {
		if (fieldName == null)
			throw new IllegalArgumentException("Tried to register a metadata field with null as name");
        // Synchronized because we might be using the map in another indexing thread
        synchronized (metadataFieldInfos) {
    		if (metadataFieldInfos.containsKey(fieldName))
    			return;
    		// Not registered yet; do so now.
    		MetadataFieldDesc mf = new MetadataFieldDesc(fieldName, FieldType.TOKENIZED);
    		mf.setUnknownCondition(UnknownCondition.fromStringValue(defaultUnknownCondition));
    		mf.setUnknownValue(defaultUnknownValue);
    		metadataFieldInfos.put(fieldName, mf);
        }
	}

	/**
	 * Do we always have words+1 tokens (before we sometimes did, if an XML tag
	 * occurred after the last word; now we always make sure we have it, so we
	 * can always skip the last token when matching)
	 *
	 * @return true if we do, false if we don't
	 */
	public boolean alwaysHasClosingToken() {
		return alwaysHasClosingToken;
	}

	/**
	 * Are tag lengths stored in the start tag payload (index v3.1 and higher)
	 * or are tag ends stored in a separate property (up until index v3)?
	 *
	 * @return true if tag lengths are stored in the start tag payload
	 */
	public boolean tagLengthInPayload() {
		return tagLengthInPayload;
	}

	/**
	 * Don't use this.
	 *
	 * Change the pid field. Do not use this method.
	 *
	 * This exists only to support a deprecated configuration setting in BlackLab Server
	 * and will eventually be removed.
	 *
	 * @param newPidField the pid field
	 * @deprecated method only exists to support deprecated setting, will be removed soon
	 */
	@Deprecated
	public void _setPidField(String newPidField) {
		this.pidField = newPidField;
	}

	/**
	 * Is this a new, empty index?
	 *
	 * An empty index is one that doesn't have a main contents field yet,
	 * or has a main contents field but no indexed tokens yet.
	 *
	 * @return true if it is, false if not.
	 */
	public boolean isNewIndex() {
		return mainContentsField == null || tokenCount == 0;
	}

	/**
	 * Set the display name for this index. Only makes
	 * sense in index mode where the change will be saved.
	 * Usually called when creating an index.
	 *
	 * @param displayName the display name to set.
	 */
	public void setDisplayName(String displayName) {
		if (displayName.length() > 80)
			displayName = StringUtil.abbreviate(displayName, 75);
		this.displayName = displayName;
	}

	/**
	 * Set a document format (or formats) for this index.
	 *
	 * This should be a format identifier as understood by the
	 * DocumentFormats class (either an abbreviation or a
	 * (qualified) class name).
	 *
	 * It only makes sense to call this in index mode, where
	 * this change will be saved.
	 *
	 * @param documentFormat the document format to store
	 */
	public void setDocumentFormat(String documentFormat) {
		this.documentFormat = documentFormat;
	}

	public void addToTokenCount(long tokensProcessed) {
		tokenCount += tokensProcessed;
	}

	public long getTokenCount() {
		return tokenCount;
	}

    /**
     * Used when creating an index to initialize contentViewable setting. Do not use otherwise.
     *
     * It is also used to support a deprecated configuration setting in BlackLab Server, but
     * this use will eventually be removed.
     *
     * @param contentViewable whether content may be freely viewed
     */
	public void setContentViewable(boolean contentViewable) {
		this.contentViewable = contentViewable;
	}

    /**
     * Used when creating an index to initialize textDirection setting. Do not use otherwise.
     *
     * @param textDirection text direction
     */
    public void setTextDirection(TextDirection textDirection) {
        this.textDirection = textDirection;
    }

    /**
     * If the object node contains any keys other than those specified, warn about it
     *
     * @param where where are we in the file (e.g. "top level", "complex field 'contents'", etc.)
     * @param node node to check
     * @param knownKeys keys that may occur under this node
     */
    private void warnUnknownKeys(String where, JsonNode node, Set<String> knownKeys) {
        Iterator<Entry<String, JsonNode>> it = node.fields();
        while (it.hasNext()) {
            Entry<String, JsonNode> e = it.next();
            String key = e.getKey();
            if (!knownKeys.contains(key))
                logger.warn("Unknown key " + key + " " + where + " in indexmetadata file");
        }
    }

    /** Extract the index structure from the (in-memory) JSON structure (and Lucene index).
     *
     * Looks at the Lucene index to detect certain information (sometimes) missing from the
     * JSON structure, such as naming scheme and available properties and alternatives for complex
     * fields. (Should probably eventually all be recorded in the metadata.)
     *
     * @param jsonRoot JSON structure to extract
     * @param reader index reader used to detect certain information, or null if we don't have an
     *          index reader (e.g. because we're creating a new index)
     * @param usedTemplate whether the JSON structure was read from a indextemplate file. If so,
     *          clear certain parts of it that aren't relevant anymore.
     * @param initTimestamps whether or not to update blacklab build time, version, and index
     *          creation/modification time
     */
    private void extractFromJson(ObjectNode jsonRoot, IndexReader reader, boolean usedTemplate, boolean initTimestamps) {
        // Read and interpret index metadata file
        warnUnknownKeys("at top-level", jsonRoot, KEYS_TOP_LEVEL);
        displayName = Json.getString(jsonRoot, "displayName", "");
        description = Json.getString(jsonRoot, "description", "");
        contentViewable = Json.getBoolean(jsonRoot, "contentViewable", false);
        textDirection = TextDirection.fromCode(Json.getString(jsonRoot, "textDirection", "ltr"));
        documentFormat = Json.getString(jsonRoot, "documentFormat", "");
        tokenCount = Json.getLong(jsonRoot, "tokenCount", 0);

        ObjectNode versionInfo = Json.getObject(jsonRoot, "versionInfo");
        warnUnknownKeys("in versionInfo", versionInfo, KEYS_VERSION_INFO);
        indexFormat = Json.getString(versionInfo, "indexFormat", "");
        if (initTimestamps) {
            blackLabBuildTime = Searcher.getBlackLabBuildTime();
            blackLabVersion = Searcher.getBlackLabVersion();
            timeModified = timeCreated = IndexStructure.getTimestamp();
        } else {
            blackLabBuildTime = Json.getString(versionInfo, "blackLabBuildTime", "UNKNOWN");
            blackLabVersion = Json.getString(versionInfo, "blackLabVersion", "UNKNOWN");
            timeCreated = Json.getString(versionInfo, "timeCreated", "");
            timeModified = Json.getString(versionInfo, "timeModified", timeCreated);
        }
        alwaysHasClosingToken = Json.getBoolean(versionInfo, "alwaysAddClosingToken", false);
        tagLengthInPayload = Json.getBoolean(versionInfo, "tagLengthInPayload", false);

        // Specified in index metadata file?
        String namingScheme;
        ObjectNode fieldInfo = Json.getObject(jsonRoot, "fieldInfo");
        warnUnknownKeys("in fieldInfo", fieldInfo, KEYS_FIELD_INFO);
        FieldInfos fis = reader == null ? null : MultiFields.getMergedFieldInfos(reader);
        if (fieldInfo.has("namingScheme")) {
            // Yes.
            namingScheme = fieldInfo.get("namingScheme").textValue();
            if (!namingScheme.equals("DEFAULT") && !namingScheme.equals("NO_SPECIAL_CHARS")) {
                throw new RuntimeException("Unknown value for namingScheme: " + namingScheme);
            }
            ComplexFieldUtil.setFieldNameSeparators(namingScheme.equals("NO_SPECIAL_CHARS"));
        } else {
            // Not specified; detect it.
            boolean hasNoFieldsYet = fis == null || fis.size() == 0;
            boolean usingSpecialCharsAsSeparators = hasNoFieldsYet;
            boolean usingCharacterCodesAsSeparators = false;
            if (fis != null) {
                for (int i1 = 0; i1 < fis.size(); i1++) {
                    FieldInfo fi = fis.fieldInfo(i1);
                    String name1 = fi.name;
                    if (name1.contains("%") || name1.contains("@") || name1.contains("#")) {
                        usingSpecialCharsAsSeparators = true;
                    }
                    if (name1.contains("_PR_") || name1.contains("_AL_") || name1.contains("_BK_")) {
                        usingCharacterCodesAsSeparators = true;
                    }
                }
            }
            if (!usingSpecialCharsAsSeparators && !usingCharacterCodesAsSeparators) {
                throw new RuntimeException("Could not detect index naming scheme. If your index was created with an old version of BlackLab, it may use the old naming scheme and cannot be opened with this version. Please re-index your data, or use a BlackLab version from before August 2014.");
            }
            if (usingSpecialCharsAsSeparators && usingCharacterCodesAsSeparators) {
                throw new RuntimeException("Your index seems to use two different naming schemes. Avoid using '%', '@', '#' or '_' in (metadata) field names and re-index your data.");
            }
            ComplexFieldUtil.setFieldNameSeparators(usingCharacterCodesAsSeparators);
        }
        defaultUnknownCondition = Json.getString(fieldInfo, "unknownCondition", "NEVER");
        defaultUnknownValue = Json.getString(fieldInfo, "unknownValue", "unknown");

        ObjectNode metaFieldConfigs = Json.getObject(fieldInfo, "metadataFields");
        boolean hasMetaFields = metaFieldConfigs.size() > 0;
        ObjectNode complexFieldConfigs = Json.getObject(fieldInfo, "complexFields");
        boolean hasComplexFields = complexFieldConfigs.size() > 0;
        boolean hasFieldInfo = hasMetaFields || hasComplexFields;

        if (hasFieldInfo && fieldInfo.has("metadataFieldGroups")) {
            metadataGroups.clear();
            JsonNode groups = fieldInfo.get("metadataFieldGroups");
            for (int i = 0; i < groups.size(); i++) {
                JsonNode group = groups.get(i);
                warnUnknownKeys("in metadataFieldGroup", group, KEYS_METADATA_GROUP);
                String name = Json.getString(group, "name", "UNKNOWN");
                List<String> fields = Json.getListOfStrings(group, "fields");
                MetadataGroup metadataGroup = new MetadataGroup(name, fields);
                if (Json.getBoolean(group, "addRemainingFields", false))
                    metadataGroup.setAddRemainingFields(true);
                metadataGroups.put(name, metadataGroup);
            }
        }
        if (hasFieldInfo) {
            // Metadata fields
            Iterator<Entry<String, JsonNode>> it = metaFieldConfigs.fields();
            while (it.hasNext()) {
                Entry<String, JsonNode> entry = it.next();
                String fieldName = entry.getKey();
                JsonNode fieldConfig = entry.getValue();
                warnUnknownKeys("in metadata field config for '" + fieldName + "'", fieldConfig, KEYS_META_FIELD_CONFIG);
                FieldType fieldType = FieldType.fromStringValue(Json.getString(fieldConfig, "type", "tokenized"));
                MetadataFieldDesc fieldDesc = new MetadataFieldDesc(fieldName, fieldType);
                fieldDesc.setDisplayName     (Json.getString(fieldConfig, "displayName", fieldName));
                fieldDesc.setUiType          (Json.getString(fieldConfig, "uiType", ""));
                fieldDesc.setDescription     (Json.getString(fieldConfig, "description", ""));
                fieldDesc.setGroup           (Json.getString(fieldConfig, "group", ""));
                fieldDesc.setAnalyzer        (Json.getString(fieldConfig, "analyzer", "DEFAULT"));
                fieldDesc.setUnknownValue    (Json.getString(fieldConfig, "unknownValue", defaultUnknownValue));
                UnknownCondition unk = UnknownCondition.fromStringValue(Json.getString(fieldConfig, "unknownCondition", defaultUnknownCondition));
                fieldDesc.setUnknownCondition(unk);
                if (fieldConfig.has("values"))
                    fieldDesc.setValues(fieldConfig.get("values"));
                if (fieldConfig.has("displayValues"))
                    fieldDesc.setDisplayValues(fieldConfig.get("displayValues"));
                if (fieldConfig.has("displayOrder"))
                    fieldDesc.setDisplayOrder(Json.getListOfStrings(fieldConfig, "displayOrder"));
                fieldDesc.setValueListComplete(Json.getBoolean(fieldConfig, "valueListComplete", false));
                metadataFieldInfos.put(fieldName, fieldDesc);
            }

            // Complex fields
            it = complexFieldConfigs.fields();
            while (it.hasNext()) {
                Entry<String, JsonNode> entry = it.next();
                String fieldName = entry.getKey();
                JsonNode fieldConfig = entry.getValue();
                warnUnknownKeys("in complex field config for '" + fieldName + "'", fieldConfig, KEYS_COMPLEX_FIELD_CONFIG);
                ComplexFieldDesc fieldDesc = new ComplexFieldDesc(fieldName);
                fieldDesc.setDisplayName (Json.getString(fieldConfig, "displayName", fieldName));
                fieldDesc.setDescription (Json.getString(fieldConfig, "description", ""));
                String mainPropertyName = Json.getString(fieldConfig, "mainProperty", "");
                if (mainPropertyName.length() > 0)
                    fieldDesc.setMainPropertyName(mainPropertyName);

                // Process information about annotations (displayName, uiType, etc.
                ArrayList<String> annotationOrder = new ArrayList<>();
                if (fieldConfig.has("annotations")) {
                    JsonNode annotations = fieldConfig.get("annotations");
                    Iterator<JsonNode> itAnnot = annotations.elements();
                    while (itAnnot.hasNext()) {
                        JsonNode annotation = itAnnot.next();
                        Iterator<Entry<String, JsonNode>> itAnnotOpt = annotation.fields();
                        PropertyDesc propDesc = new PropertyDesc();
                        while (itAnnotOpt.hasNext()) {
                            Entry<String, JsonNode> opt = itAnnotOpt.next();
                            switch (opt.getKey()) {
                            case "name":
                                propDesc.setName(opt.getValue().textValue());
                                annotationOrder.add(opt.getValue().textValue());
                                break;
                            case "displayName":
                                propDesc.setDisplayName(opt.getValue().textValue());
                                break;
                            case "description":
                                propDesc.setDescription(opt.getValue().textValue());
                                break;
                            case "uiType":
                                propDesc.setUiType(opt.getValue().textValue());
                                break;
                            default:
                                logger.warn("Unknown key " + opt.getKey() + " in annotation for field '" + fieldName + "' in indexmetadata file");
                                break;
                            }
                        }
                        if (StringUtils.isEmpty(propDesc.getName()))
                            logger.warn("Annotation entry without name for field '" + fieldName + "' in indexmetadata file; skipping");
                        else
                            fieldDesc.putProperty(propDesc);
                    }
                }

                // These properties should get no forward index
                // TODO: refactor this so this information is stored with each property instead, deprecating this setting
                JsonNode nodeNoForwardIndexProps = fieldConfig.get("noForwardIndexProps");
                if (nodeNoForwardIndexProps instanceof ArrayNode) {
                    Iterator<JsonNode> itNFIP = nodeNoForwardIndexProps.elements();
                    Set<String> noForwardIndex = new HashSet<>();
                    while (itNFIP.hasNext()) {
                        noForwardIndex.add(itNFIP.next().asText());
                    }
                    fieldDesc.setNoForwardIndexProps(noForwardIndex);
                } else {
                    String noForwardIndex = Json.getString(fieldConfig, "noForwardIndexProps", "").trim();
                    if (noForwardIndex.length() > 0) {
                        String[] noForwardIndexProps = noForwardIndex.split("\\s+");
                        fieldDesc.setNoForwardIndexProps(new HashSet<>(Arrays.asList(noForwardIndexProps)));
                    }
                }

                // This is the "natural order" of our annotations
                // (probably not needed anymore - if not specified, the order of the annotations will be used)
                List<String> displayOrder = Json.getListOfStrings(fieldConfig, "displayOrder");
                if (displayOrder.size() == 0) {
                    displayOrder.addAll(annotationOrder);
                }
                fieldDesc.setDisplayOrder(displayOrder);

                complexFields.put(fieldName, fieldDesc);
            }
        }
        if (fis != null) {
            // Detect fields
            for (int i = 0; i < fis.size(); i++) {
            	FieldInfo fi = fis.fieldInfo(i);
            	String name = fi.name;

            	// Parse the name to see if it is a metadata field or part of a complex field.
            	String[] parts;
            	if (name.endsWith("Numeric")) {
            		// Special case: this is not a property alternative, but a numeric
            		// alternative for a metadata field.
            		// (TODO: this should probably be changed or removed)
            		parts = new String[] { name };
            	} else {
            		parts = ComplexFieldUtil.getNameComponents(name);
            	}
            	if (parts.length == 1 && !complexFields.containsKey(parts[0])) {
            		if (!metadataFieldInfos.containsKey(name)) {
            			// Metadata field, not found in metadata JSON file
            			FieldType type = getFieldType(name);
            			MetadataFieldDesc metadataFieldDesc = new MetadataFieldDesc(name, type);
            			metadataFieldDesc.setUnknownCondition(UnknownCondition.fromStringValue(defaultUnknownCondition));
            			metadataFieldDesc.setUnknownValue(defaultUnknownValue);
            			metadataFieldInfos.put(name, metadataFieldDesc);
            		}
            	} else {
            		// Part of complex field.
            		if (metadataFieldInfos.containsKey(parts[0])) {
            			throw new RuntimeException(
            					"Complex field and metadata field with same name, error! ("
            							+ parts[0] + ")");
            		}

            		// Get or create descriptor object.
            		ComplexFieldDesc cfd = getOrCreateComplexField(parts[0]);
            		cfd.processIndexField(parts);
            	}
            } // even if we have metadata, we still have to detect props/alts
        }

        defaultAnalyzerName = Json.getString(fieldInfo, "defaultAnalyzer", "DEFAULT");

        titleField = authorField = dateField = pidField = null;
        if (fieldInfo.has("titleField"))
            titleField = fieldInfo.get("titleField").textValue();
        if (titleField == null) {
            titleField = findTextField("title");
            if (titleField == null) {
                titleField = "fromInputFile";
            }
        }
        if (fieldInfo.has("authorField"))
            authorField = fieldInfo.get("authorField").textValue();
        if (fieldInfo.has("dateField"))
            dateField = fieldInfo.get("dateField").textValue();
        if (fieldInfo.has("pidField"))
            pidField = fieldInfo.get("pidField").textValue();

        if (usedTemplate) {
            // Update / clear possible old values that were in the template file
            // (template file may simply be the metadata file copied from a previous version)

            // Reset version info
            blackLabBuildTime = Searcher.getBlackLabBuildTime();
            blackLabVersion = Searcher.getBlackLabVersion();
            indexFormat = LATEST_INDEX_FORMAT;
            timeModified = timeCreated = IndexStructure.getTimestamp();

            // Clear any recorded values in metadata fields
            for (MetadataFieldDesc f: metadataFieldInfos.values()) {
                f.resetForIndexing();
            }
        }
    }

    private static void addVersionInfo(ObjectNode jsonRoot) {
        ObjectNode versionInfo = jsonRoot.putObject("versionInfo");
        versionInfo.put("blackLabBuildTime", Searcher.getBlackLabBuildTime());
        versionInfo.put("blackLabVersion", Searcher.getBlackLabVersion());
        versionInfo.put("timeCreated", IndexStructure.getTimestamp());
        versionInfo.put("timeModified", IndexStructure.getTimestamp());
        versionInfo.put("indexFormat", IndexStructure.LATEST_INDEX_FORMAT);
        versionInfo.put("alwaysAddClosingToken", true);
        versionInfo.put("tagLengthInPayload", true);
    }

    private void addFieldInfoFromConfig(ObjectNode metadata, ObjectNode complex, ArrayNode metaGroups, ConfigInputFormat config) {

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
            ArrayNode displayOrder = g.putArray("displayOrder");
            ArrayNode noForwardIndexProps = g.putArray("noForwardIndexProps");
            ArrayNode annotations = g.putArray("annotations");
            for (ConfigAnnotation a: f.getAnnotations().values()) {
                displayOrder.add(a.getName());
                if (!a.createForwardIndex())
                    noForwardIndexProps.add(a.getName());
                ObjectNode annotation = annotations.addObject();
                annotation.put("name", a.getName());
                annotation.put("displayName", a.getDisplayName());
                annotation.put("description", a.getDescription());
                annotation.put("uiType", a.getUiType());
            }
            for (ConfigStandoffAnnotations standoff: f.getStandoffAnnotations()) {
                for (ConfigAnnotation a: standoff.getAnnotations().values()) {
                    displayOrder.add(a.getName());
                    if (!a.createForwardIndex())
                        noForwardIndexProps.add(a.getName());
                }
            }
        }

        // Also (recursively) add metadata and complex field config from any linked documents
        for (ConfigLinkedDocument ld: config.getLinkedDocuments().values()) {
            Format format = DocumentFormats.getFormat(ld.getInputFormatIdentifier());
            if (format.isConfigurationBased())
                addFieldInfoFromConfig(metadata, complex, metaGroups, format.getConfig());
        }
    }

    /**
     * Format the current date and time according to the SQL datetime convention.
     *
     * @return a string representation, e.g. "1980-02-01 00:00:00"
     */
    static String getTimestamp() {
    	return DATETIME_FORMAT.format(new Date());
    }


}
