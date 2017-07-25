package nl.inl.blacklab.index.xpath;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import nl.inl.blacklab.index.complex.ComplexFieldProperty.SensitivitySetting;
import nl.inl.blacklab.index.xpath.ConfigLinkedDocument.MissingLinkPathAction;
import nl.inl.blacklab.search.indexstructure.FieldType;
import nl.inl.blacklab.search.indexstructure.MetadataFieldDesc.UnknownCondition;
import nl.inl.util.FileUtil;
import nl.inl.util.Json;
import nl.inl.util.StringUtil;

/**
 * Reads ConfigInputFormat from a YAML or JSON source.
 */
public class InputFormatReader {

    public static void read(Reader r, boolean isJson, ConfigInputFormat cfg) throws IOException {
        ObjectMapper mapper = isJson ? Json.getJsonObjectMapper() : Json.getYamlObjectMapper();
        ObjectNode root = (ObjectNode)mapper.readTree(r);
        read(root, cfg);
    }

    public static void read(File file, ConfigInputFormat cfg) throws IOException {
        read(FileUtil.openForReading(file), file.getName().endsWith(".json"), cfg);
    }

    protected static void read(ObjectNode root, ConfigInputFormat cfg) {
        Iterator<Entry<String, JsonNode>> it = root.fields();
        while (it.hasNext()) {
            Entry<String, JsonNode> e = it.next();
            JsonNode v = e.getValue();
            switch (e.getKey()) {
            case "name": cfg.setName(v.asText()); break;
            case "displayName": cfg.setDisplayName(v.asText()); break;
            case "description": cfg.setDescription(v.asText()); break;
            case "baseFormat": cfg.setBaseFormat(v.asText()); break;
            case "type": cfg.setType(v.asText()); break;
            case "namespaces": readStringMap(v, cfg.namespaces); break;
            case "documentPath": cfg.setDocumentPath(v.asText()); break;
            case "store": cfg.setStore(v.asBoolean()); break;
            case "indexFieldAs": readStringMap(v, cfg.indexFieldAs); break;
            case "specialFields": readStringMap(v, cfg.specialFields); break;
            case "annotatedFields": readAnnotatedFields(v, cfg); break;
            case "metadataFieldGroups": readMetadataFieldGroups(v, cfg); break;
            case "metadataDefaultAnalyzer": cfg.setMetadataDefaultAnalyzer(v.asText()); break;
            case "metadata": readMetadata(v, cfg); break;
            case "linkedDocuments": readLinkedDocuments(v, cfg); break;
            default:
                String n = cfg.getName() == null ? "UNKNOWN" : cfg.getName();
                throw new IllegalArgumentException("Unknown key " + e.getKey() + " in format config " + n);
            }
        }
    }

    private static void readStringMap(JsonNode node, Map<String, String> addToMap) {
        Iterator<Entry<String, JsonNode>> it = node.fields();
        while (it.hasNext()) {
            Entry<String, JsonNode> e = it.next();
            String k = e.getKey();
            String v = e.getValue().asText();
            addToMap.put(k, v);
        }
    }

    private static void readStringList(JsonNode node, List<String> addToList) {
        Iterator<JsonNode> it = node.elements();
        while (it.hasNext())
            addToList.add(it.next().asText());
    }

    private static void readMetadataFieldGroups(JsonNode node, ConfigInputFormat cfg) {
        Iterator<Entry<String, JsonNode>> itGroups = node.fields();
        while (itGroups.hasNext()) {
            Entry<String, JsonNode> group = itGroups.next();
            String groupName = group.getKey();
            Iterator<Entry<String, JsonNode>> itGroup = group.getValue().fields();
            ConfigMetadataFieldGroup g = cfg.getOrCreateMetadataFieldGroup(groupName);
            List<String> fields = new ArrayList<>();
            while (itGroup.hasNext()) {
                Entry<String, JsonNode> prop = itGroup.next();
                switch (prop.getKey()) {
                case "fields":
                    readStringList(prop.getValue(), fields);
                    g.addFields(fields);
                    break;
                case "addRemainingFields":
                    g.setAddRemainingFields(prop.getValue().asBoolean());
                    break;
                default:
                    throw new IllegalArgumentException("Unknown key " + prop.getKey() + " in metadata field group " + groupName);
                }
            }
            cfg.addMetadataFieldGroup(g);
        }
    }

