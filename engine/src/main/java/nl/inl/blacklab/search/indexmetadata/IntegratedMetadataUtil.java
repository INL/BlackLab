package nl.inl.blacklab.search.indexmetadata;

import java.io.File;
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
import org.apache.lucene.index.IndexReader;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import nl.inl.blacklab.index.DocIndexerFactory.Format;
import nl.inl.blacklab.index.DocumentFormats;
import nl.inl.blacklab.index.annotated.AnnotationSensitivities;
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
import nl.inl.util.Json;
import nl.inl.util.TimeUtil;

/**
 * Methods for serializing and deserializing index metadata, as well as creating the initial metadata.
 *
 * Only used for the integrated index format (newer version of the metadata structure, with slightly
 * different fields).
 */
class IntegratedMetadataUtil {

    private static final Logger logger = LogManager.getLogger(IntegratedMetadataUtil.class);

    private static final String KEY_ANNOTATED_FIELDS = "annotatedFields";

    private static final String KEY_MAIN_ANNOTATION = "mainAnnotation";

    private static final String LATEST_INDEX_FORMAT = "4";

    public static ObjectNode createIndexMetadata(ConfigInputFormat config, File dir) {
        return config == null ? createEmptyIndexMetadata(dir) : createIndexMetadataFromConfig(config,
                dir);
    }

    public static ObjectNode createEmptyIndexMetadata(File indexDirectory) {
        ObjectMapper mapper = Json.getJsonObjectMapper();
        ObjectNode jsonRoot = mapper.createObjectNode();
        jsonRoot.put("displayName", IndexMetadata.indexNameFromDirectory(indexDirectory));
        jsonRoot.put("description", "");
        createVersionInfo(jsonRoot);
        ObjectNode fieldInfo = jsonRoot.putObject("fieldInfo");
        fieldInfo.putObject("metadataFields");
        fieldInfo.putObject(KEY_ANNOTATED_FIELDS);
        return jsonRoot;
    }

    public static ObjectNode createIndexMetadataFromConfig(ConfigInputFormat config, File indexDirectory) {
        ConfigCorpus corpusConfig = config.getCorpusConfig();
        ObjectMapper mapper = Json.getJsonObjectMapper();
        ObjectNode jsonRoot = mapper.createObjectNode();
        String displayName = corpusConfig.getDisplayName();
        if (displayName.isEmpty())
            displayName = IndexMetadata.indexNameFromDirectory(indexDirectory);
        jsonRoot.put("displayName", displayName);
        jsonRoot.put("description", corpusConfig.getDescription());
        jsonRoot.put("contentViewable", corpusConfig.isContentViewable());
        jsonRoot.put("textDirection", corpusConfig.getTextDirection().getCode());
        jsonRoot.put("documentFormat", config.getName());
        createVersionInfo(jsonRoot);
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
        ObjectNode annotated = fieldInfo.putObject(KEY_ANNOTATED_FIELDS);

        addFieldInfoFromConfig(metadata, annotated, metaGroups, annotGroups, config);
        return jsonRoot;
    }

