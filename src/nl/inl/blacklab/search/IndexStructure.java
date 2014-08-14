package nl.inl.blacklab.search;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.index.complex.ComplexFieldUtil.BookkeepFieldType;
import nl.inl.util.DateUtil;
import nl.inl.util.FileUtil;
import nl.inl.util.Json;
import nl.inl.util.StringUtil;
import nl.inl.util.json.JSONArray;
import nl.inl.util.json.JSONObject;

import org.apache.log4j.Logger;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.Terms;
import org.apache.lucene.util.Bits;

/**
 * Determines the structure of a BlackLab index.
 */
public class IndexStructure {
	protected static final Logger logger = Logger.getLogger(IndexStructure.class);

	private static final String METADATA_FILE_NAME = "indexmetadata.json";

	/** Possible types of metadata fields. */
	public enum FieldType {
		TEXT, NUMERIC, UNTOKENIZED
	}

	/** Types of property alternatives */
	public enum AltType {
		UNKNOWN, SENSITIVE
	}

	/** Conditions for using the unknown value */
	public enum UnknownCondition {
		NEVER,            // never use unknown value
		MISSING,          // use unknown value when field is missing (not when empty)
		EMPTY,            // use unknown value when field is empty (not when missing)
		MISSING_OR_EMPTY  // use unknown value when field is empty or missing
	}

	public static abstract class BaseFieldDesc {
		/** Complex field's name */
		protected String fieldName;

		/** Complex field's name */
		protected String displayName;

		/** Complex field's name */
		protected String description = "";

		public BaseFieldDesc(String fieldName) {
			this(fieldName, null);
		}

		public BaseFieldDesc(String fieldName, String displayName) {
			this.fieldName = fieldName;
			this.displayName = displayName == null ? fieldName : displayName;
		}

		/** Get this complex field's name
		 * @return this field's name */
		public String getName() {
			return fieldName;
		}

		public void setDisplayName(String displayName) {
			this.displayName = displayName;
		}

		/** Get this complex field's display name
		 * @return this field's display name */
		public String getDisplayName() {
			return displayName;
		}

		public void setDescription(String description) {
			this.description = description;
		}

		/** Get this complex field's display name
		 * @return this field's display name */
		public String getDescription() {
			return description;
		}

	}

	public static class MetadataFieldDesc extends BaseFieldDesc {

		protected FieldType type;

		private String analyzer;

		private String unknownValue;

		private UnknownCondition unknownCondition;

		private List<String> values;

		private boolean valueListComplete;

		public MetadataFieldDesc(String fieldName, FieldType type) {
			super(fieldName);
			this.type = type;
		}

		public MetadataFieldDesc(String fieldName, String typeName) {
			super(fieldName);
			if (typeName.equals("untokenized")) {
				this.type = FieldType.UNTOKENIZED;
			} else if (typeName.equals("tokenized") || typeName.equals("text")) {
				this.type = FieldType.TEXT;
			} else if (typeName.equals("numeric")) {
				this.type = FieldType.NUMERIC;
			} else {
				throw new RuntimeException("Unknown field type name: " + typeName);
			}
		}

		public FieldType getType() {
			return type;
		}

		public void setAnalyzer(String analyzer) {
			this.analyzer = analyzer;
		}

		public void setUnknownValue(String unknownValue) {
			this.unknownValue = unknownValue;
		}

		public void setUnknownCondition(String unknownCondition) {
			if (unknownCondition.equals("NEVER")) {
				this.unknownCondition = UnknownCondition.NEVER;
			} else if (unknownCondition.equals("MISSING")) {
				this.unknownCondition = UnknownCondition.MISSING;
			} else if (unknownCondition.equals("EMPTY")) {
				this.unknownCondition = UnknownCondition.EMPTY;
			} else if (unknownCondition.equals("MISSING_OR_EMPTY")) {
				this.unknownCondition = UnknownCondition.MISSING_OR_EMPTY;
			} else {
				throw new RuntimeException("Unknown unknown condition: " + unknownCondition);
			}
		}

		public void setUnknownCondition(UnknownCondition unknownCondition) {
			this.unknownCondition = unknownCondition;
		}

		public void setValues(JSONArray values) {
			this.values = new ArrayList<String>(values.length());
			for (int i = 0; i < values.length(); i++) {
				this.values.add(values.getString(i));
			}
		}

