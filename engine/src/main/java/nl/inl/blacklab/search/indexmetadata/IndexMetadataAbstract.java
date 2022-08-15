package nl.inl.blacklab.search.indexmetadata;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.IndexReader;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.IndexVersionMismatch;
import nl.inl.blacklab.index.DocIndexerFactory.Format;
import nl.inl.blacklab.index.DocumentFormats;
import nl.inl.blacklab.index.annotated.AnnotatedFieldWriter;
import nl.inl.blacklab.index.annotated.AnnotationWriter;
import nl.inl.blacklab.indexers.config.ConfigAnnotatedField;
import nl.inl.blacklab.indexers.config.ConfigAnnotation;
import nl.inl.blacklab.indexers.config.ConfigAnnotationGroup;
import nl.inl.blacklab.indexers.config.ConfigAnnotationGroups;
import nl.inl.blacklab.indexers.config.ConfigCorpus;
import nl.inl.blacklab.indexers.config.ConfigInputFormat;
import nl.inl.blacklab.indexers.config.ConfigLinkedDocument;
import nl.inl.blacklab.indexers.config.ConfigMetadataBlock;
import nl.inl.blacklab.indexers.config.ConfigMetadataField;
import nl.inl.blacklab.indexers.config.ConfigMetadataFieldGroup;
import nl.inl.blacklab.indexers.config.ConfigStandoffAnnotations;
import nl.inl.blacklab.indexers.config.TextDirection;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.BlackLabIndexIntegrated;
import nl.inl.util.Json;
import nl.inl.util.TimeUtil;

/**
 * Determines the structure of a BlackLab index.
 */
@SuppressWarnings("deprecation")
public abstract class IndexMetadataAbstract implements IndexMetadataWriter {

    private static final Logger logger = LogManager.getLogger(IndexMetadataAbstract.class);

    /** What keys may occur at top level? */
    private static final Set<String> KEYS_TOP_LEVEL = new HashSet<>(Arrays.asList(
            "displayName", "description", "contentViewable", "textDirection",
            "documentFormat", "tokenCount", "versionInfo", "fieldInfo"));

    /** What keys may occur under versionInfo? */
    private static final Set<String> KEYS_VERSION_INFO = new HashSet<>(Arrays.asList(
            "indexFormat", "blackLabBuildTime", "blackLabVersion", "timeCreated",
            "timeModified", "alwaysAddClosingToken", "tagLengthInPayload"));

    /** What keys may occur under fieldInfo? */
    private static final Set<String> KEYS_FIELD_INFO = new HashSet<>(Arrays.asList(
            "namingScheme", "unknownCondition", "unknownValue",
            "metadataFields", "complexFields", "metadataFieldGroups", "annotationGroups",
            "defaultAnalyzer", "titleField", "authorField", "dateField", "pidField"));

    /** What keys may occur under metadataFieldGroups group? */
    private static final Set<String> KEYS_METADATA_GROUP = new HashSet<>(Arrays.asList(
            "name", "fields", "addRemainingFields"));

    /** What keys may occur under annotationGroups group? */
    private static final Set<String> KEYS_ANNOTATION_GROUP = new HashSet<>(Arrays.asList(
            "name", "annotations", "addRemainingAnnotations"));

    /** What keys may occur under metadata field config? */
    private static final Set<String> KEYS_META_FIELD_CONFIG = new HashSet<>(Arrays.asList(
            "type", "displayName", "uiType",
            "description", "group", "analyzer",
            "unknownValue", "unknownCondition", "values",
            "displayValues", "displayOrder", "valueListComplete"));

    /** What keys may occur under annotated field config? */
    private static final Set<String> KEYS_ANNOTATED_FIELD_CONFIG = new HashSet<>(Arrays.asList(
            "displayName", "description", "mainProperty",
            "noForwardIndexProps", "displayOrder", "annotations"));

    /** Our index */
    protected final BlackLabIndex index;

    /** Index display name */
    protected String displayName;

    /** Index description */
    private String description;

    /** Custom properties */
    private final CustomProps customProps;

    /** When BlackLab.jar was built */
    private String blackLabBuildTime;

    /** BlackLab version used to (initially) create index */
    private String blackLabVersion;

    /** Format the index uses */
    private String indexFormat;

    /** Time at which index was created */
    private String timeCreated;

    /** Time at which index was created */
    private String timeModified;

    /**
     * May all users freely retrieve the full content of documents, or is that
     * restricted?
     */
    private boolean contentViewable = false;

    /** Text direction for this corpus */
    private TextDirection textDirection = TextDirection.LEFT_TO_RIGHT;

    /**
     * Indication of the document format(s) in this index.
     *
     * This is in the form of a format identifier as understood by the
     * DocumentFormats class (either an abbreviation or a (qualified) class name).
     */
    private String documentFormat;

    protected long tokenCount = 0;

    /** Our metadata fields */
    protected final MetadataFieldsImpl metadataFields;

    /** Our annotated fields */
    protected final AnnotatedFieldsImpl annotatedFields = new AnnotatedFieldsImpl();

    /** Is this instance frozen, that is, are all mutations disallowed? */
    private boolean frozen;

    public IndexMetadataAbstract(BlackLabIndex index) {
        this.index = index;
        metadataFields = new MetadataFieldsImpl(createMetadataFieldValuesFactory());
        customProps = new CustomPropsDelegateCorpus(this);
    }

    // Methods that read data
    // ------------------------------------------------------------------------------

