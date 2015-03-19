package nl.inl.blacklab.search.indexstructure;

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
import nl.inl.blacklab.search.Searcher;
import nl.inl.util.DateUtil;
import nl.inl.util.FileUtil;
import nl.inl.util.Json;
import nl.inl.util.StringUtil;
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
	private String blackLabBuildTime;

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

	/** Default analyzer to use for metadata fields */
	private String defaultAnalyzerName;

	/** Do we always have words+1 tokens (before we sometimes did, if an XML tag
	 *  occurred after the last word; now we always make sure we have it, so we
	 *  can always skip the last token when matching) */
	private boolean alwaysHasClosingToken = false;

	/** May all users freely retrieve the full content of documents, or is that restricted? */
	private boolean contentViewable = false;

	/** Indication of the document format(s) in this index.
	 *
	 * This is in the form of a format identifier as understood
	 * by the DocumentFormats class (either an abbreviation or a
	 * (qualified) class name).
	 */
	private String documentFormat;

	private long tokenCount = 0;

	/**
	 * Construct an IndexStructure object, querying the index for the available
	 * fields and their types.
	 * @param reader the index of which we want to know the structure
	 * @param indexDir where the index (and the metadata file) is stored
	 * @param createNewIndex whether we're creating a new index
	 */
	public IndexStructure(DirectoryReader reader, File indexDir, boolean createNewIndex) {
		this(reader, indexDir, createNewIndex, (File)null);
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
	public IndexStructure(DirectoryReader reader, File indexDir,
			boolean createNewIndex, File indexTemplateFile) {
		//this.reader = reader;
		this.indexDir = indexDir;

		metadataFieldInfos = new TreeMap<String, MetadataFieldDesc>();
		complexFields = new TreeMap<String, ComplexFieldDesc>();

		readMetadata(reader, createNewIndex, indexTemplateFile);

		if (!createNewIndex) { // new index doesn't have this information yet
			// Detect the main properties for all complex fields
			// (looks for fields with char offset information stored)
			mainContentsField = null;
			for (ComplexFieldDesc d: complexFields.values()) {
				if (mainContentsField == null || d.getName().equals("contents"))
					mainContentsField = d;
				d.detectMainProperty(reader);
			}
		}
	}

	/**
	 * Read the indexmetadata.json, if it exists, or the template, if supplied.
	 *
	 * @param reader the index of which we want to know the structure
	 * @param indexDir where the index (and the metadata file) is stored
	 * @param createNewIndex whether we're creating a new index
	 * @param indexTemplateFile JSON file to use as template for index structure / metadata
	 *   (only if creating a new index)
	 */
	private void readMetadata(DirectoryReader reader, boolean createNewIndex, File indexTemplateFile) {

		File metadataFile = new File(indexDir, METADATA_FILE_NAME);
		boolean usedTemplate = false;
		if (createNewIndex && indexTemplateFile != null) {
			// Copy the template file to the index dir and read the metadata again.
			FileUtil.writeFile(metadataFile, FileUtil.readFile(indexTemplateFile));
			usedTemplate = true;
		}

		// Read and interpret index metadata file
		IndexMetadata indexMetadata;
		boolean initTimestamps = false;
		if ((createNewIndex && !usedTemplate) || !metadataFile.exists()) {
			// No metadata file yet; start with a blank one
			String name = indexDir.getName();
			if (name.equals("index"))
				name = indexDir.getParentFile().getName();
			indexMetadata = new IndexMetadata(name);
			initTimestamps = true;
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
		contentViewable = indexMetadata.getContentViewable();
		documentFormat = indexMetadata.getDocumentFormat();
		tokenCount = indexMetadata.getTokenCount();
		JSONObject versionInfo = indexMetadata.getVersionInfo();
		indexFormat = Json.getString(versionInfo, "indexFormat", "");
		if (initTimestamps) {
			blackLabBuildTime = Searcher.getBlackLabBuildTime();
			timeModified = timeCreated = DateUtil.getSqlDateTimeString();
		} else {
			blackLabBuildTime = Json.getString(versionInfo, "blackLabBuildTime", "UNKNOWN");
			timeCreated = Json.getString(versionInfo, "timeCreated", "");
			timeModified = Json.getString(versionInfo, "timeModified", timeCreated);
		}
		alwaysHasClosingToken = Json.getBoolean(versionInfo, "alwaysAddClosingToken", false);
		FieldInfos fis = MultiFields.getMergedFieldInfos(reader);
		setNamingScheme(indexMetadata, fis);
		if (indexMetadata.hasFieldInfo()) {
			getFieldInfoFromMetadata(indexMetadata, fis);
		}
		detectFields(fis); // even if we have metadata, we still have to detect props/alts
		defaultAnalyzerName = indexMetadata.getDefaultAnalyzer();
		determineDocumentFields(indexMetadata);

		if (usedTemplate) {
			// Update / clear possible old values that were in the template file
			// (template file may simply be the metadata file copied from a previous version)

			// Reset version info
			blackLabBuildTime = Searcher.getBlackLabBuildTime();
			indexFormat = "3";
			timeModified = timeCreated = DateUtil.getSqlDateTimeString();

			// Clear any recorded values in metadata fields
			for (MetadataFieldDesc f: metadataFieldInfos.values()) {
				f.resetForIndexing();
			}
		}
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
		root.put("contentViewable", contentViewable);
		root.put("documentFormat", documentFormat);
		root.put("tokenCount", tokenCount);
		root.put("versionInfo", Json.object(
			"blackLabBuildTime", blackLabBuildTime,
			"indexFormat", indexFormat,
			"timeCreated", timeCreated,
			"timeModified", timeModified,
			"alwaysAddClosingToken", true // Indicates that we always index words+1 tokens (last token is for XML tags after the last word)
		));
		JSONObject metadataFields = new JSONObject();
		JSONObject jsonComplexFields = new JSONObject();
		root.put("fieldInfo", Json.object(
			"namingScheme", ComplexFieldUtil.avoidSpecialCharsInFieldNames() ? "NO_SPECIAL_CHARS": "DEFAULT",
			"defaultAnalyzer", defaultAnalyzerName,
			"titleField", titleField,
			"authorField", authorField,
			"dateField", dateField,
			"pidField", pidField,
			"metadataFields", metadataFields,
			"complexFields", jsonComplexFields
		));

		// Add metadata field info
		for (MetadataFieldDesc f: metadataFieldInfos.values()) {
			MetadataFieldDesc.UnknownCondition unknownCondition = f.getUnknownCondition();
			JSONObject fieldInfo = Json.object(
				"displayName", f.getDisplayName(),
				"description", f.getDescription(),
				"type", f.getType().toString().toLowerCase(),
				"analyzer", f.getAnalyzerName(),
				"unknownValue", f.getUnknownValue(),
				"unknownCondition", unknownCondition == null ? "NEVER" : unknownCondition.toString(),
				"valueListComplete", f.isValueListComplete()
			);
			JSONObject jsonValues = new JSONObject();
			Map<String, Integer> values = f.getValueDistribution();
			if (values != null) {
				for (Map.Entry<String, Integer> e: values.entrySet()) {
					jsonValues.put(e.getKey(), e.getValue());
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
			String group = Json.getString(fieldConfig, "group", "");
			String type = Json.getString(fieldConfig, "type", "tokenized");
			String analyzer = Json.getString(fieldConfig, "analyzer", "DEFAULT");
			String unknownValue = Json.getString(fieldConfig, "unknownValue", "unknown");
			String unknownCondition = Json.getString(fieldConfig, "unknownCondition", "NEVER");
			JSONObject values = null;
			if (fieldConfig.has("values")) {
				values = fieldConfig.getJSONObject("values");
			}
			boolean valueListComplete = Json.getBoolean(fieldConfig, "valueListComplete", false);

			MetadataFieldDesc fieldDesc = new MetadataFieldDesc(fieldName, type);
			fieldDesc.setDisplayName(displayName);
			fieldDesc.setDescription(description);
			fieldDesc.setGroup(group);
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
	public FieldType getMetadataType(String fieldName) {
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
	private void determineDocumentFields(IndexMetadata indexMetadata) {
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
		String dispName = "index";
		if (displayName != null && displayName.length() != 0)
			dispName = displayName;
		if (dispName.equalsIgnoreCase("index"))
			dispName = StringUtil.capitalizeFirst(indexDir.getName());
		if (dispName.equalsIgnoreCase("index"))
			dispName = StringUtil.capitalizeFirst(indexDir.getParentFile().getName());
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

//	/**
//	 * Set the template for the indexmetadata.json file for a new index.
//	 *
//	 * The template determines whether and how fields are tokenized/analyzed,
//	 * indicates which fields are title/author/date/pid fields, and provides
//	 * extra (optional) information like display names and descriptions.
//	 *
//	 * This method should be called just after creating the new index. It cannot
//	 * be used on existing indices; if you need to change something about your
//	 * index metadata, edit the file directly (but be careful, as it of course
//	 * will not affect already-indexed data).
//	 *
//	 * @param indexTemplateFile the JSON file to use as a template.
//	 */
//	public void setNewIndexMetadataTemplate(File indexTemplateFile) {
//		// Copy the template file to the index dir and read the metadata again.
//		File indexMetadataFile = new File(indexDir, METADATA_FILE_NAME);
//		FileUtil.writeFile(indexMetadataFile, FileUtil.readFile(indexTemplateFile));
//		readMetadata(reader, false);
//
//		// Reset version info
//		blackLabBuildTime = Searcher.getBlackLabBuildTime();
//		indexFormat = "3";
//		timeModified = timeCreated = DateUtil.getSqlDateTimeString();
//
//		// Clear any recorded values in metadata fields
//		for (MetadataFieldDesc f: metadataFieldInfos.values()) {
//			f.resetForIndexing();
//		}
//	}

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
			throw new RuntimeException("Tried to register a metadata field with null as name");
		if (metadataFieldInfos.containsKey(fieldName))
			return;
		// Not registered yet; do so now.
		MetadataFieldDesc mf = new MetadataFieldDesc(fieldName, FieldType.TEXT);
		metadataFieldInfos.put(fieldName, mf);
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
	 * Change the content viewable setting. Do not use this method.
	 *
	 * This exists only to support a deprecated configuration setting in BlackLab Server
	 * and will eventually be removed.
	 *
	 * @param b whether content may be freely viewed
	 * @deprecated method only exists to support deprecated setting, will be removed soon
	 */
	@Deprecated
	public void _setContentViewable(boolean b) {
		this.contentViewable = b;
	}

	/**
	 * Is this a new, empty index?
	 *
	 * An empty index is one that doesn't have a main contents field yet.
	 *
	 * @return true if it is, false if not.
	 */
	public boolean isNewIndex() {
		return mainContentsField == null;
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

	public void setContentViewable(boolean contentViewable) {
		this.contentViewable = contentViewable;
	}

}