		public void setValues(Collection<String> values) {
			this.values = new ArrayList<String>(values.size());
			for (String value: values) {
				this.values.add(value);
			}
		}

		public void setValueListComplete(boolean valueListComplete) {
			this.valueListComplete = valueListComplete;
		}

		public String getAnalyzer() {
			return analyzer;
		}

		public String getUnknownValue() {
			return unknownValue;
		}

		public UnknownCondition getUnknownCondition() {
			return unknownCondition;
		}

		public Collection<String> getValues() {
			return values;
		}

		public boolean isValueListComplete() {
			return valueListComplete;
		}

		/**
		 * Reset the information that is dependent on input data
		 * (i.e. list of values, etc.) because we're going to
		 * (re-)index the data.
		 */
		public void resetForIndexing() {
			this.values.clear();
			valueListComplete = true;
		}

	}

	/** Description of a complex field */
	public static class ComplexFieldDesc extends BaseFieldDesc {

		/** This complex field's properties */
		private Map<String, PropertyDesc> props;

		/** The field's main property */
		private PropertyDesc mainProperty;

		/** The field's main property name (for storing the main prop name before we have the prop. descriptions) */
		private String mainPropertyName;

		/** Does the field have an associated content store? */
		private boolean contentStore;

		/** Is the field length in tokens stored? */
		private boolean lengthInTokens;

		/** Are there XML tag locations stored for this field? */
		private boolean xmlTags;

		public ComplexFieldDesc(String name) {
			super(name);
			props = new TreeMap<String, PropertyDesc>();
			contentStore = false;
			lengthInTokens = false;
			xmlTags = false;
			mainProperty = null;
		}

		@Override
		public String toString() {
			return fieldName + " [" + StringUtil.join(props.values(), ", ") + "]";
		}

		/** Get the set of property names for this complex field
		 * @return the set of properties
		 */
		public Collection<String> getProperties() {
			return props.keySet();
		}

		/**
		 * Get a property description.
		 * @param name name of the property
		 * @return the description
		 */
		public PropertyDesc getPropertyDesc(String name) {
			if (!props.containsKey(name))
				throw new RuntimeException("Property '" + name + "' not found!");
			return props.get(name);
		}

		public boolean hasContentStore() {
			return contentStore;
		}

		public boolean hasLengthTokens() {
			return lengthInTokens;
		}

		public boolean hasXmlTags() {
			return xmlTags;
		}

		/**
		 * Checks if this field has a "punctuation" forward index, storing all the
		 * intra-word characters (whitespace and punctuation) so we can build concordances
		 * directly from the forward indices.
		 * @return true iff there's a punctuation forward index.
		 */
		public boolean hasPunctuation() {
			PropertyDesc pd = props.get(ComplexFieldUtil.PUNCTUATION_PROP_NAME);
			return pd != null && pd.hasForwardIndex();
		}

		/**
		 * An index field was found and split into parts, and belongs
		 * to this complex field. See what type it is and update our
		 * fields accordingly.
		 * @param parts parts of the Lucene index field name
		 */
		void processIndexField(String[] parts) {

			// See if this is a builtin bookkeeping field or a property.
			if (parts.length == 1)
				throw new RuntimeException("Complex field with just basename given, error!");

			String propPart = parts.length == 1 ? "" : parts[1];

			if (propPart == null && parts.length >= 3) {
				// Bookkeeping field
				BookkeepFieldType bookkeepingFieldIndex = ComplexFieldUtil
						.whichBookkeepingSubfield(parts[3]);
				switch (bookkeepingFieldIndex) {
				case CONTENT_ID:
					// Complex field has content store
					contentStore = true;
					return;
				case FORWARD_INDEX_ID:
					// Main property has forward index
					getOrCreateProperty("").setForwardIndex(true);
					return;
				case LENGTH_TOKENS:
					// Complex field has length in tokens
					lengthInTokens = true;
					return;
				}
				throw new RuntimeException();
			}

			// Not a bookkeeping field; must be a property (alternative).
			PropertyDesc pd = getOrCreateProperty(propPart);
			if (pd.getName().equals(ComplexFieldUtil.START_TAG_PROP_NAME))
				xmlTags = true;
			if (parts.length > 2) {
				if (parts[2] != null) {
					// Alternative
					pd.addAlternative(parts[2]);
				} else if (parts.length >= 3) {
					// Property bookkeeping field
					if (parts[3].equals(ComplexFieldUtil.FORWARD_INDEX_ID_BOOKKEEP_NAME)) {
						pd.setForwardIndex(true);
					} else
						throw new RuntimeException("Unknown property bookkeeping field " + parts[3]);
				} else {
					// No alternative specified; this is an error.
					throw new RuntimeException("No alternative given!");
				}
			}
		}

