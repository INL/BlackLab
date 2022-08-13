package nl.inl.blacklab.search.indexmetadata;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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

    private static ObjectNode createEmptyIndexMetadata(File indexDirectory) {
        ObjectMapper mapper = Json.getJsonObjectMapper();
        ObjectNode jsonRoot = mapper.createObjectNode();

        ObjectNode nodeCustom = jsonRoot.putObject("custom");
        nodeCustom.put("displayName", IndexMetadata.indexNameFromDirectory(indexDirectory));
        nodeCustom.put("description", "");
        nodeCustom.put("textDirection", "ltr");

        createVersionInfo(jsonRoot);
        jsonRoot.putObject("metadataFields");
        jsonRoot.putObject(KEY_ANNOTATED_FIELDS);
        return jsonRoot;
    }

    private static ObjectNode createIndexMetadataFromConfig(ConfigInputFormat config, File indexDirectory) {
        ConfigCorpus corpusConfig = config.getCorpusConfig();
        ObjectMapper mapper = Json.getJsonObjectMapper();
        ObjectNode jsonRoot = mapper.createObjectNode();
        String displayName = corpusConfig.getDisplayName();
        if (displayName.isEmpty())
            displayName = IndexMetadata.indexNameFromDirectory(indexDirectory);

        // Top-level custom properties
        ObjectNode nodeCustom = jsonRoot.putObject("custom");
        nodeCustom.put("displayName", displayName);
        nodeCustom.put("description", corpusConfig.getDescription());
        nodeCustom.put("textDirection", corpusConfig.getTextDirection().getCode());
        for (Entry<String, String> e: corpusConfig.getSpecialFields().entrySet()) {
            if (!e.getKey().equals("pidField"))
                nodeCustom.put(e.getKey(), e.getValue());
        }
        nodeCustom.put("unknownCondition", config.getMetadataDefaultUnknownCondition().stringValue());
        nodeCustom.put("unknownValue", config.getMetadataDefaultUnknownValue());
        ArrayNode metaGroups = nodeCustom.putArray("metadataFieldGroups");
        ObjectNode annotGroups = nodeCustom.putObject("annotationGroups");
        addGroupsInfoFromConfig(metaGroups, annotGroups, config);

        jsonRoot.put("contentViewable", corpusConfig.isContentViewable());
        jsonRoot.put("documentFormat", config.getName());

        createVersionInfo(jsonRoot);

        jsonRoot.put("defaultAnalyzer", config.getMetadataDefaultAnalyzer());
        if (corpusConfig.getSpecialFields().containsKey("pidField"))
            jsonRoot.put("pidField", corpusConfig.getSpecialFields().get("pidField"));


        ObjectNode metadata = jsonRoot.putObject("metadataFields");
        ObjectNode annotated = jsonRoot.putObject(KEY_ANNOTATED_FIELDS);
        addFieldInfoFromConfig(metadata, annotated, config);

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
     * @param metadata metadata object to extract to
     * @param jsonRoot JSON structure to extract
     */
    public static void extractFromJson(IndexMetadataIntegrated metadata, ObjectNode jsonRoot) {
        metadata.ensureNotFrozen();

        MetadataFieldsImpl metadataFields = metadata.metadataFields();
        metadataFields.clearSpecialFields();

        // Read and interpret index metadata file
        extractTopLevelKeys(metadata, jsonRoot);
        extractVersionInfo(metadata, jsonRoot);


        // Metadata fields
        ObjectNode nodeMetaFields = Json.getObject(jsonRoot, "metadataFields");
        boolean hasMetaFields = nodeMetaFields.size() > 0;
        if (hasMetaFields) {

            if (metadata.custom().containsKey("metadataFieldGroups")) {
                Map<String, MetadataFieldGroupImpl> groupMap = new LinkedHashMap<>();
                List<Map<String, Object>> groups = metadata.custom().get("metadataFieldGroups", Collections.emptyList());
                for (Map<String, Object> group: groups) {
                    String name = (String)group.getOrDefault("name", "UNKNOWN");
                    List<String> fields = (List<String>)group.getOrDefault("fields", Collections.emptyList());
                    for (String f: fields) {
                        // Ensure field exists
                        metadataFields.register(f);
                    }
                    boolean addRemainingFields = (boolean)group.getOrDefault("addRemainingFields", false);
                    MetadataFieldGroupImpl metadataGroup = new MetadataFieldGroupImpl(metadataFields, name, fields,
                            addRemainingFields);
                    groupMap.put(name, metadataGroup);
                }
                metadataFields.setMetadataGroups(groupMap);
            }

            // Metadata fields
            Iterator<Entry<String, JsonNode>> it = nodeMetaFields.fields();
            final Set<String> KEYS_META_FIELD_CONFIG = new HashSet<>(Arrays.asList("type", "group", "analyzer", "custom"));
            while (it.hasNext()) {
                Entry<String, JsonNode> entry = it.next();
                String fieldName = entry.getKey();
                ObjectNode fieldConfig = (ObjectNode)entry.getValue();
                warnUnknownKeys("in metadata field config for '" + fieldName + "'", fieldConfig,
                        KEYS_META_FIELD_CONFIG);
                FieldType fieldType = FieldType.fromStringValue(Json.getString(fieldConfig, "type", "tokenized"));
                MetadataFieldImpl fieldDesc = new MetadataFieldImpl(fieldName, fieldType,
                        metadataFields.getMetadataFieldValuesFactory());

                fieldDesc.custom().putAll(CustomProps.fromJson(Json.getObject(fieldConfig, "custom")));
                fieldDesc.setGroup(Json.getString(fieldConfig, "group", ""));
                fieldDesc.setAnalyzer(Json.getString(fieldConfig, "analyzer", "DEFAULT"));
                metadataFields.put(fieldName, fieldDesc);
            }
        }

        // Annotated fields
        ObjectNode nodeAnnotatedFields = Json.getObject(jsonRoot, IntegratedMetadataUtil.KEY_ANNOTATED_FIELDS);
        boolean hasAnnotatedFields = nodeAnnotatedFields.size() > 0;
        AnnotatedFieldsImpl annotatedFields = metadata.annotatedFields();
        if (hasAnnotatedFields) {
            if (metadata.custom().containsKey("annotationGroups")) {
                annotatedFields.clearAnnotationGroups();
                Map<String, List<Map<String, Object>>> groupingsPerField = metadata.custom().get("annotationGroups", Collections.emptyMap());
                for (Map.Entry<String, List<Map<String, Object>>> entry: groupingsPerField.entrySet()) {
                    String fieldName = entry.getKey();
                    List<Map<String, Object>> groups = entry.getValue();
                    List<AnnotationGroup> annotationGroups = extractAnnotationGroups(annotatedFields, fieldName, groups);
                    annotatedFields.putAnnotationGroups(fieldName, new AnnotationGroups(fieldName, annotationGroups));
                }
            }
            // Annotated fields
            Iterator<Entry<String, JsonNode>> it = nodeAnnotatedFields.fields();
            final Set<String> KEYS_ANNOTATED_FIELD_CONFIG = new HashSet<>(Arrays.asList(
                    IntegratedMetadataUtil.KEY_MAIN_ANNOTATION, "hasContentStore", "hasXmlTags", "annotations", "custom"));
            while (it.hasNext()) {
                Entry<String, JsonNode> entry = it.next();
                String fieldName = entry.getKey();
                ObjectNode fieldConfig = (ObjectNode)entry.getValue();
                warnUnknownKeys("in annotated field config for '" + fieldName + "'", fieldConfig,
                        KEYS_ANNOTATED_FIELD_CONFIG);
                AnnotatedFieldImpl fieldDesc = new AnnotatedFieldImpl(metadata, fieldName);

                fieldDesc.custom().putAll(CustomProps.fromJson(Json.getObject(fieldConfig, "custom")));

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
                        AnnotationImpl annotation = new AnnotationImpl(fieldDesc);
                        String offsetsSensitivity = "";
                        while (itAnnotOpt.hasNext()) {
                            Entry<String, JsonNode> opt = itAnnotOpt.next();
                            switch (opt.getKey()) {
                            case "name":
                                annotation.setName(opt.getValue().textValue());
                                annotationOrder.add(opt.getValue().textValue());
                                break;
                            case "custom":
                                annotation.custom().putAll(CustomProps.fromJson((ObjectNode)opt.getValue()));
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
                    if (fieldDesc.custom.get("displayOrder", Collections.emptyList()).isEmpty())
                        fieldDesc.setDisplayOrder(annotationOrder);
                }
                AnnotationImpl mainAnnot = (AnnotationImpl)fieldDesc.annotations().main();
                if (mainAnnot != null) {
                    MatchSensitivity offsetsSens = mainAnnot.mainSensitivity().sensitivity();
                    mainAnnot.setOffsetsSensitivity(offsetsSens);
                }
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
        metadataFields.setDefaultAnalyzerName(Json.getString(jsonRoot, "defaultAnalyzer", "DEFAULT"));
        if (jsonRoot.has("pidField"))
            metadataFields.setSpecialField(MetadataFields.PID, jsonRoot.get("pidField").textValue());
        if (metadataFields.titleField() == null) {
            if (metadataFields.pidField() != null)
                metadataFields.setSpecialField(MetadataFields.TITLE, metadataFields.pidField().name());
            else
                metadataFields.setSpecialField(MetadataFields.TITLE, "fromInputFile");
        }
    }

    private static void extractTopLevelKeys(IndexMetadataIntegrated metadata, ObjectNode jsonRoot) {
        final Set<String> KEYS_TOP_LEVEL = new HashSet<>(Arrays.asList(
                "custom", "contentViewable", "documentFormat", "versionInfo",
                "metadataFields", IntegratedMetadataUtil.KEY_ANNOTATED_FIELDS, "defaultAnalyzer", "pidField"));
        warnUnknownKeys("at top-level", jsonRoot, KEYS_TOP_LEVEL);

        // Get top-level custom properties
        metadata.setCustomProperties(CustomProps.fromJson(Json.getObject(jsonRoot, "custom")));

        // Set some metadata fields settings from the custom properties
        // (we should eventually get rid of these copies of the properties)
        CustomPropsMap custom = metadata.custom();
        MetadataFieldsImpl metadataFields = metadata.metadataFields();
        metadataFields.setDefaultUnknownCondition(custom.get("unknownCondition", "NEVER"));
        metadataFields.setDefaultUnknownValue(custom.get("unknownValue", "unknown"));
        if (custom.containsKey("authorField"))
            metadataFields.setSpecialField(MetadataFields.AUTHOR, (String) custom.get("authorField"));
        if (custom.containsKey("dateField"))
            metadataFields.setSpecialField(MetadataFields.DATE, (String) custom.get("dateField"));
        if (custom.containsKey("titleField"))
            metadataFields.setSpecialField(MetadataFields.TITLE, (String) custom.get("titleField"));

        metadata.setContentViewable(Json.getBoolean(jsonRoot, "contentViewable", false));
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

        // Add top-level custom properties
        jsonRoot.putPOJO("custom", metadata.custom().asMap());

        jsonRoot.put("contentViewable", metadata.contentViewable());
        jsonRoot.put("documentFormat", metadata.documentFormat());
        ObjectNode versionInfo = jsonRoot.putObject("versionInfo");
        versionInfo.put("blackLabBuildTime", metadata.indexBlackLabBuildTime());
        versionInfo.put("blackLabVersion", metadata.indexBlackLabVersion());
        versionInfo.put("indexFormat", metadata.indexFormat());
        versionInfo.put("timeCreated", metadata.timeCreated());
        versionInfo.put("timeModified", metadata.timeModified());

        ObjectNode fieldInfo = addFieldInfo(metadata.metadataFields(), jsonRoot);
        addMetadataFields(metadata.metadataFields(), fieldInfo);
        addAnnotatedFields(metadata.annotatedFields(), fieldInfo);
        return jsonRoot;
    }

    private static ObjectNode addFieldInfo(MetadataFieldsImpl metadataFields, ObjectNode jsonRoot) {
        jsonRoot.put("defaultAnalyzer", metadataFields.defaultAnalyzerName());
        if (metadataFields.pidField() != null)
            jsonRoot.put("pidField", metadataFields.special(MetadataFields.PID).name());
        return jsonRoot;
    }

    private static void addMetadataFields(MetadataFields metaFields, ObjectNode fieldInfo) {
        ObjectNode metadataFields = fieldInfo.putObject("metadataFields");
        for (MetadataField f: metaFields) {
            ObjectNode fi = metadataFields.putObject(f.name());

            // Custom props
            fi.putPOJO("custom", f.custom().asMap());
            fi.put("type", f.type().stringValue());
            fi.put("analyzer", f.analyzerName());
        }
    }

    private static void addAnnotatedFields(AnnotatedFields annotFields, ObjectNode fieldInfo) {
        ObjectNode nodeAnnotatedFields = fieldInfo.putObject(KEY_ANNOTATED_FIELDS);
        for (AnnotatedField f: annotFields) {
            ObjectNode nodeField = nodeAnnotatedFields.putObject(f.name());
            nodeField.putPOJO("custom", f.custom().asMap());
            if (f.mainAnnotation() != null)
                nodeField.put(KEY_MAIN_ANNOTATION, f.mainAnnotation().name());
            nodeField.put("hasContentStore", f.hasContentStore());
            nodeField.put("hasXmlTags", f.hasXmlTags());
            ArrayNode annots = nodeField.putArray("annotations");
            for (Annotation annotation: f.annotations()) {
                ObjectNode annot = annots.addObject();
                annot.put("name", annotation.name());

                annot.putPOJO("custom", annotation.custom().asMap());

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

    protected static void addGroupsInfoFromConfig(ArrayNode metaGroups,
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

        // Also (recursively) add groups config from any linked documents
        for (ConfigLinkedDocument ld: config.getLinkedDocuments().values()) {
            Format format = DocumentFormats.getFormat(ld.getInputFormatIdentifier());
            if (format != null && format.isConfigurationBased())
                addGroupsInfoFromConfig(metaGroups, annotGroupsPerField, format.getConfig());
        }
    }

    protected static void addFieldInfoFromConfig(ObjectNode metadata, ObjectNode annotated, ConfigInputFormat config) {
        // Add metadata info
        String defaultAnalyzer = config.getMetadataDefaultAnalyzer();
        for (ConfigMetadataBlock b: config.getMetadataBlocks()) {
            for (ConfigMetadataField f: b.getFields()) {
                if (f.isForEach())
                    continue;
                ObjectNode g = metadata.putObject(f.getName());

                // Custom properties
                ObjectNode nodeCustom = g.putObject("custom");
                nodeCustom.put("displayName", f.getDisplayName());
                nodeCustom.put("description", f.getDescription());
                nodeCustom.put("uiType", f.getUiType());
                nodeCustom.put("unknownCondition", (f.getUnknownCondition() == null ?
                        config.getMetadataDefaultUnknownCondition() :
                        f.getUnknownCondition()).stringValue());
                nodeCustom.put("unknownValue",
                        f.getUnknownValue() == null ? config.getMetadataDefaultUnknownValue() : f.getUnknownValue());
                ObjectNode h = nodeCustom.putObject("displayValues");
                for (Entry<String, String> e: f.getDisplayValues().entrySet()) {
                    h.put(e.getKey(), e.getValue());
                }
                ArrayNode i = nodeCustom.putArray("displayOrder");
                for (String v: f.getDisplayOrder()) {
                    i.add(v);
                }

                g.put("type", f.getType().stringValue());
                if (!f.getAnalyzer().equals(defaultAnalyzer))
                    g.put("analyzer", f.getAnalyzer());
            }
        }

        // Add annotated field info
        for (ConfigAnnotatedField f: config.getAnnotatedFields().values()) {
            ObjectNode g = annotated.putObject(f.getName());

            ObjectNode nodeCustom = g.putObject("custom");
            nodeCustom.put("displayName", f.getDisplayName());
            nodeCustom.put("description", f.getDescription());
            ArrayNode displayOrder = nodeCustom.putArray("displayOrder");

            if (!f.getAnnotations().isEmpty())
                g.put(KEY_MAIN_ANNOTATION, f.getAnnotations().values().iterator().next().getName());
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
            if (format != null && format.isConfigurationBased()) {
                addFieldInfoFromConfig(metadata, annotated, format.getConfig());
            }
        }
    }

    private static void addAnnotationInfo(ConfigAnnotation annotation, boolean hasOffsets, ArrayNode displayOrder, ArrayNode annotations) {
        String annotationName = annotation.getName();
        displayOrder.add(annotationName);
        ObjectNode annotationNode = annotations.addObject();
        annotationNode.put("name", annotationName);

        ObjectNode nodeCustom = annotationNode.putObject("custom");
        nodeCustom.put("displayName", annotation.getDisplayName());
        nodeCustom.put("description", annotation.getDescription());
        nodeCustom.put("uiType", annotation.getUiType());

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
            List<Map<String, Object>> groups) {
        List<AnnotationGroup> annotationGroups = new ArrayList<>();
        for (Map<String, Object> group: groups) {
            String groupName = (String)group.getOrDefault("name", "UNKNOWN");
            List<String> annotations = (List<String>)group.getOrDefault( "annotations", Collections.emptyList());
            boolean addRemainingAnnotations = (boolean)group.getOrDefault("addRemainingAnnotations", false);
            annotationGroups.add(new AnnotationGroup(annotatedFields, fieldName, groupName, annotations,
                    addRemainingAnnotations));
        }
        return annotationGroups;
    }
}