    protected ObjectNode createEmptyIndexMetadata() {
        ObjectMapper mapper = Json.getJsonObjectMapper();
        ObjectNode jsonRoot = mapper.createObjectNode();
        jsonRoot.put("displayName", IndexMetadata.indexNameFromDirectory(index.indexDirectory()));
        jsonRoot.put("description", "");
        addVersionInfo(jsonRoot);
        ObjectNode fieldInfo = jsonRoot.putObject("fieldInfo");
        fieldInfo.putObject("metadataFields");
        fieldInfo.putObject("complexFields");
        return jsonRoot;
    }

    protected ObjectNode createIndexMetadataFromConfig(ConfigInputFormat config) {
        ConfigCorpus corpusConfig = config.getCorpusConfig();
        ObjectMapper mapper = Json.getJsonObjectMapper();
        ObjectNode jsonRoot = mapper.createObjectNode();
        String displayName = corpusConfig.getDisplayName();
        if (displayName.isEmpty())
            displayName = IndexMetadata.indexNameFromDirectory(index.indexDirectory());
        jsonRoot.put("displayName", displayName);
        jsonRoot.put("description", corpusConfig.getDescription());
        jsonRoot.put("contentViewable", corpusConfig.isContentViewable());
        jsonRoot.put("textDirection", corpusConfig.getTextDirection().getCode());
        jsonRoot.put("documentFormat", config.getName());
        addVersionInfo(jsonRoot);
        ObjectNode fieldInfo = jsonRoot.putObject("fieldInfo");
        fieldInfo.put("defaultAnalyzer", config.getMetadataDefaultAnalyzer());
        fieldInfo.put("unknownCondition", config.getMetadataDefaultUnknownCondition().stringValue());
        fieldInfo.put("unknownValue", config.getMetadataDefaultUnknownValue());
        for (Entry<String, String> e: corpusConfig.getSpecialFields().entrySet()) {
            fieldInfo.put(e.getKey(), e.getValue());
        }
        ArrayNode metaGroups = fieldInfo.putArray("metadataFieldGroups");
        ObjectNode annotGroups = fieldInfo.putObject("annotationGroups");
        ObjectNode metadata = fieldInfo.putObject("metadataFields");
        ObjectNode annotated = fieldInfo.putObject("complexFields");

        addFieldInfoFromConfig(metadata, annotated, metaGroups, annotGroups, config);
        return jsonRoot;
    }

    /**
     * Encode the index structure to an (in-memory) JSON structure.
     *
     * @return json structure
     */
    public ObjectNode encodeToJson() {
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
        // Indicates that we always index words+1 tokens (last token is
        // for XML tags after the last word)
        versionInfo.put("alwaysAddClosingToken", true);
        // Indicates that start tag annotation payload contains tag lengths,
        // and there is no end tag annotation
        versionInfo.put("tagLengthInPayload", true);

        ObjectNode fieldInfo = jsonRoot.putObject("fieldInfo");
        fieldInfo.put("namingScheme", "DEFAULT");
        fieldInfo.put("defaultAnalyzer", metadataFields.defaultAnalyzerName());
        fieldInfo.put("unknownCondition", metadataFields.defaultUnknownCondition());
        fieldInfo.put("unknownValue", metadataFields.defaultUnknownValue());
        if (metadataFields.titleField() != null)
            fieldInfo.put("titleField", metadataFields.titleField().name());
        if (metadataFields.authorField() != null)
            fieldInfo.put("authorField", metadataFields.authorField().name());
        if (metadataFields.dateField() != null)
            fieldInfo.put("dateField", metadataFields.dateField().name());
        if (metadataFields.pidField() != null)
            fieldInfo.put("pidField", metadataFields.pidField().name());
        ArrayNode metadataFieldGroups = fieldInfo.putArray("metadataFieldGroups");
        ObjectNode annotationGroups = fieldInfo.putObject("annotationGroups");
        ObjectNode metadataFields = fieldInfo.putObject("metadataFields");
        ObjectNode jsonAnnotatedFields = fieldInfo.putObject("complexFields");

        // Add metadata field group info
        for (MetadataFieldGroup g: metadataFields().groups()) {
            ObjectNode group = metadataFieldGroups.addObject();
            group.put("name", g.name());
            if (g.addRemainingFields())
                group.put("addRemainingFields", true);
            ArrayNode arr = group.putArray("fields");
            Json.arrayOfStrings(arr, g.stream().map(Field::name).collect(Collectors.toList()));
        }
        // Add annotation group info
        for (AnnotatedField f: annotatedFields()) {
            AnnotationGroups groups = annotatedFields().annotationGroups(f.name());
            if (groups != null) {
                ArrayNode jsonGroups = annotationGroups.putArray(f.name());
                for (AnnotationGroup g: groups) {
                    ObjectNode jsonGroup = jsonGroups.addObject();
                    jsonGroup.put("name", g.groupName());
                    if (g.addRemainingAnnotations())
                        jsonGroup.put("addRemainingAnnotations", true);
                    ArrayNode arr = jsonGroup.putArray("annotations");
                    Json.arrayOfStrings(arr, g.annotations().stream().map(Annotation::name).collect(Collectors.toList()));
                }
            }
        }

        // Add metadata field info
        for (MetadataField f: this.metadataFields) {
            UnknownCondition unknownCondition = f.unknownCondition();
            ObjectNode fi = metadataFields.putObject(f.name());
            fi.put("displayName", f.displayName());
            fi.put("uiType", f.uiType());
            fi.put("description", f.description());
            fi.put("type", f.type().stringValue());
            fi.put("analyzer", f.analyzerName());
            fi.put("unknownValue", f.unknownValue());
            fi.put("unknownCondition", unknownCondition.toString());
            if (f.isValueListComplete() != ValueListComplete.UNKNOWN)
                fi.put("valueListComplete", f.isValueListComplete().equals(ValueListComplete.YES));
            Map<String, Integer> values = f.valueDistribution();
            if (values != null) {
                ObjectNode jsonValues = fi.putObject("values");
                for (Map.Entry<String, Integer> e: values.entrySet()) {
                    jsonValues.put(e.getKey(), e.getValue());
                }
            }
            Map<String, String> displayValues = f.displayValues();
            if (displayValues != null) {
                ObjectNode jsonDisplayValues = fi.putObject("displayValues");
                for (Map.Entry<String, String> e: displayValues.entrySet()) {
                    jsonDisplayValues.put(e.getKey(), e.getValue());
                }
            }
            List<String> displayOrder = f.displayOrder();
            if (displayOrder != null) {
                ArrayNode jsonDisplayValues = fi.putArray("displayOrder");
                for (String value: displayOrder) {
                    jsonDisplayValues.add(value);
                }
            }
        }

        // Add annotated field info
        for (AnnotatedField f: annotatedFields) {
            ObjectNode fieldInfo2 = jsonAnnotatedFields.putObject(f.name());
            fieldInfo2.put("displayName", f.displayName());
            fieldInfo2.put("description", f.description());
            if (f.mainAnnotation() != null)
                fieldInfo2.put("mainProperty", f.mainAnnotation().name());
            ArrayNode arr = fieldInfo2.putArray("displayOrder");
            Json.arrayOfStrings(arr, ((AnnotatedFieldImpl) f).getDisplayOrder());
            ArrayNode annots = fieldInfo2.putArray("annotations");
            for (Annotation annotation: f.annotations()) {
                ObjectNode annot = annots.addObject();
                annot.put("name", annotation.name());
                annot.put("displayName", annotation.displayName());
                annot.put("description", annotation.description());
                annot.put("uiType", annotation.uiType());
                annot.put("isInternal", annotation.isInternal());
                if (annotation.subannotationNames().size() > 0) {
                    ArrayNode subannots = annot.putArray("subannotations");
                    for (String subannotName: annotation.subannotationNames()) {
                        subannots.add(subannotName);
                    }
                }
            }
        }

        return jsonRoot;

    }