		private PropertyDesc getOrCreateProperty(String name) {
			PropertyDesc pd = props.get(name);
			if (pd == null) {
				pd = new PropertyDesc(name);
				props.put(name, pd);
			}
			return pd;
		}

		public PropertyDesc getMainProperty() {
			return mainProperty;
		}

		public void detectMainProperty(IndexReader reader) {
			if (mainPropertyName != null && mainPropertyName.length() > 0) {
				// Main property name was set from index metadata before we
				// had the property desc. available; use that now and don't do
				// any actual detecting.
				mainProperty = getPropertyDesc(mainPropertyName);
				mainPropertyName = null;
				return;
			}

			PropertyDesc firstProperty = null;
			for (PropertyDesc pr: props.values()) {
				if (firstProperty == null)
					firstProperty = pr;
				if (pr.detectOffsetsAlternative(reader, fieldName)) {
					// This field has offsets stored. Must be the main prop field.
					mainProperty = pr;
					return;
				}
			}

			// None have offsets; just assume the first property is the main one
			// (note that not having any offsets makes it impossible to highlight the
			// original content, but this may not be an issue. We probably need
			// a better way to keep track of the main property)
			mainProperty = firstProperty;

			// throw new RuntimeException(
			// "No main property (with char. offsets) detected for complex field " + fieldName);
		}

		@Deprecated
		public void print(PrintStream out) {
			print(new PrintWriter(out));
		}

		public void print(PrintWriter out) {
			for (PropertyDesc pr: props.values()) {
				out.println("  * Property: " + pr.toString());
			}
			out.println("  * " + (contentStore ? "Includes" : "No") + " content store");
			out.println("  * " + (xmlTags ? "Includes" : "No") + " XML tag index");
			out.println("  * " + (lengthInTokens ? "Includes" : "No") + " document length field");
		}

		public void setMainPropertyName(String mainPropertyName) {
			this.mainPropertyName = mainPropertyName;
		}

	}

	/** Description of a property */
	public static class PropertyDesc {
		/** The property name */
		private String propName;

		/** Any alternatives this property may have */
		private Map<String, AltDesc> alternatives;

		private boolean forwardIndex;

		/** Which of the alternatives is the main one (containing the offset info, if present) */
		private AltDesc offsetsAlternative;

		public PropertyDesc(String name) {
			propName = name;
			alternatives = new TreeMap<String, AltDesc>();
			forwardIndex = false;
		}

		@Override
		public String toString() {
			String altDesc = "";
			String altList = StringUtil.join(alternatives.values(), "\", \"");
			if (alternatives.size() > 1)
				altDesc = ", with alternatives \"" + altList + "\"";
			else if (alternatives.size() == 1)
				altDesc = ", with alternative \"" + altList + "\"";
			return (propName.length() == 0 ? "(default)" : propName)
					+ (forwardIndex ? " (+FI)" : "") + altDesc;
		}

		public boolean hasForwardIndex() {
			return forwardIndex;
		}

		public void addAlternative(String name) {
			AltDesc altDesc = new AltDesc(name);
			alternatives.put(name, altDesc);
		}

		void setForwardIndex(boolean b) {
			forwardIndex = b;
		}

		/** Get this property's name
		 * @return the name */
		public String getName() {
			return propName;
		}

		/** Get the set of names of alternatives for this property
		 * @return the names
		 */
		public Collection<String> getAlternatives() {
			return alternatives.keySet();
		}

		/**
		 * Get an alternative's description.
		 * @param name name of the alternative
		 * @return the description
		 */
		public AltDesc getAlternativeDesc(String name) {
			if (!alternatives.containsKey(name))
				throw new RuntimeException("Alternative '" + name + "' not found!");
			return alternatives.get(name);
		}