    /**
     * If the object node contains any keys other than those specified, warn about
     * it
     *
     * @param where     where are we in the file (e.g. "top level", "annotated field
     *                  'contents'", etc.)
     * @param node      node to check
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
     * @param reader   index reader used to detect certain information, or null if we
     *                 don't have an index reader (e.g. because we're creating a new
     *                 index)
     */
    public static void extractFromJson(IndexMetadataIntegrated metadata, ObjectNode jsonRoot, IndexReader reader) {
        metadata.ensureNotFrozen();

        // Read and interpret index metadata file
        extractTopLevelKeys(metadata, jsonRoot);
        extractVersionInfo(metadata, jsonRoot);

        MetadataFieldsImpl metadataFields = metadata.metadataFields();

        // Specified in index metadata file?
        ObjectNode fieldInfo = Json.getObject(jsonRoot, "fieldInfo");
        final Set<String> KEYS_FIELD_INFO = new HashSet<>(Arrays.asList(
                "unknownCondition", "unknownValue",
                "metadataFields", IntegratedMetadataUtil.KEY_ANNOTATED_FIELDS, "metadataFieldGroups",
                "annotationGroups",
                "defaultAnalyzer", "titleField", "authorField", "dateField", "pidField"));
        warnUnknownKeys("in fieldInfo", fieldInfo, KEYS_FIELD_INFO);
        metadataFields.setDefaultUnknownCondition(Json.getString(fieldInfo, "unknownCondition", "NEVER"));
        metadataFields.setDefaultUnknownValue(Json.getString(fieldInfo, "unknownValue", "unknown"));

        // Metadata fields
        ObjectNode nodeMetaFields = Json.getObject(fieldInfo, "metadataFields");
        boolean hasMetaFields = nodeMetaFields.size() > 0;
        if (hasMetaFields) {
            if (hasMetaFields && fieldInfo.has("metadataFieldGroups")) {
                Map<String, MetadataFieldGroupImpl> groupMap = new LinkedHashMap<>();
                JsonNode groups = fieldInfo.get("metadataFieldGroups");
                final Set<String> KEYS_METADATA_GROUP = new HashSet<>(Arrays.asList(
                        "name", "fields", "addRemainingFields"));
                for (int i = 0; i < groups.size(); i++) {
                    JsonNode group = groups.get(i);
                    warnUnknownKeys("in metadataFieldGroup", group, KEYS_METADATA_GROUP);
                    String name = Json.getString(group, "name", "UNKNOWN");
                    List<String> fields = Json.getListOfStrings(group, "fields");
                    for (String f: fields) {
                        // Ensure field exists
                        metadataFields.register(f);
                    }
                    boolean addRemainingFields = Json.getBoolean(group, "addRemainingFields", false);
                    MetadataFieldGroupImpl metadataGroup = new MetadataFieldGroupImpl(metadataFields, name, fields,
                            addRemainingFields);
                    groupMap.put(name, metadataGroup);
                }
                metadataFields.setMetadataGroups(groupMap);
            }

            // Metadata fields
            Iterator<Entry<String, JsonNode>> it = nodeMetaFields.fields();
            final Set<String> KEYS_META_FIELD_CONFIG = new HashSet<>(Arrays.asList(
                    "type", "displayName", "uiType",
                    "description", "group", "analyzer",
                    "unknownValue", "unknownCondition", "values",
                    "displayValues", "displayOrder", "valueListComplete"));
            while (it.hasNext()) {
                Entry<String, JsonNode> entry = it.next();
                String fieldName = entry.getKey();
                JsonNode fieldConfig = entry.getValue();
                warnUnknownKeys("in metadata field config for '" + fieldName + "'", fieldConfig,
                        KEYS_META_FIELD_CONFIG);
                FieldType fieldType = FieldType.fromStringValue(Json.getString(fieldConfig, "type", "tokenized"));
                MetadataFieldImpl fieldDesc = new MetadataFieldImpl(fieldName, fieldType,
                        metadataFields.getMetadataFieldValuesFactory());
                fieldDesc.setDisplayName(Json.getString(fieldConfig, "displayName", fieldName));
                fieldDesc.setUiType(Json.getString(fieldConfig, "uiType", ""));
                fieldDesc.setDescription(Json.getString(fieldConfig, "description", ""));
                fieldDesc.setGroup(Json.getString(fieldConfig, "group", ""));
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
        }

        // Annotated fields
        ObjectNode nodeAnnotatedFields = Json.getObject(fieldInfo, IntegratedMetadataUtil.KEY_ANNOTATED_FIELDS);
        boolean hasAnnotatedFields = nodeAnnotatedFields.size() > 0;
        AnnotatedFieldsImpl annotatedFields = metadata.annotatedFields();
        if (hasAnnotatedFields) {
            if (fieldInfo.has("annotationGroups")) {
                annotatedFields.clearAnnotationGroups();
                JsonNode groupingsPerField = fieldInfo.get("annotationGroups");
                Iterator<Entry<String, JsonNode>> it = groupingsPerField.fields();
                while (it.hasNext()) {
                    Entry<String, JsonNode> entry = it.next();
                    String fieldName = entry.getKey();
                    JsonNode groups = entry.getValue();
                    List<AnnotationGroup> annotationGroups = IntegratedMetadataUtil.extractAnnotationGroups(
                            annotatedFields,
                            fieldName, groups);
                    annotatedFields.putAnnotationGroups(fieldName, new AnnotationGroups(fieldName, annotationGroups));
                }
            }
            // Annotated fields
            Iterator<Entry<String, JsonNode>> it = nodeAnnotatedFields.fields();
            final Set<String> KEYS_ANNOTATED_FIELD_CONFIG = new HashSet<>(Arrays.asList(
                    "displayName", "description", IntegratedMetadataUtil.KEY_MAIN_ANNOTATION, "displayOrder",
                    "hasContentStore", "hasXmlTags", "annotations"));
            while (it.hasNext()) {
                Entry<String, JsonNode> entry = it.next();
                String fieldName = entry.getKey();
                JsonNode fieldConfig = entry.getValue();
                warnUnknownKeys("in annotated field config for '" + fieldName + "'", fieldConfig,
                        KEYS_ANNOTATED_FIELD_CONFIG);
                AnnotatedFieldImpl fieldDesc = new AnnotatedFieldImpl(metadata, fieldName);
                fieldDesc.setDisplayName(Json.getString(fieldConfig, "displayName", fieldName));
                fieldDesc.setDescription(Json.getString(fieldConfig, "description", ""));
                fieldDesc.setContentStore(Json.getBoolean(fieldConfig, "hasContentStore", true));
                fieldDesc.setXmlTags(Json.getBoolean(fieldConfig, "hasXmlTags", true));
                String mainAnnotationName = Json.getString(fieldConfig, IntegratedMetadataUtil.KEY_MAIN_ANNOTATION, "");
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
                        AnnotationImpl annotation = new AnnotationImpl(metadata, fieldDesc);
                        String offsetsSensitivity = "";
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
                                annotation.setSubannotationNames(
                                        Json.getListOfStrings(jsonAnnotation, "subannotations"));
                                break;
                            case "hasForwardIndex":
                                // (used to be detected, now stored in metadata)
                                boolean hasForwardIndex = opt.getValue().booleanValue();
                                annotation.setForwardIndex(hasForwardIndex);
                                break;
                            case "offsetsSensitivity":
                                offsetsSensitivity = opt.getValue().textValue();
                                break;
                            case "sensitivity":
                                // Create all the sensitivities that this annotation was indexed with
                                AnnotationSensitivities sensitivity = AnnotationSensitivities.fromStringValue(
                                        opt.getValue().textValue());
                                annotation.createSensitivities(sensitivity);
                                break;
                            default:
                                logger.warn(
                                        "Unknown key " + opt.getKey() + " in annotation for field '" + fieldName
                                                + "' in indexmetadata file");
                                break;
                            }
                        }
                        if (!StringUtils.isEmpty(offsetsSensitivity))
                            annotation.setOffsetsSensitivity(MatchSensitivity.fromLuceneFieldSuffix(offsetsSensitivity));
                        if (StringUtils.isEmpty(annotation.name())) {
                            logger.warn(
                                    "Annotation entry without name for field '" + fieldName
                                            + "' in indexmetadata file; skipping");
                        } else
                            fieldDesc.putAnnotation(annotation);
                    }
                }
                AnnotationImpl mainAnnot = (AnnotationImpl)fieldDesc.annotations().main();
                if (mainAnnot != null) {
                    MatchSensitivity offsetsSens = mainAnnot.mainSensitivity().sensitivity();
                    mainAnnot.setOffsetsSensitivity(offsetsSens);
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

        // Some additional metadata settings
        metadataFields.setDefaultAnalyzerName(Json.getString(fieldInfo, "defaultAnalyzer", "DEFAULT"));
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
    }