    @Override
    public AnnotatedFields annotatedFields() {
        return annotatedFields;
    }

    /**
     * Detect type by finding the first document that includes this field and
     * inspecting the Fieldable. This assumes that the field type is the same for
     * all documents.
     *
     * @param fieldName the field name to determine the type for
     * @return type of the field (text or numeric)
     */
    private static FieldType getFieldType(String fieldName) {

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
        if (fieldName.endsWith("Numeric") || fieldName.endsWith("Num") || fieldName.equals("metadataCid"))
            type = FieldType.NUMERIC;
        return type;
    }

    @Override
    public MetadataFieldsWriter metadataFields() {
        return metadataFields;
    }

    /**
     * Get a description of the index, if specified
     *
     * @return the description
     */
    @Override
    public String description() {
        return description;
    }

    /**
     * Is the content freely viewable by all users, or is it restricted?
     *
     * @return true if the full content may be retrieved by anyone
     */
    @Override
    public boolean contentViewable() {
        return contentViewable;
    }

    /**
     * What's the text direction of this corpus?
     *
     * @return text direction
     */
    @Override
    public TextDirection textDirection() {
        return textDirection;
    }

    /**
     * What format(s) is/are the documents in?
     *
     * This is in the form of a format identifier as understood by the
     * DocumentFormats class (either an abbreviation or a (qualified) class name).
     *
     * @return the document format(s)
     */
    @Override
    public String documentFormat() {
        return documentFormat;
    }

    /**
     * What version of the index format is this?
     *
     * @return the index format version
     */
    @Override
    public String indexFormat() {
        return indexFormat;
    }

    /**
     * When was this index created?
     *
     * @return date/time stamp
     */
    @Override
    public String timeCreated() {
        return timeCreated;
    }

    /**
     * When was this index last modified?
     *
     * @return date/time stamp
     */
    @Override
    public String timeModified() {
        return timeCreated;
    }

    /**
     * When was the BlackLab.jar used for indexing built?
     *
     * @return date/time stamp
     */
    @Override
    public String indexBlackLabBuildTime() {
        return blackLabBuildTime;
    }

    /**
     * When was the BlackLab.jar used for indexing built?
     *
     * @return date/time stamp
     */
    @Override
    public String indexBlackLabVersion() {
        return blackLabVersion;
    }

    /**
     * Is this a new, empty index?
     *
     * An empty index is one that doesn't have a main contents field yet, or has a
     * main contents field but no indexed tokens yet.
     *
     * @return true if it is, false if not.
     */
    @Override
    public boolean isNewIndex() {
        return annotatedFields.main() == null || tokenCount == 0;
    }

    @Override
    public long tokenCount() {
        return tokenCount;
    }

    // Methods that mutate data
    // ------------------------------------