		/**
		 * Detect which alternative is the one containing character offsets.
		 *
		 * Note that there may not be such an alternative.
		 *
		 * @param reader the index reader
		 * @param fieldName the field this property belongs under
		 * @return true if found, false if not
		 */
		public boolean detectOffsetsAlternative(IndexReader reader, String fieldName) {
			// Iterate over the alternatives and for each alternative, find a term
			// vector. If that has character offsets stored, it's our main property.
			// If not, keep searching.
			for (AltDesc alt: alternatives.values()) {
				String luceneAltName = ComplexFieldUtil.propertyField(fieldName, propName,
						alt.getName());
				if (hasOffsets(reader, luceneAltName)) {
					offsetsAlternative = alt;
					return true;
				}
			}

			return false;
		}

		/**
		 * Return which alternative contains character offset information.
		 *
		 * Note that there may not be such an alternative.
		 *
		 * @return the alternative, or null if there is none.
		 */
		public AltDesc getOffsetsAlternative() {
			return offsetsAlternative;
		}
	}

	/** Description of a property alternative */
	public static class AltDesc {
		/** name of this alternative */
		private String altName;

		/** type of this alternative */
		private AltType type;

		public AltDesc(String name) {
			altName = name;
			type = name.equals("s") ? AltType.SENSITIVE : AltType.UNKNOWN;
		}

		@Override
		public String toString() {
			return altName;
		}

		/** Get the name of this alternative
		 * @return the name
		 */
		public String getName() {
			return altName;
		}

		/** Get the type of this alternative
		 * @return the type
		 */
		public AltType getType() {
			return type;
		}
	}

	/** All non-complex fields in our index (metadata fields) and their types. */
	private Map<String, MetadataFieldDesc> metadataFieldInfos;

	/** The complex fields in our index */
	private Map<String, ComplexFieldDesc> complexFields;

	/** The main contents field in our index. This is either the complex field with the name "contents",
	 *  or if that doesn't exist, the first complex field found. */
	private ComplexFieldDesc mainContentsField;

	/** Where to save indexmetadata.json */
	private File indexDir;

	/** Index display name */
	private String displayName;

	/** Index description */
	private String description;

	/** When BlackLab.jar was built */
	private String blackLabBuildDate;

	/** Format the index uses */
	private String indexFormat;

	/** Time at which index was created */
	private String timeCreated;

	/** Time at which index was created */
	private String timeModified;

	/** Metadata field containing document title */
	private String titleField;

	/** Metadata field containing document author */
	private String authorField;

	/** Metadata field containing document date */
	private String dateField;

	/** Metadata field containing document pid */
	private String pidField;

	/** The index reader */
	private DirectoryReader reader;

	/**
	 * Construct an IndexStructure object, querying the index for the available
	 * fields and their types.
	 * @param reader the index of which we want to know the structure
	 * @param indexDir where the index (and the metadata file) is stored
	 * @param createNewIndex whether we're creating a new index
	 */
	public IndexStructure(DirectoryReader reader, File indexDir, boolean createNewIndex) {
		this.reader = reader;
		this.indexDir = indexDir;

		metadataFieldInfos = new TreeMap<String, MetadataFieldDesc>();
		complexFields = new TreeMap<String, ComplexFieldDesc>();

		readMetadata(reader, createNewIndex);

		// Detect the main properties for all complex fields
		// (looks for fields with char offset information stored)
		mainContentsField = null;
		for (ComplexFieldDesc d: complexFields.values()) {
			if (mainContentsField == null || d.getName().equals("contents"))
				mainContentsField = d;
			d.detectMainProperty(reader);
		}
	}