    private static void extractTopLevelKeys(IndexMetadataIntegrated metadata, ObjectNode jsonRoot) {
        final Set<String> KEYS_TOP_LEVEL = new HashSet<>(Arrays.asList(
                "displayName", "description", "contentViewable", "textDirection",
                "documentFormat", "tokenCount", "versionInfo", "fieldInfo"));
        warnUnknownKeys("at top-level", jsonRoot, KEYS_TOP_LEVEL);
        metadata.setDisplayName(Json.getString(jsonRoot, "displayName", ""));
        metadata.setDescription(Json.getString(jsonRoot, "description", ""));
        metadata.setContentViewable(Json.getBoolean(jsonRoot, "contentViewable", false));
        metadata.setTextDirection(TextDirection.fromCode(Json.getString(jsonRoot, "textDirection", "ltr")));
        metadata.setDocumentFormat(Json.getString(jsonRoot, "documentFormat", ""));
    }

    private static void extractVersionInfo(IndexMetadataIntegrated metadata, ObjectNode jsonRoot) {
        ObjectNode versionInfo = Json.getObject(jsonRoot, "versionInfo");
        final Set<String> KEYS_VERSION_INFO = new HashSet<>(Arrays.asList(
                "indexFormat", "blackLabBuildTime", "blackLabVersion", "timeCreated",
                "timeModified"));
        warnUnknownKeys("in versionInfo", versionInfo, KEYS_VERSION_INFO);
        metadata.setIndexFormat(Json.getString(versionInfo, "indexFormat", ""));
        metadata.setBlackLabBuildTime(Json.getString(versionInfo, "blackLabBuildTime", "UNKNOWN"));
        metadata.setBlackLabVersion(Json.getString(versionInfo, "blackLabVersion", "UNKNOWN"));
        metadata.setTimeCreated(Json.getString(versionInfo, "timeCreated", ""));
        metadata.setTimeModified(Json.getString(versionInfo, "timeModified", metadata.timeCreated()));
    }