    /**
     * Extract the index structure from the (in-memory) JSON structure (and Lucene
     * index).
     *
     * Looks at the Lucene index to detect certain information (sometimes) missing
     * from the JSON structure, such as naming scheme and available annotations and
     * alternatives for annotated fields. (Should probably eventually all be recorded
     * in the metadata.)
     *
     * @param jsonRoot JSON structure to extract
     * @param reader index reader used to detect certain information, or null if we
     *            don't have an index reader (e.g. because we're creating a new
     *            index)
     * @param usedTemplate whether the JSON structure was read from a indextemplate
     *            file. If so, clear certain parts of it that aren't relevant
     *            anymore.
     * @throws IndexVersionMismatch if the index is too old or too new to open with this BlackLab version
     */
    protected void extractFromJson(ObjectNode jsonRoot, IndexReader reader, boolean usedTemplate) throws IndexVersionMismatch {
        ensureNotFrozen();

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
        blackLabBuildTime = Json.getString(versionInfo, "blackLabBuildTime", "UNKNOWN");
        blackLabVersion = Json.getString(versionInfo, "blackLabVersion", "UNKNOWN");
        timeCreated = Json.getString(versionInfo, "timeCreated", "");
        timeModified = Json.getString(versionInfo, "timeModified", timeCreated);
        boolean alwaysHasClosingToken = Json.getBoolean(versionInfo, "alwaysAddClosingToken", false);
        if (!alwaysHasClosingToken)
            throw new IndexVersionMismatch(
                    "Your index is too old (alwaysAddClosingToken == false). Please use v1.7.1 or re-index your data.");
        boolean tagLengthInPayload = Json.getBoolean(versionInfo, "tagLengthInPayload", false);
        if (!tagLengthInPayload) {
            logger.warn(
                    "Your index is too old (tagLengthInPayload == false). Searches using XML elements like <s> may not work correctly. If this is a problem, please use v1.7.1 or re-index your data.");
            //throw new IndexVersionMismatch("Your index is too old (tagLengthInPayload == false). Please use v1.7.1 or re-index your data.");
        }

        // Specified in index metadata file?
        ObjectNode fieldInfo = Json.getObject(jsonRoot, "fieldInfo");
        warnUnknownKeys("in fieldInfo", fieldInfo, KEYS_FIELD_INFO);
        metadataFields.setDefaultUnknownCondition(Json.getString(fieldInfo, "unknownCondition", "NEVER"));
        metadataFields.setDefaultUnknownValue(Json.getString(fieldInfo, "unknownValue", "unknown"));

        ObjectNode metaFieldConfigs = Json.getObject(fieldInfo, "metadataFields");
        boolean hasMetaFields = metaFieldConfigs.size() > 0;
        ObjectNode annotatedFieldConfigs = Json.getObject(fieldInfo, "complexFields");
        boolean hasAnnotatedFields = annotatedFieldConfigs.size() > 0;
        boolean hasFieldInfo = hasMetaFields || hasAnnotatedFields;

        if (hasFieldInfo && fieldInfo.has("annotationGroups")) {
            annotatedFields.clearAnnotationGroups();
            JsonNode groupingsPerField = fieldInfo.get("annotationGroups");
            Iterator<Entry<String, JsonNode>> it = groupingsPerField.fields();
            while (it.hasNext()) {
                Entry<String, JsonNode> entry = it.next();
                String fieldName = entry.getKey();
                JsonNode groups = entry.getValue();
                List<AnnotationGroup> annotationGroups = new ArrayList<>();
                for (int i = 0; i < groups.size(); i++) {
                    JsonNode group = groups.get(i);
                    warnUnknownKeys("in annotation group", group, KEYS_ANNOTATION_GROUP);
                    String groupName = Json.getString(group, "name", "UNKNOWN");
                    List<String> annotations = Json.getListOfStrings(group, "annotations");
                    boolean addRemainingAnnotations = Json.getBoolean(group, "addRemainingAnnotations", false);
                    annotationGroups.add(new AnnotationGroup(annotatedFields, fieldName, groupName, annotations,
                            addRemainingAnnotations));
                }
                annotatedFields.putAnnotationGroups(fieldName, new AnnotationGroups(fieldName, annotationGroups));
            }
        }
        if (hasFieldInfo && fieldInfo.has("metadataFieldGroups")) {
            Map<String, MetadataFieldGroupImpl> groupMap = new LinkedHashMap<>();
            JsonNode groups = fieldInfo.get("metadataFieldGroups");
            for (int i = 0; i < groups.size(); i++) {
                JsonNode group = groups.get(i);
                warnUnknownKeys("in metadataFieldGroup", group, KEYS_METADATA_GROUP);
                String name = Json.getString(group, "name", "UNKNOWN");
                List<String> fields = Json.getListOfStrings(group, "fields");
                for (String f: fields) {
                    // Ensure field exists
                    metadataFields().register(f);
                }
                boolean addRemainingFields = Json.getBoolean(group, "addRemainingFields", false);
                MetadataFieldGroupImpl metadataGroup = new MetadataFieldGroupImpl(metadataFields(), name, fields,
                        addRemainingFields);
                groupMap.put(name, metadataGroup);
            }
            metadataFields.setMetadataGroups(groupMap);
        }
        if (hasFieldInfo) {
            // Metadata fields
            Iterator<Entry<String, JsonNode>> it = metaFieldConfigs.fields();
            while (it.hasNext()) {
                Entry<String, JsonNode> entry = it.next();
                String fieldName = entry.getKey();
                JsonNode fieldConfig = entry.getValue();
                warnUnknownKeys("in metadata field config for '" + fieldName + "'", fieldConfig,
                        KEYS_META_FIELD_CONFIG);
                FieldType fieldType = FieldType.fromStringValue(Json.getString(fieldConfig, "type", "tokenized"));
                MetadataFieldImpl fieldDesc = new MetadataFieldImpl(fieldName, fieldType, metadataFields.getMetadataFieldValuesFactory());
                fieldDesc.setDisplayName(Json.getString(fieldConfig, "displayName", fieldName));
                fieldDesc.setUiType(Json.getString(fieldConfig, "uiType", ""));
                fieldDesc.setDescription(Json.getString(fieldConfig, "description", ""));
                //fieldDesc.setGroup(Json.getString(fieldConfig, "group", ""));
                fieldDesc.setAnalyzer(Json.getString(fieldConfig, "analyzer", "DEFAULT"));
                fieldDesc.setUnknownValue(
                        Json.getString(fieldConfig, "unknownValue", metadataFields.defaultUnknownValue()));
                UnknownCondition unk = UnknownCondition
                        .fromStringValue(Json.getString(fieldConfig, "unknownCondition",
                                metadataFields.defaultUnknownCondition()));
                fieldDesc.setUnknownCondition(unk);
                if (fieldConfig.has("values"))
                    fieldDesc.setValues(fieldConfig.get("values"));
                if (fieldConfig.has("displayValues"))
                    fieldDesc.setDisplayValues(fieldConfig.get("displayValues"));
                if (fieldConfig.has("displayOrder"))
                    fieldDesc.setDisplayOrder(Json.getListOfStrings(fieldConfig, "displayOrder"));
                if (fieldConfig.has("valueListComplete"))
                    fieldDesc.setValueListComplete(Json.getBoolean(fieldConfig, "valueListComplete", false));
                metadataFields.put(fieldName, fieldDesc);
            }

            // Annotated fields
            it = annotatedFieldConfigs.fields();
            while (it.hasNext()) {
                Entry<String, JsonNode> entry = it.next();
                String fieldName = entry.getKey();
                JsonNode fieldConfig = entry.getValue();
                warnUnknownKeys("in annotated field config for '" + fieldName + "'", fieldConfig,
                        KEYS_ANNOTATED_FIELD_CONFIG);
                AnnotatedFieldImpl fieldDesc = new AnnotatedFieldImpl(fieldName);
                fieldDesc.setDisplayName(Json.getString(fieldConfig, "displayName", fieldName));
                fieldDesc.setDescription(Json.getString(fieldConfig, "description", ""));
                String mainAnnotationName = Json.getString(fieldConfig, "mainProperty", "");
                if (mainAnnotationName.length() > 0)
                    fieldDesc.setMainAnnotationName(mainAnnotationName);

                // Process information about annotations (displayName, uiType, etc.
                ArrayList<String> annotationOrder = new ArrayList<>();
                if (fieldConfig.has("annotations")) {
                    JsonNode annotations = fieldConfig.get("annotations");
                    Iterator<JsonNode> itAnnot = annotations.elements();
                    while (itAnnot.hasNext()) {
                        JsonNode jsonAnnotation = itAnnot.next();
                        Iterator<Entry<String, JsonNode>> itAnnotOpt = jsonAnnotation.fields();
                        AnnotationImpl annotation = new AnnotationImpl(fieldDesc);
                        while (itAnnotOpt.hasNext()) {
                            Entry<String, JsonNode> opt = itAnnotOpt.next();
                            switch (opt.getKey()) {
                            case "name":
                                annotation.setName(opt.getValue().textValue());
                                annotationOrder.add(opt.getValue().textValue());
                                break;
                            case "displayName":
                                annotation.setDisplayName(opt.getValue().textValue());
                                break;
                            case "description":
                                annotation.setDescription(opt.getValue().textValue());
                                break;
                            case "uiType":
                                annotation.setUiType(opt.getValue().textValue());
                                break;
                            case "isInternal":
                                if (opt.getValue().booleanValue())
                                    annotation.setInternal();
                                break;
                            case "subannotations":
                                annotation.setSubannotationNames(Json.getListOfStrings(jsonAnnotation, "subannotations"));
                                break;
                            case "hasForwardIndex":
                                // (used to be detected, now stored in metadata)
                                boolean hasForwardIndex = opt.getValue().booleanValue();
                                annotation.setForwardIndex(hasForwardIndex);
                                break;
                            default:
                                logger.warn("Unknown key " + opt.getKey() + " in annotation for field '" + fieldName
                                        + "' in indexmetadata file");
                                break;
                            }
                        }
                        if (StringUtils.isEmpty(annotation.name()))
                            logger.warn("Annotation entry without name for field '" + fieldName
                                    + "' in indexmetadata file; skipping");
                        else
                            fieldDesc.putAnnotation(annotation);
                    }
                }

                // These annotations should get no forward index
                // TODO: refactor this so this information is stored with each annotation instead,
                // deprecating this setting
                JsonNode nodeNoForwardIndexAnnotations = fieldConfig.get("noForwardIndexProps");
                if (nodeNoForwardIndexAnnotations instanceof ArrayNode) {
                    Iterator<JsonNode> itNFIP = nodeNoForwardIndexAnnotations.elements();
                    Set<String> noForwardIndex = new HashSet<>();
                    while (itNFIP.hasNext()) {
                        noForwardIndex.add(itNFIP.next().asText());
                    }
                    fieldDesc.setNoForwardIndexAnnotations(noForwardIndex);
                } else {
                    String noForwardIndex = Json.getString(fieldConfig, "noForwardIndexProps", "").trim();
                    if (noForwardIndex.length() > 0) {
                        String[] noForwardIndexAnnotations = noForwardIndex.split("\\s+");
                        fieldDesc.setNoForwardIndexAnnotations(new HashSet<>(Arrays.asList(noForwardIndexAnnotations)));
                    }
                }

                // This is the "natural order" of our annotations
                // (probably not needed anymore - if not specified, the order of the annotations
                // will be used)
                List<String> displayOrder = Json.getListOfStrings(fieldConfig, "displayOrder");
                if (displayOrder.isEmpty()) {
                    displayOrder.addAll(annotationOrder);
                }
                fieldDesc.setDisplayOrder(displayOrder);

                annotatedFields.put(fieldName, fieldDesc);
            }
        }
        FieldInfos fis = reader == null ? null : FieldInfos.getMergedFieldInfos(reader);
        if (fis != null) {
            // Detect fields
            for (FieldInfo fi: fis) {
            //for (int i = 0; i < fis.size(); i++) {
                //FieldInfo fi = fis.fieldInfo(i);
                String name = fi.name;

                // Parse the name to see if it is a metadata field or part of an annotated field.
                String[] parts;
                if (name.endsWith("Numeric")) {
                    // Special case: this is not a annotation alternative, but a numeric
                    // alternative for a metadata field.
                    // (TODO: this should probably be changed or removed)
                    parts = new String[] { name };
                } else {
                    parts = AnnotatedFieldNameUtil.getNameComponents(name);
                }
                if (parts.length == 1 && !annotatedFields.exists(parts[0])) {
                    if (!metadataFields.exists(name)) {
                        // Metadata field, not found in metadata JSON file
                        FieldType type = getFieldType(name);
                        MetadataFieldImpl metadataFieldDesc = new MetadataFieldImpl(name, type, metadataFields.getMetadataFieldValuesFactory());
                        metadataFieldDesc
                                .setUnknownCondition(
                                        UnknownCondition.fromStringValue(metadataFields.defaultUnknownCondition()));
                        metadataFieldDesc.setUnknownValue(metadataFields.defaultUnknownValue());
                        metadataFieldDesc.setDocValuesType(fi.getDocValuesType());
                        metadataFields.put(name, metadataFieldDesc);
                    }
                } else {
                    // Part of annotated field.
                    if (metadataFields.exists(parts[0])) {
                        throw new BlackLabRuntimeException(
                                "Annotated field and metadata field with same name, error! ("
                                        + parts[0] + ")");
                    }

                    // Get or create descriptor object.
                    AnnotatedFieldImpl cfd = getOrCreateAnnotatedField(parts[0]);
                    cfd.processIndexField(parts, fi);
                }
            } // even if we have metadata, we still have to detect annotations/sensitivities
        }

        // Link subannotations to their parent annotation
        for (AnnotatedField f: annotatedFields) {
            Annotations annot = f.annotations();
            for (Annotation a: annot) {
                if (a.subannotationNames().size() > 0) {
                    // Link these subannotations back to their parent annotation
                    for (String name: a.subannotationNames()) {
                        annot.get(name).setSubAnnotation(a);
                    }
                }
            }
        }

        metadataFields.setDefaultAnalyzer(Json.getString(fieldInfo, "defaultAnalyzer", "DEFAULT"));

        metadataFields.clearSpecialFields();
        if (fieldInfo.has("authorField"))
            metadataFields.setSpecialField(MetadataFields.AUTHOR, fieldInfo.get("authorField").textValue());
        if (fieldInfo.has("dateField"))
            metadataFields.setSpecialField(MetadataFields.DATE, fieldInfo.get("dateField").textValue());
        if (fieldInfo.has("pidField"))
            metadataFields.setSpecialField(MetadataFields.PID, fieldInfo.get("pidField").textValue());
        if (fieldInfo.has("titleField"))
            metadataFields.setSpecialField(MetadataFields.TITLE, fieldInfo.get("titleField").textValue());
        if (metadataFields.titleField() == null) {
            if (metadataFields.pidField() != null)
                metadataFields.setSpecialField(MetadataFields.TITLE, metadataFields.pidField().name());
            else
                metadataFields.setSpecialField(MetadataFields.TITLE, "fromInputFile");
        }

        if (usedTemplate) {
            // Update / clear possible old values that were in the template file
            // (template file may simply be the metadata file copied from a previous
            // version)

            // Reset version info
            blackLabBuildTime = BlackLab.buildTime();
            blackLabVersion = BlackLab.version();
            indexFormat = getLatestIndexFormat();
            timeModified = timeCreated = TimeUtil.timestamp();

            // Clear any recorded values in metadata fields
            metadataFields.resetForIndexing();
        }
    }

