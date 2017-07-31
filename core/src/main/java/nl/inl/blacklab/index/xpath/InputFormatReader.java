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
import com.fasterxml.jackson.databind.node.ValueNode;

import nl.inl.blacklab.index.complex.ComplexFieldProperty.SensitivitySetting;
import nl.inl.blacklab.index.xpath.ConfigInputFormat.FileType;
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

    private static ObjectNode obj(JsonNode node, String name) {
        if (!(node instanceof ObjectNode))
            throw new InputFormatConfigException(name + " must be an object");
        return (ObjectNode) node;
    }

    private static ObjectNode obj(Entry<String, JsonNode> e) {
        return obj(e.getValue(), e.getKey());
    }

    private static ArrayNode array(JsonNode node, String name) {
        if (!(node instanceof ArrayNode))
            throw new InputFormatConfigException(name + " must be an array");
        return (ArrayNode) node;
    }

    private static ArrayNode array(Entry<String, JsonNode> e) {
        return array(e.getValue(), e.getKey());
    }

    private static String str(JsonNode node, String name) {
        if (!(node instanceof ValueNode))
            throw new InputFormatConfigException(name + " must be a value");
        return node.asText();
    }

    private static String str(Entry<String, JsonNode> e) {
        return str(e.getValue(), e.getKey());
    }

    private static boolean bool(JsonNode node, String name) {
        if (!(node instanceof ValueNode))
            throw new InputFormatConfigException(name + " must be a value");
        return node.asBoolean();
    }

    private static boolean bool(Entry<String, JsonNode> e) {
        return bool(e.getValue(), e.getKey());
    }

    public static void read(Reader r, boolean isJson, ConfigInputFormat cfg) throws IOException {
        ObjectMapper mapper = isJson ? Json.getJsonObjectMapper() : Json.getYamlObjectMapper();
        JsonNode root = mapper.readTree(r);
        read(root, cfg);
    }

    public static void read(File file, ConfigInputFormat cfg) throws IOException {
        read(FileUtil.openForReading(file), file.getName().endsWith(".json"), cfg);
    }

    protected static void read(JsonNode root, ConfigInputFormat cfg) {
        obj(root, "root node");
        Iterator<Entry<String, JsonNode>> it = root.fields();
        while (it.hasNext()) {
            Entry<String, JsonNode> e = it.next();
            JsonNode v = e.getValue();
            switch (e.getKey()) {
            case "name": cfg.setName(str(e)); break;
            case "displayName": cfg.setDisplayName(str(e)); break;
            case "description": cfg.setDescription(str(e)); break;
            case "baseFormat": cfg.setBaseFormat(str(e)); break;
            case "type": cfg.setType(str(e)); break;
            case "fileType": cfg.setFileType(FileType.fromStringValue(str(e))); break;
            case "tabularOptions": cfg.setTabularOptions(readTabularOptions(e)); break;
            case "contentViewable": cfg.setContentViewable(bool(e)); break;
            case "namespaces": readStringMap(e, cfg.namespaces); break;
            case "documentPath": cfg.setDocumentPath(str(e)); break;
            case "store": cfg.setStore(bool(e)); break;
            case "indexFieldAs": readStringMap(e, cfg.indexFieldAs); break;
            case "specialFields": readStringMap(e, cfg.specialFields); break;
            case "annotatedFields": readAnnotatedFields(e, cfg); break;
            case "metadataFieldGroups": readMetadataFieldGroups(e, cfg); break;
            case "metadataDefaultAnalyzer": cfg.setMetadataDefaultAnalyzer(str(e)); break;
            case "metadata": readMetadata(v, cfg); break;
            case "linkedDocuments": readLinkedDocuments(e, cfg); break;
            default:
                throw new InputFormatConfigException("Unknown top-level key " + e.getKey());
            }
        }
    }

    private static ConfigTabularOptions readTabularOptions(Entry<String, JsonNode> tabOptEntry) {
        ObjectNode node = obj(tabOptEntry.getValue(), null);
        Iterator<Entry<String, JsonNode>> it = node.fields();
        ConfigTabularOptions to = new ConfigTabularOptions();
        while (it.hasNext()) {
            Entry<String, JsonNode> e = it.next();
            switch(e.getKey()) {
            case "type": to.setType(ConfigTabularOptions.Type.fromStringValue(str(e))); break;
            case "columnNames": to.setColumnNames(bool(e)); break;
            default:
                throw new InputFormatConfigException("Unknown key " + e.getKey() + " in tabular options");
            }
        }
        return to;
    }

    private static void readStringMap(Entry<String, JsonNode> strMapEntry, Map<String, String> addToMap) {
        ObjectNode node = obj(strMapEntry.getValue(), null);
        Iterator<Entry<String, JsonNode>> it = node.fields();
        while (it.hasNext()) {
            Entry<String, JsonNode> e = it.next();
            addToMap.put(e.getKey(), str(e));
        }
    }

    private static void readStringList(Entry<String, JsonNode> strListEntry, List<String> addToList) {
        Iterator<JsonNode> it = array(strListEntry).elements();
        while (it.hasNext())
            addToList.add(str(it.next(), strListEntry.getKey() + " element"));
    }

    private static void readMetadataFieldGroups(Entry<String, JsonNode> mfgEntry, ConfigInputFormat cfg) {
        Iterator<JsonNode> itGroups = array(mfgEntry).elements();
        while (itGroups.hasNext()) {
            JsonNode group = itGroups.next();
            Iterator<Entry<String, JsonNode>> itGroup = obj(group, "metadata field group").fields();
            ConfigMetadataFieldGroup g = new ConfigMetadataFieldGroup();
            List<String> fields = new ArrayList<>();
            while (itGroup.hasNext()) {
                Entry<String, JsonNode> e = itGroup.next();
                switch (e.getKey()) {
                case "name": g.setName(str(e)); break;
                case "fields":
                    readStringList(e, fields);
                    g.addFields(fields);
                    break;
                case "addRemainingFields":
                    g.setAddRemainingFields(bool(e));
                    break;
                default:
                    throw new InputFormatConfigException("Unknown key " + e.getKey() + " in metadata field group " + g.getName());
                }
            }
            cfg.addMetadataFieldGroup(g);
        }
    }

    private static void readAnnotatedFields(Entry<String, JsonNode> afsEntry, ConfigInputFormat cfg) {
        Iterator<Entry<String, JsonNode>> itFields = obj(afsEntry).fields();
        while (itFields.hasNext()) {
            Entry<String, JsonNode> field = itFields.next();
            String fieldName = field.getKey();
            Iterator<Entry<String, JsonNode>> itField = obj(field).fields();
            ConfigAnnotatedField af = cfg.getOrCreateAnnotatedField(fieldName);
            while (itField.hasNext()) {
                Entry<String, JsonNode> e = itField.next();
                switch (e.getKey()) {
                case "displayName": af.setDisplayName(str(e)); break;
                case "description": af.setDescription(str(e)); break;
                case "containerPath": af.setContainerPath(str(e)); break;
                case "wordPath": af.setWordPath(str(e)); break;
                case "tokenPositionIdPath": af.setTokenPositionIdPath(str(e)); break;
                case "punctPath": af.setPunctPath(str(e)); break;
                case "annotations": readAnnotations(e, af); break;
                case "standoffAnnotations": readStandoffAnnotations(e, af); break;
                case "inlineTags": readInlineTags(e, af); break;
                default:
                    throw new InputFormatConfigException("Unknown key " + e.getKey() + " in annotated field " + fieldName);
                }
            }
        }
    }

    private static void readAnnotations(Entry<String, JsonNode> annotsEntry, ConfigWithAnnotations af) {
        Iterator<JsonNode> itAnnotations = array(annotsEntry).elements();
        while (itAnnotations.hasNext()) {
            af.addAnnotation(readAnnotation(false, itAnnotations.next()));
        }
    }

    private static void readSubAnnotations(Entry<String, JsonNode> saEntry, ConfigAnnotation annot) {
        Iterator<JsonNode> itAnnotations = array(saEntry).elements();
        while (itAnnotations.hasNext()) {
            annot.addSubAnnotation(readAnnotation(true, itAnnotations.next()));
        }
    }

    protected static ConfigAnnotation readAnnotation(boolean isSubAnnotation, JsonNode a) {
        Iterator<Entry<String, JsonNode>> itAnnotation = obj(a, "annotation").fields();
        ConfigAnnotation annot = new ConfigAnnotation();
        while (itAnnotation.hasNext()) {
            Entry<String, JsonNode> e = itAnnotation.next();
            switch (e.getKey()) {
            case "name": annot.setName(str(e)); break;
            case "value": annot.setValuePath(fixedStringToXpath(str(e))); break;
            case "valuePath": annot.setValuePath(str(e)); break;
            case "forEachPath":
                if (!isSubAnnotation)
                    throw new InputFormatConfigException("Only subannotations may have forEachPath/namePath");
                annot.setForEachPath(str(e)); break;
            case "namePath":
                if (!isSubAnnotation)
                    throw new InputFormatConfigException("Only subannotations may have forEachPath/namePath");
                annot.setName(str(e)); break;
            case "process": annot.setProcess(readProcess(e)); break;
            case "displayName": annot.setDisplayName(str(e)); break;
            case "description": annot.setDescription(str(e)); break;
            case "basePath": annot.setBasePath(str(e)); break;
            case "sensitivity":
                if (isSubAnnotation)
                    throw new InputFormatConfigException("Subannotations may not have their own sensitivity settings");
                annot.setSensitivity(SensitivitySetting.fromStringValue(str(e))); break;
            case "uiType": annot.setUiType(str(e)); break;
            case "subAnnotations":
                if (isSubAnnotation)
                    throw new InputFormatConfigException("Subannotations may not have their own subannotations");
                readSubAnnotations(e, annot); break;
            default:
                throw new InputFormatConfigException("Unknown key " + e.getKey() + " in annotation " + StringUtil.nullToEmpty(annot.getName()));
            }
        }
        return annot;
    }

    /**
     * Convert a fixed string value to an XPath expression yielding that value.
     * @param s fixed string the XPath should evaluate to
     * @return XPath expression
     */
    public static String fixedStringToXpath(String s) {
        return "\"" + s.replaceAll("\\\\", "\\\\").replaceAll("\"", "\\\"") + "\"";
    }

    private static void readStandoffAnnotations(Entry<String, JsonNode> sasEntry, ConfigAnnotatedField af) {
        Iterator<JsonNode> itAnnotations = array(sasEntry).elements();
        while (itAnnotations.hasNext()) {
            JsonNode as = itAnnotations.next();
            ConfigStandoffAnnotations s = new ConfigStandoffAnnotations();
            Iterator<Entry<String, JsonNode>> it = obj(as, "standoffAnnotation").fields();
            while (it.hasNext()) {
                Entry<String, JsonNode> e = it.next();
                switch(e.getKey()) {
                case "path": s.setPath(str(e)); break;
                case "refTokenPositionIdPath": s.setRefTokenPositionIdPath(str(e)); break;
                case "annotations": readAnnotations(e, s); break;
                default:
                    throw new InputFormatConfigException("Unknown key " + e.getKey() + " in standoff annotations block");
                }
            }
            af.addStandoffAnnotation(s);
        }
    }

    private static void readInlineTags(Entry<String, JsonNode> itsEntry, ConfigAnnotatedField af) {
        Iterator<JsonNode> itTags = array(itsEntry).elements();
        while (itTags.hasNext()) {
            JsonNode as = itTags.next();
            ConfigInlineTag t = new ConfigInlineTag();
            Iterator<Entry<String, JsonNode>> itTag = obj(as, "inlineTag").fields();
            while (itTag.hasNext()) {
                Entry<String, JsonNode> e = itTag.next();
                switch(e.getKey()) {
                case "path": t.setPath(str(e)); break;
                default:
                    throw new InputFormatConfigException("Unknown key " + e.getKey() + " in inline tag " + t.getPath());
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
            throw new InputFormatConfigException("Wrong node type for metadata (must be object or array)");
        }
    }

    private static void readMetadataBlock(JsonNode as, ConfigInputFormat cfg) {
        ConfigMetadataBlock b = cfg.createMetadataBlock();
        Iterator<Entry<String, JsonNode>> it = obj(as, "metadata block").fields();
        while (it.hasNext()) {
            Entry<String, JsonNode> e = it.next();
            switch(e.getKey()) {
            case "containerPath": b.setContainerPath(str(e)); break;
            case "defaultAnalyzer": b.setDefaultAnalyzer(str(e)); break;
            case "fields": readMetadataFields(e, b); break;
            default:
                throw new InputFormatConfigException("Unknown key " + e.getKey() + " in metadata block");
            }
        }
    }

    private static void readMetadataFields(Entry<String, JsonNode> mfsEntry, ConfigMetadataBlock b) {
        Iterator<JsonNode> itFields = array(mfsEntry).elements();
        while (itFields.hasNext()) {
            JsonNode fld = itFields.next();
            Iterator<Entry<String, JsonNode>> itField = obj(fld, "metadata field").fields();
            ConfigMetadataField f = new ConfigMetadataField();
            while (itField.hasNext()) {
                Entry<String, JsonNode> e = itField.next();
                switch (e.getKey()) {
                case "name": case "namePath": f.setName(str(e)); break;
                case "value": f.setValuePath(fixedStringToXpath(str(e))); break;
                case "valuePath": f.setValuePath(str(e)); break;
                case "forEachPath": f.setForEachPath(str(e)); break;
                case "process": f.setProcess(readProcess(e)); break;
                case "displayName": f.setDisplayName(str(e)); break;
                case "description": f.setDescription(str(e)); break;
                case "type": f.setType(FieldType.fromStringValue(str(e))); break;
                case "uiType": f.setUiType(str(e)); break;
                case "unknownCondition": f.setUnknownCondition(UnknownCondition.fromStringValue(str(e))); break;
                case "unknownValue": f.setUiType(str(e)); break;
                case "analyzer": f.setAnalyzer(str(e)); break;
                default:
                    throw new InputFormatConfigException("Unknown key " + e.getKey() + " in metadata field " + f.getName());
                }
            }
            b.addMetadataField(f);
        }
    }

    private static void readLinkedDocuments(Entry<String, JsonNode> ldsEntry, ConfigInputFormat cfg) {
        Iterator<Entry<String, JsonNode>> itLinkedDocs = obj(ldsEntry).fields();
        while (itLinkedDocs.hasNext()) {
            Entry<String, JsonNode> linkedDoc = itLinkedDocs.next();
            ConfigLinkedDocument ld = cfg.getOrCreateLinkedDocument(linkedDoc.getKey());
            Iterator<Entry<String, JsonNode>> itLinkedDoc = obj(linkedDoc).fields();
            while (itLinkedDoc.hasNext()) {
                Entry<String, JsonNode> e = itLinkedDoc.next();
                switch (e.getKey()) {
                case "store": ld.setStore(bool(e)); break;
                case "linkValues": readLinkValues(e, ld); break;
                case "ifLinkPathMissing": ld.setIfLinkPathMissing(MissingLinkPathAction.fromStringValue(str(e))); break;
                case "inputFile": ld.setInputFile(str(e)); break;
                case "pathInsideArchive": ld.setPathInsideArchive(str(e)); break;
                case "documentPath": ld.setDocumentPath(str(e)); break;
                case "inputFormat": ld.setInputFormat(str(e)); break;
                default:
                    throw new InputFormatConfigException("Unknown key " + e.getKey() + " in linked document " + ld.getName());
                }
            }
        }
    }

    private static void readLinkValues(Entry<String, JsonNode> lvsEntry, ConfigLinkedDocument ld) {
        Iterator<JsonNode> itLinkValues = array(lvsEntry).elements();
        while (itLinkValues.hasNext()) {
            JsonNode linkValue = itLinkValues.next();
            ConfigLinkValue lv = new ConfigLinkValue();
            Iterator<Entry<String, JsonNode>> itLinkValue = obj(linkValue, "link value").fields();
            while (itLinkValue.hasNext()) {
                Entry<String, JsonNode> e = itLinkValue.next();
                switch (e.getKey()) {
                case "value": lv.setValuePath(fixedStringToXpath(str(e))); break;
                case "valuePath": lv.setValuePath(str(e)); break;
                case "valueField": lv.setValueField(str(e)); break;
                case "process": lv.setProcess(readProcess(e)); break;
                default:
                    throw new InputFormatConfigException("Unknown key " + e.getKey() + " in linked document " + ld.getName());
                }
            }
            ld.addLinkValue(lv);
        }
    }

    private static List<ConfigProcessStep> readProcess(Entry<String, JsonNode> prEntry) {
        Iterator<JsonNode> itSteps = array(prEntry).elements();
        List<ConfigProcessStep> p = new ArrayList<>();
        while (itSteps.hasNext()) {
            JsonNode step = itSteps.next();
            ConfigProcessStep s = new ConfigProcessStep();
            Iterator<Entry<String, JsonNode>> itStep = obj(step, "processing step").fields();
            while (itStep.hasNext()) {
                Entry<String, JsonNode> e = itStep.next();
                if (s.getMethod() != null)
                    throw new InputFormatConfigException("Can only provide one method per processing step");
                s.setMethod(e.getKey());
                Iterator<Entry<String, JsonNode>> itParams = obj(e.getValue(), "processing step parameters").fields();
                while (itParams.hasNext()) {
                    Entry<String, JsonNode> par = itParams.next();
                    s.addParam(par.getKey(), par.getValue().asText());
                }
            }
            p.add(s);
        }
        return p;
    }


}