    /**
     * Encode the index structure to an (in-memory) JSON structure.
     *
     * @return json structure
     */
    public static ObjectNode encodeToJson(IndexMetadataIntegrated metadata) {
        ObjectMapper mapper = Json.getJsonObjectMapper();
        ObjectNode jsonRoot = mapper.createObjectNode();
        jsonRoot.put("displayName", metadata.displayName());
        jsonRoot.put("description", metadata.description());
        jsonRoot.put("contentViewable", metadata.contentViewable());
        jsonRoot.put("textDirection", metadata.textDirection().getCode());
        jsonRoot.put("documentFormat", metadata.documentFormat());
        ObjectNode versionInfo = jsonRoot.putObject("versionInfo");
        versionInfo.put("blackLabBuildTime", metadata.indexBlackLabBuildTime());
        versionInfo.put("blackLabVersion", metadata.indexBlackLabVersion());
        versionInfo.put("indexFormat", metadata.indexFormat());
        versionInfo.put("timeCreated", metadata.timeCreated());
        versionInfo.put("timeModified", metadata.timeModified());

        ObjectNode fieldInfo = addFieldInfo(metadata.metadataFields(), jsonRoot);
        addMetadataGroups(metadata.metadataFields().groups(), fieldInfo);
        addAnnotationGroups(metadata.annotatedFields(), fieldInfo);
        addMetadataFields(metadata.metadataFields(), fieldInfo);
        addAnnotatedFields(metadata.annotatedFields(), fieldInfo);
        return jsonRoot;
    }

    public static void addAnnotationGroups(AnnotatedFields annotatedFields, ObjectNode fieldInfo) {
        ObjectNode nodeAnnotationGroups = fieldInfo.putObject("annotationGroups");
        for (AnnotatedField f: annotatedFields) {
            AnnotationGroups groups = annotatedFields.annotationGroups(f.name());
            if (groups != null) {
                ArrayNode jsonGroups = nodeAnnotationGroups.putArray(f.name());
                for (AnnotationGroup g: groups) {
                    ObjectNode jsonGroup = jsonGroups.addObject();
                    jsonGroup.put("name", g.groupName());
                    if (g.addRemainingAnnotations())
                        jsonGroup.put("addRemainingAnnotations", true);
                    ArrayNode arr = jsonGroup.putArray("annotations");
                    Json.arrayOfStrings(arr,
                            g.annotations().stream().map(Annotation::name).collect(Collectors.toList()));
                }
            }
        }
    }