    /**
     * Return a factory that creates a MetadataFieldValues object.
     *
     * Will either create such an object that uses the indexmetadata file
     * to manage metadata field values, or one that determines the values
     * from the Lucene index directly, using DocValues.
     *
     * @return factory
     */
    protected abstract MetadataFieldValues.Factory createMetadataFieldValuesFactory();

    protected abstract String getLatestIndexFormat();

    private synchronized AnnotatedFieldImpl getOrCreateAnnotatedField(String name) {
        ensureNotFrozen();
        AnnotatedFieldImpl cfd = null;
        if (annotatedFields.exists(name))
            cfd = ((AnnotatedFieldImpl) annotatedField(name));
        if (cfd == null) {
            cfd = new AnnotatedFieldImpl(name);
            annotatedFields.put(name, cfd);
        }
        return cfd;
    }

    /**
     * Indicate that the index was modified, so that fact will be recorded in the
     * metadata file.
     */
    @Override
    public void updateLastModified() {
        ensureNotFrozen();
        timeModified = TimeUtil.timestamp();
    }

    /**
     * While indexing, check if an annotated field is already registered in the
     * metadata, and if not, add it now.
     *
     * @param fieldWriter field to register
     * @return registered annotated field
     */
    @Override
    public synchronized AnnotatedField registerAnnotatedField(AnnotatedFieldWriter fieldWriter) {
        String fieldName = fieldWriter.name();
        AnnotatedFieldImpl cf;
        if (annotatedFields.exists(fieldName)) {
            cf = annotatedFields.get(fieldName);
        } else {
            ensureNotFrozen();

            // Not registered yet; do so now. Note that we only add the main annotation,
            // not the other annotations, but that's okay; they're not needed at index
            // time and will be detected at search time.
            cf = getOrCreateAnnotatedField(fieldName);
        }

        // Make sure all the annotations, their sensitivities, the offset sensitivity, whether
        // they have a forward index, and the main annotation are all registered correctly.
        for (AnnotationWriter annotationWriter: fieldWriter.annotationWriters()) {
            AnnotationImpl annotation = cf.getOrCreateAnnotation(annotationWriter.name());
            for (String suffix: annotationWriter.sensitivitySuffixes()) {
                annotation.addAlternative(MatchSensitivity.fromLuceneFieldSuffix(suffix));
            }
            if (annotationWriter.includeOffsets())
                annotation.setOffsetsMatchSensitivity(MatchSensitivity.fromLuceneFieldSuffix(annotationWriter.mainSensitivity()));
            annotation.setForwardIndex(annotationWriter.hasForwardIndex());
            annotationWriter.setAnnotation(annotation);
        }
        String mainAnnotName = fieldWriter.mainAnnotation().name();
        cf.getOrCreateAnnotation(mainAnnotName); // create main annotation
        cf.setMainAnnotationName(mainAnnotName); // set main annotation
        fieldWriter.setAnnotatedField(cf);
        return cf;
    }