    private static void readAnnotatedFields(JsonNode node, ConfigInputFormat cfg) {
        Iterator<Entry<String, JsonNode>> itFields = node.fields();
        while (itFields.hasNext()) {
            Entry<String, JsonNode> field = itFields.next();
            String fieldName = field.getKey();
            Iterator<Entry<String, JsonNode>> itField = field.getValue().fields();
            ConfigAnnotatedField af = cfg.getOrCreateAnnotatedField(fieldName);
            while (itField.hasNext()) {
                Entry<String, JsonNode> prop = itField.next();
                JsonNode v = prop.getValue();
                switch (prop.getKey()) {
                case "displayName": af.setDisplayName(v.asText()); break;
                case "description": af.setDescription(v.asText()); break;
                case "containerPath": af.setContainerPath(v.asText()); break;
                case "wordsPath": af.setWordsPath(v.asText()); break;
                case "tokenPositionIdPath": af.setTokenPositionIdPath(v.asText()); break;
                case "punctPath": af.setPunctPath(v.asText()); break;
                case "annotations": readAnnotations(v, af); break;
                case "standoffAnnotations": readStandoffAnnotations(v, af); break;
                case "inlineTags": readInlineTags(v, af); break;
                default:
                    throw new IllegalArgumentException("Unknown key " + prop.getKey() + " in annotated field " + fieldName);
                }
            }
            cfg.addAnnotatedField(af);
        }
    }

    private static void readAnnotations(JsonNode node, ConfigWithAnnotations af) {
        Iterator<JsonNode> itAnnotations = node.elements();
        while (itAnnotations.hasNext()) {
            af.addAnnotation(readAnnotation(false, itAnnotations.next()));
        }
    }

    private static void readSubAnnotations(JsonNode node, ConfigAnnotation annot) {
        Iterator<JsonNode> itAnnotations = node.elements();
        while (itAnnotations.hasNext()) {
            annot.addSubAnnotation(readAnnotation(true, itAnnotations.next()));
        }
    }

    protected static ConfigAnnotation readAnnotation(boolean isSubAnnotation, JsonNode a) {
        Iterator<Entry<String, JsonNode>> itAnnotation = a.fields();
        ConfigAnnotation annot = new ConfigAnnotation();
        while (itAnnotation.hasNext()) {
            Entry<String, JsonNode> prop = itAnnotation.next();
            JsonNode v = prop.getValue();
            switch (prop.getKey()) {
            case "name": annot.setName(v.asText()); break;
            case "valuePath": annot.setValuePath(v.asText()); break;
            case "forEachPath":
                if (!isSubAnnotation)
                    throw new IllegalArgumentException("Only subannotations may have forEachPath/namePath");
                annot.setForEachPath(v.asText()); break;
            case "namePath":
                if (!isSubAnnotation)
                    throw new IllegalArgumentException("Only subannotations may have forEachPath/namePath");
                annot.setName(v.asText()); break;
            case "displayName": annot.setDisplayName(v.asText()); break;
            case "description": annot.setDescription(v.asText()); break;
            case "basePath": annot.setBasePath(v.asText()); break;
            case "sensitivity":
                if (isSubAnnotation)
                    throw new IllegalArgumentException("Subannotations may not have their own sensitivity settings");
                annot.setSensitivity(SensitivitySetting.fromStringValue(v.asText())); break;
            case "uiType": annot.setUiType(v.asText()); break;
            case "subAnnotations":
                if (isSubAnnotation)
                    throw new IllegalArgumentException("Subannotations may not have their own subannotations");
                readSubAnnotations(v, annot); break;
            default:
                throw new IllegalArgumentException("Unknown key " + prop.getKey() + " in annotation " + StringUtil.nullToEmpty(annot.getName()));
            }
        }
        return annot;
    }

    private static void readStandoffAnnotations(JsonNode node, ConfigAnnotatedField af) {
        Iterator<JsonNode> itAnnotations = node.elements();
        while (itAnnotations.hasNext()) {
            JsonNode as = itAnnotations.next();
            ConfigStandoffAnnotations s = new ConfigStandoffAnnotations();
            Iterator<Entry<String, JsonNode>> it = as.fields();
            while (it.hasNext()) {
                Entry<String, JsonNode> e = it.next();
                JsonNode v = e.getValue();
                switch(e.getKey()) {
                case "path": s.setPath(v.asText()); break;
                case "refTokenPositionIdPath": s.setRefTokenPositionIdPath(v.asText()); break;
                case "annotations": readAnnotations(v, s); break;
                default:
                    throw new IllegalArgumentException("Unknown key " + e.getKey() + " in standoff annotations block");
                }
            }
            af.addStandoffAnnotation(s);
        }
    }