	/**
	 * Read the indexmetadata.json, if it exists.
	 *
	 * @param reader
	 * @param indexDir
	 * @param createNewIndex
	 */
	private void readMetadata(DirectoryReader reader, boolean createNewIndex) {
		// Read and interpret index metadata file
		File metadataFile = new File(indexDir, METADATA_FILE_NAME);
		IndexMetadata indexMetadata;
		if (createNewIndex || !metadataFile.exists()) {
			// No metadata file yet; start with a blank one
			indexMetadata = new IndexMetadata(indexDir.getName());
		} else {
			// Read the metadata file
			try {
				indexMetadata = new IndexMetadata(metadataFile);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		displayName = indexMetadata.getDisplayName();
		description = indexMetadata.getDescription();
		JSONObject versionInfo = indexMetadata.getVersionInfo();
		blackLabBuildDate = Json.getString(versionInfo, "blackLabBuildDate", "");
		indexFormat = Json.getString(versionInfo, "indexFormat", "");
		timeCreated = Json.getString(versionInfo, "timeCreated", "");
		timeModified = Json.getString(versionInfo, "timeModified", timeCreated);
		FieldInfos fis = MultiFields.getMergedFieldInfos(reader);
		setNamingScheme(indexMetadata, fis);
		if (indexMetadata.hasFieldInfo()) {
			getFieldInfoFromMetadata(indexMetadata, fis);
		}
		detectFields(fis); // even if we have metadata, we still have to detect props/alts
		determineDocumentsFields(indexMetadata);
	}

	/**
	 * Indicate that the index was modified, so that fact
	 * will be recorded in the metadata file.
	 */
	public void setModified() {
		timeModified = DateUtil.getSqlDateTimeString();
	}

	public void writeMetadata() {
		File metadataFile = new File(indexDir, METADATA_FILE_NAME);
		IndexMetadata indexMetadata = new IndexMetadata(indexDir.getName());
		JSONObject root = indexMetadata.getRoot();
		root.put("displayName", displayName);
		root.put("description", description);
		root.put("versionInfo", Json.object(
			"blackLabBuildDate", blackLabBuildDate,
			"indexFormat", indexFormat,
			"timeCreated", timeCreated,
			"timeModified", timeModified
		));
		JSONObject metadataFields = new JSONObject();
		JSONObject jsonComplexFields = new JSONObject();
		root.put("fieldInfo", Json.object(
			"namingScheme", ComplexFieldUtil.avoidSpecialCharsInFieldNames() ? "NO_SPECIAL_CHARS": "DEFAULT",
			"titleField", titleField,
			"authorField", authorField,
			"dateField", dateField,
			"pidField", pidField,
			"metadataFields", metadataFields,
			"complexFields", jsonComplexFields
		));

		// Add metadata field info
		for (MetadataFieldDesc f: metadataFieldInfos.values()) {
			UnknownCondition unknownCondition = f.getUnknownCondition();
			JSONObject fieldInfo = Json.object(
				"displayName", f.getDisplayName(),
				"description", f.getDescription(),
				"type", f.getType().toString().toLowerCase(),
				"analyzer", "default",
				"unknownValue", f.getUnknownValue(),
				"unknownCondition", unknownCondition == null ? "NEVER" : unknownCondition.toString(),
				"valueListComplete", f.isValueListComplete()
			);
			JSONArray jsonValues = new JSONArray();
			Collection<String> values = f.getValues();
			if (values != null) {
				for (String value: values) {
					jsonValues.put(value);
				}
				fieldInfo.put("values", jsonValues);
			}
			metadataFields.put(f.getName(), fieldInfo);
		}

		// Add complex field info
		for (ComplexFieldDesc f: complexFields.values()) {

			/*
			JSONObject jsonProperties = new JSONObject();
			for (String propName: f.getProperties()) {
				PropertyDesc prop = f.getPropertyDesc(propName);
				jsonProperties.put(propName, Json.object(
					// (we might want to store per-property information in the future)
				));
			}
			*/

			jsonComplexFields.put(f.getName(), Json.object(
				"displayName", f.getDisplayName(),
				"description", f.getDescription(),
				"mainProperty", f.getMainProperty().getName()
				//, "properties", jsonProperties
			));
		}

		// Write the file
		indexMetadata.write(metadataFile);
	}

	/**
	 * Get field information from the index metadata file.
	 *
	 * @param indexMetadata the metadata information
	 * @param fis the Lucene field infos
	 */
	@SuppressWarnings("unchecked")
	private void getFieldInfoFromMetadata(IndexMetadata indexMetadata, FieldInfos fis) {

		// Metadata fields
		Iterator<String> it = indexMetadata.getMetaFieldConfigs().keys();
		while (it.hasNext()) {
			String fieldName = it.next();
			JSONObject fieldConfig = indexMetadata.getMetaFieldConfig(fieldName);
			String displayName = Json.getString(fieldConfig, "displayName", fieldName);
			String description = Json.getString(fieldConfig, "description", "");
			String type = Json.getString(fieldConfig, "type", "tokenized");
			String analyzer = Json.getString(fieldConfig, "analyzer", "default");
			String unknownValue = Json.getString(fieldConfig, "unknownValue", "unknown");
			String unknownCondition = Json.getString(fieldConfig, "unknownCondition", "NEVER");
			JSONArray values = null;
			if (fieldConfig.has("values")) {
				values = fieldConfig.getJSONArray("values");
			}
			boolean valueListComplete = Json.getBoolean(fieldConfig, "valueListComplete", false);

			MetadataFieldDesc fieldDesc = new MetadataFieldDesc(fieldName, type);
			fieldDesc.setDescription(description);
			fieldDesc.setDisplayName(displayName);
			fieldDesc.setAnalyzer(analyzer);
			fieldDesc.setUnknownValue(unknownValue);
			fieldDesc.setUnknownCondition(unknownCondition);
			if (values != null)
				fieldDesc.setValues(values);
			fieldDesc.setValueListComplete(valueListComplete);
			metadataFieldInfos.put(fieldName, fieldDesc);
		}

		// Complex fields
		it = indexMetadata.getComplexFieldConfigs().keys();
		while (it.hasNext()) {
			String fieldName = it.next();
			JSONObject fieldConfig = indexMetadata.getComplexFieldConfig(fieldName);
			String displayName = Json.getString(fieldConfig, "displayName", fieldName);
			String description = Json.getString(fieldConfig, "description", "");
			String mainProperty = Json.getString(fieldConfig, "mainProperty", "");
			// TODO: useAnnotation..?
			ComplexFieldDesc fieldDesc = new ComplexFieldDesc(fieldName);
			fieldDesc.setDisplayName(displayName);
			fieldDesc.setDescription(description);
			if (mainProperty.length() > 0)
				fieldDesc.setMainPropertyName(mainProperty);
			complexFields.put(fieldName, fieldDesc);
		}
	}

	/**
	 * Try to detect field information from the Lucene index.
	 * Will not be perfect.
	 *
	 * @param fis the Lucene field infos
	 */
	private void detectFields(FieldInfos fis) {
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
					metadataFieldInfos.put(name, new MetadataFieldDesc(name, type));
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
		}
	}

	/**
	 * Detect which field naming scheme our index uses.
	 *
	 * Either gets the naming scheme from the index metadata file,
	 * or tries to detect it in the index.
	 *
	 * There was an old naming scheme which is no longer supported;
	 * the default scheme is the one using %, # and @ as separators;
	 * alternative is the one using only underscores and character
	 * codes for use with certain other software that doesn't like
	 * special characters in field names.
	 *
	 * @param indexMetadata the metadata
	 * @param fis field infos
	 */
	private void setNamingScheme(IndexMetadata indexMetadata, FieldInfos fis) {
		// Specified in index metadata file?
		String namingScheme = indexMetadata.getFieldNamingScheme();
		if (namingScheme != null) {
			// Yes.
			ComplexFieldUtil.setFieldNameSeparators(namingScheme.equals("NO_SPECIAL_CHARS"));
			return;
		}

		// Not specified; detect it.
		boolean hasNoFieldsYet = fis.size() == 0;
		boolean usingSpecialCharsAsSeparators = hasNoFieldsYet;
		boolean usingCharacterCodesAsSeparators = false;
		for (int i = 0; i < fis.size(); i++) {
			FieldInfo fi = fis.fieldInfo(i);
			String name = fi.name;
			if (name.contains("%") || name.contains("@") || name.contains("#")) {
				usingSpecialCharsAsSeparators = true;
			}
			if (name.contains("_PR_") || name.contains("_AL_") || name.contains("_BK_")) {
				usingCharacterCodesAsSeparators = true;
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

		FieldType type = FieldType.TEXT;
		if (fieldName.endsWith("Numeric") || fieldName.endsWith("Num"))
			type = FieldType.NUMERIC;
		return type;
	}

	/** See if a field has character offset information stored.
	 *
	 * @param luceneFieldName the field name to inspect
	 * @return true if it has offsets, false if not
	 * @deprecated throws an exception. Shouldn't be used directly by applications; main property is detected automatically
	 */
	@Deprecated
	public boolean hasOffsets(String luceneFieldName) {
		throw new RuntimeException("hasOffsets() shouldn't be called directly by application; main property is detected automatically");
		//return hasOffsets(reader, luceneFieldName);
	}

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
			throw new RuntimeException("Complex field '" + fieldName + "' not found!");
		return complexFields.get(fieldName);
	}

	/** Get the names of all the metadata fields in our index
	 * @return the names */
	public Collection<String> getMetadataFields() {
		return metadataFieldInfos.keySet();
	}

	public MetadataFieldDesc getMetadataFieldDesc(String fieldName) {
		if (!metadataFieldInfos.containsKey(fieldName))
			throw new RuntimeException("Metadata field '" + fieldName + "' not found!");
		return metadataFieldInfos.get(fieldName);
	}

	/** Get the type of one metadata field
	 * @param fieldName name of the field
	 * @return the type of field
	 * @deprecated use getMetadataFieldDesc(fieldName).getType() instead
	 */
	@Deprecated
	public IndexStructure.FieldType getMetadataType(String fieldName) {
		return getMetadataFieldDesc(fieldName).getType();
	}

	/**
	 * Read document field names (title, author, date, pid)
	 * from indexmetadata.json file.
	 * If the title field wasn't specified, we'll make an educated
	 * guess. (we don't try to guess at the other fields)
	 *
	 * @param indexMetadata the metadata
	 */
	private void determineDocumentsFields(IndexMetadata indexMetadata) {
		titleField = authorField = dateField = pidField = null;
		JSONObject fi = indexMetadata.getFieldInfo();

		if (fi.has("titleField")) {
			titleField = fi.getString("titleField");
		}
		if (titleField == null) {
			titleField = findTextField("title");
			if (titleField == null) {
				// Use the first text field we can find.
				for (Map.Entry<String, MetadataFieldDesc> e: metadataFieldInfos.entrySet()) {
					if (e.getValue().getType() == FieldType.TEXT) {
						titleField = e.getKey();
						return;
					}
				}
			}
		}
		if (fi.has("authorField"))
			authorField = fi.getString("authorField");
		if (fi.has("dateField"))
			dateField = fi.getString("dateField");
		if (fi.has("pidField"))
			pidField = fi.getString("pidField");
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
	 * @return the title field
	 * @deprecated renamed to titleField()
	 */
	@Deprecated
	public String getDocumentTitleField() {
		return titleField();
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
		List<String> fieldsFound = new ArrayList<String>();
		for (Map.Entry<String, MetadataFieldDesc> e: metadataFieldInfos.entrySet()) {
			if (e.getValue().getType() == FieldType.TEXT && e.getKey().toLowerCase().contains(search)) {
				if (partialMatchOkay || e.getKey().toLowerCase().equals(search))
					fieldsFound.add(e.getKey());
			}
		}
		if (fieldsFound.size() == 0)
			return null;

		// Sort (so we get titleLevel1 not titleLevel2 for example)
		Collections.sort(fieldsFound);
		return fieldsFound.get(0);
	}

	/**
	 * Print the index structure.
	 * @param out where to print it
	 * @deprecated use version that takes a PrintWriter
	 */
	@Deprecated
	public void print(PrintStream out) {
		print(new PrintWriter(out));
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
		String titleField = getDocumentTitleField();
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
			out.println("- " + e.getKey() + (type == FieldType.TEXT ? "" : " (" + type + ")")
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
		if (displayName != null && displayName.length() != 0)
			return displayName;
		return indexDir.getName();
	}

	/**
	 * Get a description of the index, if specified
	 * @return the description
	 */
	public String getDescription() {
		return description;
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
	 * When was the BlackLab.jar used built?
	 * @return date/time stamp
	 */
	public String getBlackLabBuildDate() {
		return blackLabBuildDate;
	}

	/**
	 * Set the template for the indexmetadata.json file for a new index.
	 *
	 * The template determines whether and how fields are tokenized/analyzed,
	 * indicates which fields are title/author/date/pid fields, and provides
	 * extra (optional) information like display names and descriptions.
	 *
	 * This method should be called just after creating the new index. It cannot
	 * be used on existing indices; if you need to change something about your
	 * index metadata, edit the file directly (but be careful, as it of course
	 * will not affect already-indexed data).
	 *
	 * @param indexTemplateFile the JSON file to use as a template.
	 */
	public void setNewIndexMetadataTemplate(File indexTemplateFile) {
		// Copy the template file to the index dir and read the metadata again.
		File indexMetadataFile = new File(indexDir, METADATA_FILE_NAME);
		FileUtil.writeFile(indexMetadataFile, FileUtil.readFile(indexTemplateFile));
		readMetadata(reader, false);

		// Reset version info
		blackLabBuildDate = ""; // TODO: figure out BlackLab build date and record it here
		indexFormat = "3";
		timeModified = timeCreated = DateUtil.getSqlDateTimeString();

		// Clear any recorded values in metadata fields
		for (MetadataFieldDesc f: metadataFieldInfos.values()) {
			f.resetForIndexing();
		}
	}
}