    @Override
    public synchronized MetadataField registerMetadataField(String fieldName) {
        return metadataFields.register(fieldName);
    }

    /**
     * Set the display name for this index. Only makes sense in index mode where the
     * change will be saved. Usually called when creating an index.
     *
     * @param displayName the display name to set.
     */
    @Override
    public void setDisplayName(String displayName) {
        ensureNotFrozen();
        if (displayName.length() > 80)
            displayName = StringUtils.abbreviate(displayName, 75);
        this.displayName = displayName;
    }

    /**
     * Set a document format (or formats) for this index.
     *
     * This should be a format identifier as understood by the DocumentFormats class
     * (either an abbreviation or a (qualified) class name).
     *
     * It only makes sense to call this in index mode, where this change will be
     * saved.
     *
     * @param documentFormat the document format to store
     */
    @Override
    public void setDocumentFormat(String documentFormat) {
        ensureNotFrozen();
        this.documentFormat = documentFormat;
    }

    @Override
    public void addToTokenCount(long tokensProcessed) {
        ensureNotFrozen();
        tokenCount += tokensProcessed;
    }

    /**
     * Used when creating an index to initialize contentViewable setting. Do not use
     * otherwise.
     *
     * It is also used to support a deprecated configuration setting in BlackLab
     * Server, but this use will eventually be removed.
     *
     * @param contentViewable whether content may be freely viewed
     */
    @Override
    public void setContentViewable(boolean contentViewable) {
        ensureNotFrozen();
        this.contentViewable = contentViewable;
    }