    private static void readInlineTags(JsonNode node, ConfigAnnotatedField af) {
        Iterator<JsonNode> itTags = node.elements();
        while (itTags.hasNext()) {
            JsonNode as = itTags.next();
            ConfigInlineTag t = new ConfigInlineTag();
            Iterator<Entry<String, JsonNode>> itTag = as.fields();
            while (itTag.hasNext()) {
                Entry<String, JsonNode> e = itTag.next();
                JsonNode v = e.getValue();
                switch(e.getKey()) {
                case "path": t.setPath(v.asText()); break;
                default:
                    throw new IllegalArgumentException("Unknown key " + e.getKey() + " in inline tag " + t.getPath());
                }
            }
            af.addInlineTag(t);
        }
    }

    private static void readMetadata(JsonNode node, ConfigInputFormat cfg) {
        if (node instanceof ObjectNode) {
            // Single metadata block
            readMetadataBlock(node, cfg);
        } else if (node instanceof ArrayNode) {
            // List of metadata blocks
            Iterator<JsonNode> itAnnotations = node.elements();
            while (itAnnotations.hasNext()) {
                JsonNode as = itAnnotations.next();
                readMetadataBlock(as, cfg);
            }
        } else {
            throw new IllegalArgumentException("Wrong node type for metadata");
        }
    }

    private static void readMetadataBlock(JsonNode as, ConfigInputFormat cfg) {
        ConfigMetadataBlock b = cfg.createMetadataBlock();
        Iterator<Entry<String, JsonNode>> it = as.fields();
        while (it.hasNext()) {
            Entry<String, JsonNode> e = it.next();
            JsonNode v = e.getValue();
            switch(e.getKey()) {
            case "containerPath": b.setContainerPath(v.asText()); break;
            case "defaultAnalyzer": b.setDefaultAnalyzer(v.asText()); break;
            case "fields": readMetadataFields(v, b); break;
            default:
                throw new IllegalArgumentException("Unknown key " + e.getKey() + " in metadata block");
            }
        }
    }

    private static void readMetadataFields(JsonNode node, ConfigMetadataBlock b) {
        Iterator<JsonNode> itFields = node.elements();
        while (itFields.hasNext()) {
            JsonNode fld = itFields.next();
            Iterator<Entry<String, JsonNode>> itField = fld.fields();
            while (itField.hasNext()) {
                Entry<String, JsonNode> e = itField.next();
                ConfigMetadataField f = new ConfigMetadataField();
                JsonNode v = e.getValue();
                switch (e.getKey()) {
                case "name": case "namePath": f.setName(v.asText()); break;
                case "valuePath": f.setValuePath(v.asText()); break;
                case "forEachPath": f.setForEachPath(v.asText()); break;
                case "displayName": f.setDisplayName(v.asText()); break;
                case "description": f.setDescription(v.asText()); break;
                case "type": f.setType(FieldType.fromStringValue(v.asText())); break;
                case "uiType": f.setUiType(v.asText()); break;
                case "unknownCondition": f.setUnknownCondition(UnknownCondition.fromStringValue(v.asText())); break;
                case "unknownValue": f.setUiType(v.asText()); break;
                case "analyzer": f.setAnalyzer(v.asText()); break;
                default:
                    throw new IllegalArgumentException("Unknown key " + e.getKey() + " in metadata field " + f.getName());
                }
            }
        }
    }

    private static void readLinkedDocuments(JsonNode node, ConfigInputFormat cfg) {
        Iterator<Entry<String, JsonNode>> itLinkedDocs = node.fields();
        while (itLinkedDocs.hasNext()) {
            Entry<String, JsonNode> e = itLinkedDocs.next();
            ConfigLinkedDocument ld = cfg.getOrCreateLinkedDocument(e.getKey());
            Iterator<Entry<String, JsonNode>> itLinkedDoc = e.getValue().fields();
            while (itLinkedDoc.hasNext()) {
                Entry<String, JsonNode> prop = itLinkedDoc.next();
                JsonNode v = prop.getValue();
                switch (prop.getKey()) {
                case "store": ld.setStore(v.asBoolean()); break;
                case "linkPaths": readStringList(v, ld.linkPaths); break;
                case "ifLinkPathMissing": ld.setIfLinkPathMissing(MissingLinkPathAction.fromStringValue(v.asText())); break;
                case "inputFile": ld.setInputFile(v.asText()); break;
                case "pathInsideArchive": ld.setPathInsideArchive(v.asText()); break;
                case "documentPath": ld.setDocumentPath(v.asText()); break;
                case "inputFormat": ld.setInputFormat(v.asText()); break;
                default:
                    throw new IllegalArgumentException("Unknown key " + e.getKey() + " in linked document " + ld.getName());
                }
            }
        }
    }


}