    private static void addMetadataGroups(MetadataFieldGroups metaGroups, ObjectNode fieldInfo) {
        ArrayNode nodeMetaGroups = fieldInfo.putArray("metadataFieldGroups");
        for (MetadataFieldGroup g: metaGroups) {
            ObjectNode group = nodeMetaGroups.addObject();
            group.put("name", g.name());
            if (g.addRemainingFields())
                group.put("addRemainingFields", true);
            ArrayNode arr = group.putArray("fields");
            Json.arrayOfStrings(arr, g.stream().map(Field::name).collect(Collectors.toList()));
        }
    }

    private static ObjectNode addFieldInfo(MetadataFieldsImpl metadataFields, ObjectNode jsonRoot) {
        ObjectNode fieldInfo = jsonRoot.putObject("fieldInfo");
        fieldInfo.put("defaultAnalyzer", metadataFields.defaultAnalyzerName());
        fieldInfo.put("unknownCondition", metadataFields.defaultUnknownCondition());
        fieldInfo.put("unknownValue", metadataFields.defaultUnknownValue());
        if (metadataFields.titleField() != null)
            fieldInfo.put("titleField", metadataFields.special(MetadataFields.TITLE).name());
        if (metadataFields.authorField() != null)
            fieldInfo.put("authorField", metadataFields.special(MetadataFields.AUTHOR).name());
        if (metadataFields.dateField() != null)
            fieldInfo.put("dateField", metadataFields.special(MetadataFields.DATE).name());
        if (metadataFields.pidField() != null)
            fieldInfo.put("pidField", metadataFields.special(MetadataFields.PID).name());
        return fieldInfo;
    }