    /**
     * Used when creating an index to initialize textDirection setting. Do not use
     * otherwise.
     *
     * @param textDirection text direction
     */
    @Override
    public void setTextDirection(TextDirection textDirection) {
        ensureNotFrozen();
        this.textDirection = textDirection;
    }

    /**
     * If the object node contains any keys other than those specified, warn about
     * it
     *
     * @param where where are we in the file (e.g. "top level", "annotated field
     *            'contents'", etc.)
     * @param node node to check
     * @param knownKeys keys that may occur under this node
     */
    private static void warnUnknownKeys(String where, JsonNode node, Set<String> knownKeys) {
        Iterator<Entry<String, JsonNode>> it = node.fields();
        while (it.hasNext()) {
            Entry<String, JsonNode> e = it.next();
            String key = e.getKey();
            if (!knownKeys.contains(key))
                logger.warn("Unknown key " + key + " " + where + " in indexmetadata file");
        }
    }

    protected void addVersionInfo(ObjectNode jsonRoot) {
        ObjectNode versionInfo = jsonRoot.putObject("versionInfo");
        versionInfo.put("blackLabBuildTime", BlackLab.buildTime());
        versionInfo.put("blackLabVersion", BlackLab.version());
        versionInfo.put("timeCreated", TimeUtil.timestamp());
        versionInfo.put("timeModified", TimeUtil.timestamp());
        versionInfo.put("indexFormat", getLatestIndexFormat());
        versionInfo.put("alwaysAddClosingToken", true); // always true, but BL check for it, so required
        versionInfo.put("tagLengthInPayload", true); // always true, but BL check for it, so required
    }

    protected void addFieldInfoFromConfig(ObjectNode metadata, ObjectNode annotated, ArrayNode metaGroups,
            ObjectNode annotGroupsPerField, ConfigInputFormat config) {
        ensureNotFrozen();

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

        // Add annotated field annotation groupings info
        for (ConfigAnnotationGroups cfgField: corpusConfig.getAnnotationGroups().values()) {
            ArrayNode annotGroups = annotGroupsPerField.putArray(cfgField.getName());
            if (cfgField.getGroups().size() > 0) {
                for (ConfigAnnotationGroup cfgAnnotGroup: cfgField.getGroups()) {
                    ObjectNode annotGroup = annotGroups.addObject();
                    annotGroup.put("name", cfgAnnotGroup.getName());
                    ArrayNode annots = annotGroup.putArray("annotations");
                    for (String annot: cfgAnnotGroup.getAnnotations()) {
                        annots.add(annot);
                    }
                    if (cfgAnnotGroup.isAddRemainingAnnotations())
                        annotGroup.put("addRemainingAnnotations", true);
                }
            }
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
                g.put("unknownCondition", (f.getUnknownCondition() == null ? config.getMetadataDefaultUnknownCondition() : f.getUnknownCondition()).stringValue());
                g.put("unknownValue", f.getUnknownValue() == null ? config.getMetadataDefaultUnknownValue() : f.getUnknownValue());
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

        // Add annotated field info
        for (ConfigAnnotatedField f: config.getAnnotatedFields().values()) {
            ObjectNode g = annotated.putObject(f.getName());
            g.put("displayName", f.getDisplayName());
            g.put("description", f.getDescription());
            if (!f.getAnnotations().isEmpty())
                g.put("mainProperty", f.getAnnotations().values().iterator().next().getName());
            ArrayNode displayOrder = g.putArray("displayOrder");
            ArrayNode noForwardIndexAnnotations = g.putArray("noForwardIndexProps");
            ArrayNode annotations = g.putArray("annotations");
            for (ConfigAnnotation a: f.getAnnotations().values()) {
                addAnnotationInfo(a, displayOrder, noForwardIndexAnnotations, annotations);
            }
            for (ConfigStandoffAnnotations standoff: f.getStandoffAnnotations()) {
                for (ConfigAnnotation a: standoff.getAnnotations().values()) {
                    addAnnotationInfo(a, displayOrder, noForwardIndexAnnotations, annotations);
                }
            }
        }

        // Also (recursively) add metadata and annotated field config from any linked
        // documents
        for (ConfigLinkedDocument ld: config.getLinkedDocuments().values()) {
            Format format = DocumentFormats.getFormat(ld.getInputFormatIdentifier());
            if (format != null && format.isConfigurationBased())
                addFieldInfoFromConfig(metadata, annotated, metaGroups, annotGroupsPerField, format.getConfig());
        }
    }

    private void addAnnotationInfo(ConfigAnnotation annotation, ArrayNode displayOrder, ArrayNode noForwardIndexAnnotations,
            ArrayNode annotations) {
        ensureNotFrozen();
        String annotationName = annotation.getName();
        displayOrder.add(annotationName);
        if (!annotation.createForwardIndex())
            noForwardIndexAnnotations.add(annotationName);
        ObjectNode annotationNode = annotations.addObject();
        annotationNode.put("name", annotationName);
        annotationNode.put("displayName", annotation.getDisplayName());
        annotationNode.put("description", annotation.getDescription());
        annotationNode.put("uiType", annotation.getUiType());
        if (annotation.isInternal()) {
            annotationNode.put("isInternal", annotation.isInternal());
        }
        if (index instanceof BlackLabIndexIntegrated) {
            annotationNode.put("hasForwardIndex", annotation.createForwardIndex());
        }
        if (annotation.getSubAnnotations().size() > 0) {
            ArrayNode subannots = annotationNode.putArray("subannotations");
            for (ConfigAnnotation s: annotation.getSubAnnotations()) {
                if (!s.isForEach()) {
                    addAnnotationInfo(s, displayOrder, noForwardIndexAnnotations, annotations);
                    subannots.add(s.getName());
                }
            }
        }
    }

    @Override
    public CustomProps custom() {
        return customProps;
    }

    /**
     * Get the display name for the index.
     *
     * If no display name was specified, returns the name of the index directory.
     *
     * @return the display name
     */
    @Override
    public String displayName() {
        String dispName = "index";
        if (displayName != null && displayName.length() != 0)
            dispName = displayName;
        if (dispName.equalsIgnoreCase("index"))
            dispName = StringUtils.capitalize(index.indexDirectory().getName());
        if (dispName.equalsIgnoreCase("index"))
            dispName = StringUtils.capitalize(index.indexDirectory().getAbsoluteFile().getParentFile().getName());

        return dispName;
    }

    @Override
    public void freeze() {
        this.frozen = true;
        annotatedFields.freeze();
        metadataFields.freeze();
    }

    @Override
    public boolean isFrozen() {
        return this.frozen;
    }

    protected void detectMainAnnotation(IndexReader reader) {
        boolean shouldDetectMainAnnotatedField = annotatedFields.main() == null;

        // Detect main contents field and main annotations of annotated fields
        // Detect the main annotations for all annotated fields
        // (looks for fields with char offset information stored)
        AnnotatedFieldImpl mainAnnotatedField = null;
        for (AnnotatedField d: annotatedFields()) {
            if (mainAnnotatedField == null || d.name().equals("contents"))
                mainAnnotatedField = (AnnotatedFieldImpl) d;

            if (tokenCount() > 0) // Only detect if index is not empty
                ((AnnotatedFieldImpl) d).detectMainAnnotation(reader);
        }
        if (shouldDetectMainAnnotatedField)
            annotatedFields.setMainAnnotatedField(mainAnnotatedField);
    }

    /**
     * Custom properties at corpus level that delegates to the old methods.
     */
    @SuppressWarnings("deprecation") static
    private class CustomPropsDelegateCorpus implements CustomProps {
        private final IndexMetadata indexMetadata;

        public CustomPropsDelegateCorpus(IndexMetadata indexMetadata) {
            this.indexMetadata = indexMetadata;
        }

        @Override
        public String get(String key) {
            switch (key) {
            case "displayName":
                return indexMetadata.displayName();
            case "description":
                return indexMetadata.description();
            case "textDirection":
                return indexMetadata.textDirection().getCode();
            default:
                return null;
            }
        }

        @Override
        public Map<String, Object> asMap() {
            throw new UnsupportedOperationException("Not implemented (legacy index metadata only)");
        }
    }
}