    private static void addMetadataFields(MetadataFields metaFields, ObjectNode fieldInfo) {
        ObjectNode metadataFields = fieldInfo.putObject("metadataFields");
        for (MetadataField f: metaFields) {
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
                for (Entry<String, Integer> e: values.entrySet()) {
                    jsonValues.put(e.getKey(), e.getValue());
                }
            }
            Map<String, String> displayValues = f.displayValues();
            if (displayValues != null) {
                ObjectNode jsonDisplayValues = fi.putObject("displayValues");
                for (Entry<String, String> e: displayValues.entrySet()) {
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
    }

    private static void addAnnotatedFields(AnnotatedFields annotFields, ObjectNode fieldInfo) {
        ObjectNode nodeAnnotatedFields = fieldInfo.putObject(KEY_ANNOTATED_FIELDS);
        for (AnnotatedField f: annotFields) {
            ObjectNode nodeField = nodeAnnotatedFields.putObject(f.name());
            nodeField.put("displayName", f.displayName());
            nodeField.put("description", f.description());
            if (f.mainAnnotation() != null)
                nodeField.put(KEY_MAIN_ANNOTATION, f.mainAnnotation().name());
            ArrayNode arr = nodeField.putArray("displayOrder");
            Json.arrayOfStrings(arr, ((AnnotatedFieldImpl) f).getDisplayOrder());
            nodeField.put("hasContentStore", f.hasContentStore());
            nodeField.put("hasXmlTags", f.hasXmlTags());
            ArrayNode annots = nodeField.putArray("annotations");
            for (Annotation annotation: f.annotations()) {
                ObjectNode annot = annots.addObject();
                annot.put("name", annotation.name());
                annot.put("displayName", annotation.displayName());
                annot.put("description", annotation.description());
                annot.put("uiType", annotation.uiType());
                annot.put("isInternal", annotation.isInternal());
                annot.put("hasForwardIndex", annotation.hasForwardIndex());
                if (annotation.offsetsSensitivity() != null)
                    annot.put("offsetsSensitivity", annotation.offsetsSensitivity().sensitivity().luceneFieldSuffix());
                annot.put("sensitivity", annotation.sensitivitySetting().getStringValue());
                if (annotation.subannotationNames().size() > 0) {
                    ArrayNode subannots = annot.putArray("subannotations");
                    for (String subannotName: annotation.subannotationNames()) {
                        subannots.add(subannotName);
                    }
                }
            }
        }
    }

    protected static void createVersionInfo(ObjectNode jsonRoot) {
        ObjectNode versionInfo = jsonRoot.putObject("versionInfo");
        versionInfo.put("blackLabBuildTime", BlackLab.buildTime());
        versionInfo.put("blackLabVersion", BlackLab.version());
        versionInfo.put("timeCreated", TimeUtil.timestamp());
        versionInfo.put("timeModified", TimeUtil.timestamp());
        versionInfo.put("indexFormat", LATEST_INDEX_FORMAT);
    }

    protected static void addFieldInfoFromConfig(ObjectNode metadata, ObjectNode annotated, ArrayNode metaGroups,
            ObjectNode annotGroupsPerField, ConfigInputFormat config) {
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
                g.put("unknownCondition", (f.getUnknownCondition() == null ?
                        config.getMetadataDefaultUnknownCondition() :
                        f.getUnknownCondition()).stringValue());
                g.put("unknownValue",
                        f.getUnknownValue() == null ? config.getMetadataDefaultUnknownValue() : f.getUnknownValue());
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
                g.put(KEY_MAIN_ANNOTATION, f.getAnnotations().values().iterator().next().getName());
            ArrayNode displayOrder = g.putArray("displayOrder");
            ArrayNode annotations = g.putArray("annotations");
            int n = 0;
            boolean hasOffsets;
            for (ConfigAnnotation a: f.getAnnotations().values()) {
                hasOffsets = n == 0; // first annotation gets offsets
                addAnnotationInfo(a, hasOffsets, displayOrder, annotations);
                n++;
            }
            for (ConfigStandoffAnnotations standoff: f.getStandoffAnnotations()) {
                for (ConfigAnnotation a: standoff.getAnnotations().values()) {
                    hasOffsets = n == 0; // first annotation gets offsets
                    addAnnotationInfo(a, hasOffsets, displayOrder, annotations);
                    n++;
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

    private static void addAnnotationInfo(ConfigAnnotation annotation, boolean hasOffsets, ArrayNode displayOrder, ArrayNode annotations) {
        String annotationName = annotation.getName();
        displayOrder.add(annotationName);
        ObjectNode annotationNode = annotations.addObject();
        annotationNode.put("name", annotationName);
        annotationNode.put("displayName", annotation.getDisplayName());
        annotationNode.put("description", annotation.getDescription());
        annotationNode.put("uiType", annotation.getUiType());
        if (annotation.isInternal()) {
            annotationNode.put("isInternal", annotation.isInternal());
        }
        annotationNode.put("hasForwardIndex", annotation.createForwardIndex());
        annotationNode.put("sensitivity", annotation.getSensitivitySetting().getStringValue());
        annotationNode.put("offsetsSensitivity", hasOffsets ? annotation.getMainSensitivity().luceneFieldSuffix() : "");
        if (annotation.getSubAnnotations().size() > 0) {
            ArrayNode subannots = annotationNode.putArray("subannotations");
            for (ConfigAnnotation s: annotation.getSubAnnotations()) {
                if (!s.isForEach()) {
                    addAnnotationInfo(s, false, displayOrder, annotations);
                    subannots.add(s.getName());
                }
            }
        }
    }

    public static List<AnnotationGroup> extractAnnotationGroups(AnnotatedFieldsImpl annotatedFields, String fieldName,
            JsonNode groups) {
        final Set<String> KEYS_ANNOTATION_GROUP = new HashSet<>(Arrays.asList(
                "name", "annotations", "addRemainingAnnotations"));
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
        return annotationGroups;
    }
}
