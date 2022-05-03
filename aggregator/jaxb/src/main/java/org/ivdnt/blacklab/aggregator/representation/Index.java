package org.ivdnt.blacklab.aggregator.representation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.commons.lang3.StringUtils;
import org.ivdnt.blacklab.aggregator.helper.JacksonUtil;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@XmlRootElement(name="blacklabResponse")
@XmlAccessorType(XmlAccessType.FIELD)
public class Index implements Cloneable {

    /** Use this to serialize indices to JSON.
     *
     * Necessary because we convert a list (needed for the XML mapping) to a JSON object structure .
     */
    private static class ListAnnotatedFieldAnnotationGroupsSerializer extends JsonSerializer<List<AnnotatedFieldAnnotationGroups>> {
        @Override
        public void serialize(List<AnnotatedFieldAnnotationGroups> value, JsonGenerator jgen, SerializerProvider provider)
                throws IOException {

            if (value == null)
                return;
            jgen.writeStartObject();
            for (AnnotatedFieldAnnotationGroups field: value) {
                jgen.writeArrayFieldStart(field.name);
                for (AnnotationGroup annotationGroup: field.annotationGroups) {
                    jgen.writeStartObject();
                    {
                        jgen.writeStringField("name", annotationGroup.name);
                        jgen.writeArrayFieldStart("annotations");
                        {
                            for (String a: annotationGroup.annotations) {
                                jgen.writeString(a);
                            }
                        }
                        jgen.writeEndArray();
                    }
                    jgen.writeEndObject();
                }
                jgen.writeEndArray();
            }
            jgen.writeEndObject();
        }
    }

    /** Use this to deserialize indices from JSON.
     *
     * Necessary because we convert a JSON object structure to a list (because that's what the XML mapping uses).
     */
    private static class ListAnnotatedFieldAnnotationGroupsDeserializer extends JsonDeserializer<List<AnnotatedFieldAnnotationGroups>> {

        @Override
        public List<AnnotatedFieldAnnotationGroups> deserialize(JsonParser parser, DeserializationContext deserializationContext)
                throws IOException {

            JsonToken token = parser.currentToken();
            if (token != JsonToken.START_OBJECT)
                throw new RuntimeException("Expected START_OBJECT, found " + token);

            List<AnnotatedFieldAnnotationGroups> result = new ArrayList<>();
            while (true) {
                token = parser.nextToken();
                if (token == JsonToken.END_OBJECT)
                    break;

                if (token != JsonToken.FIELD_NAME)
                    throw new RuntimeException("Expected END_OBJECT or FIELD_NAME, found " + token);
                AnnotatedFieldAnnotationGroups groups = new AnnotatedFieldAnnotationGroups();
                groups.name = parser.getCurrentName();
                groups.annotationGroups = new ArrayList<>();

                token = parser.nextToken();
                if (token != JsonToken.START_ARRAY)
                    throw new RuntimeException("Expected END_OBJECT or START_ARRAY, found " + token);
                while (true) {
                    token = parser.nextToken();
                    if (token == JsonToken.END_ARRAY)
                        break;

                    if (token != JsonToken.START_OBJECT)
                        throw new RuntimeException("Expected END_ARRAY or START_OBJECT, found " + token);
                    AnnotationGroup group = new AnnotationGroup();
                    while (true) {
                        token = parser.nextToken();
                        if (token == JsonToken.END_OBJECT)
                            break;

                        if (token != JsonToken.FIELD_NAME)
                            throw new RuntimeException("Expected END_OBJECT or FIELD_NAME, found " + token);
                        String fieldName = parser.getCurrentName();
                        parser.nextToken();
                        switch (fieldName) {
                        case "name":
                            group.name = parser.getValueAsString();
                            break;
                        case "annotations":
                            group.annotations = JacksonUtil.readStringList(parser);
                            break;
                        default:
                            throw new RuntimeException("Unexpected field " + fieldName + " in AnnotationGroup");
                        }
                    }
                    groups.annotationGroups.add(group);
                }
                result.add(groups);
            }
            return result;
        }
    }

    /** Use this to serialize annotatedFields to JSON.
     *
     * Necessary because we convert a list (needed for the XML mapping) to a JSON object structure .
     */
    private static class ListAnnotatedFieldSerializer extends JsonSerializer<List<AnnotatedField>> {
        @Override
        public void serialize(List<AnnotatedField> value, JsonGenerator jgen, SerializerProvider provider)
                throws IOException {

            if (value == null)
                return;
            jgen.writeStartObject();
            for (AnnotatedField field: value) {
                jgen.writeObjectFieldStart(field.name);
                {
                    jgen.writeStringField("fieldName", field.fieldName);
                    jgen.writeBooleanField("isAnnotatedField", field.isAnnotatedField);
                    jgen.writeStringField("displayName", field.displayName);
                    jgen.writeStringField("description", field.description);
                    jgen.writeBooleanField("hasContentStore", field.hasContentStore);
                    jgen.writeBooleanField("hasXmlTags", field.hasXmlTags);
                    jgen.writeBooleanField("hasLengthTokens", field.hasLengthTokens);
                    jgen.writeStringField("mainAnnotation", field.mainAnnotation);
                    jgen.writeArrayFieldStart("displayOrder");
                    for (String a: field.displayOrder) {
                        jgen.writeString(a);
                    }
                    jgen.writeEndArray();
                    jgen.writeObjectFieldStart("annotations");
                    for (Annotation a: field.annotations) {
                        jgen.writeObjectFieldStart(a.name);
                        {
                            jgen.writeStringField("displayName", a.displayName);
                            jgen.writeStringField("description", a.description);
                            jgen.writeStringField("uiType", a.uiType);
                            jgen.writeBooleanField("hasForwardIndex", a.hasForwardIndex);
                            jgen.writeStringField("sensitivity", a.sensitivity);
                            jgen.writeStringField("offsetsAlternative", a.offsetsAlternative);
                            jgen.writeBooleanField("isInternal", a.isInternal);
                            if (a.subannotations != null && !a.subannotations.isEmpty()) {
                                jgen.writeArrayFieldStart("subannotations");
                                for (String sub: a.subannotations) {
                                    jgen.writeString(sub);
                                }
                                jgen.writeEndArray();
                            }
                            if (!StringUtils.isEmpty(a.parentAnnotation))
                                jgen.writeStringField("parentAnnotation", a.parentAnnotation);
                        }
                        jgen.writeEndObject();
                    }
                    jgen.writeEndObject();
                }
                jgen.writeEndObject();
            }
            jgen.writeEndObject();
        }
    }

    /** Use this to deserialize annotatedFields from JSON.
     *
     * Necessary because we convert a JSON object structure to a list (because that's what the XML mapping uses).
     */
    private static class ListAnnotatedFieldDeserializer extends JsonDeserializer<List<AnnotatedField>> {

        @Override
        public List<AnnotatedField> deserialize(JsonParser parser, DeserializationContext deserializationContext)
                throws IOException {

            JsonToken token = parser.currentToken();
            if (token != JsonToken.START_OBJECT)
                throw new RuntimeException("Expected START_OBJECT, found " + token);

            List<AnnotatedField> result = new ArrayList<>();
            while (true) {
                token = parser.nextToken();
                if (token == JsonToken.END_OBJECT)
                    break;

                if (token != JsonToken.FIELD_NAME)
                    throw new RuntimeException("Expected END_OBJECT or FIELD_NAME, found " + token);
                AnnotatedField field = new AnnotatedField(parser.getCurrentName());

                token = parser.nextToken();
                if (token != JsonToken.START_OBJECT)
                    throw new RuntimeException("Expected END_OBJECT or START_OBJECT, found " + token);
                while (true) {
                    token = parser.nextToken();
                    if (token == JsonToken.END_OBJECT)
                        break;

                    if (token != JsonToken.FIELD_NAME)
                        throw new RuntimeException("Expected END_OBJECT or FIELD_NAME, found " + token);
                    String fieldName = parser.getCurrentName();
                    token = parser.nextToken();
                    switch (fieldName) {
                    case "fieldName": field.fieldName = parser.getValueAsString(); break;
                    case "isAnnotatedField": field.isAnnotatedField = parser.getValueAsBoolean(); break;
                    case "displayName": field.displayName = parser.getValueAsString(); break;
                    case "description": field.description = parser.getValueAsString(); break;
                    case "hasContentStore": field.hasContentStore = parser.getValueAsBoolean(); break;
                    case "hasXmlTags": field.hasXmlTags = parser.getValueAsBoolean(); break;
                    case "hasLengthTokens": field.hasLengthTokens = parser.getValueAsBoolean(); break;
                    case "mainAnnotation": field.mainAnnotation = parser.getValueAsString(); break;
                    case "displayOrder":
                        field.displayOrder = JacksonUtil.readStringList(parser);
                        break;
                    case "annotations":
                        if (token != JsonToken.START_OBJECT)
                            throw new RuntimeException("Expected START_OBJECT, found " + token);
                        field.annotations = JacksonUtil.readAnnotations(parser);
                        break;
                    default: throw new RuntimeException("Unexpected field " + fieldName + " in AnnotatedField");
                    }
                }

                result.add(field);
            }
            return result;
        }
    }

    /** Use this to serialize indices to JSON.
     *
     * Necessary because we convert a list (needed for the XML mapping) to a JSON object structure .
     */
    private static class ListMetadataFieldSerializer extends JsonSerializer<List<MetadataField>> {
        @Override
        public void serialize(List<MetadataField> value, JsonGenerator jgen, SerializerProvider provider)
                throws IOException {

            if (value == null)
                return;
            jgen.writeStartObject();
            for (MetadataField field: value) {
                jgen.writeObjectFieldStart(field.name);
                {
                    jgen.writeStringField("fieldName", field.fieldName);
                    jgen.writeBooleanField("isAnnotatedField", field.isAnnotatedField);
                    jgen.writeStringField("displayName", field.displayName);
                    jgen.writeStringField("description", field.description);
                    jgen.writeStringField("uiType", field.uiType);
                    jgen.writeStringField("type", field.type);
                    jgen.writeStringField("analyzer", field.analyzer);
                    jgen.writeStringField("unknownCondition", field.unknownCondition);
                    jgen.writeStringField("unknownValue", field.unknownValue);

                    jgen.writeObjectFieldStart("displayValues");
                    for (Map.Entry<String, String> e: field.displayValues.entrySet()) {
                        jgen.writeStringField(e.getKey(), e.getValue());
                    }
                    jgen.writeEndObject();

                    jgen.writeObjectFieldStart("fieldValues");
                    for (Map.Entry<String, Integer> e: field.fieldValues.entrySet()) {
                        jgen.writeNumberField(e.getKey(), e.getValue());
                    }
                    jgen.writeEndObject();

                    jgen.writeBooleanField("valueListComplete", field.valueListComplete);
                }
                jgen.writeEndObject();
            }
            jgen.writeEndObject();
        }
    }

    /** Use this to deserialize indices from JSON.
     *
     * Necessary because we convert a JSON object structure to a list (because that's what the XML mapping uses).
     */
    private static class ListMetadataFieldDeserializer extends JsonDeserializer<List<MetadataField>> {

        @Override
        public List<MetadataField> deserialize(JsonParser parser, DeserializationContext deserializationContext)
                throws IOException {

            JsonToken token = parser.currentToken();
            if (token != JsonToken.START_OBJECT)
                throw new RuntimeException("Expected START_OBJECT, found " + token);

            List<MetadataField> result = new ArrayList<>();
            while (true) {
                token = parser.nextToken();
                if (token == JsonToken.END_OBJECT)
                    break;

                if (token != JsonToken.FIELD_NAME)
                    throw new RuntimeException("Expected END_OBJECT or FIELD_NAME, found " + token);
                MetadataField field = new MetadataField();
                field.name = parser.getCurrentName();

                token = parser.nextToken();
                if (token != JsonToken.START_OBJECT)
                    throw new RuntimeException("Expected END_OBJECT or START_OBJECT, found " + token);
                while (true) {
                    token = parser.nextToken();
                    if (token == JsonToken.END_OBJECT)
                        break;

                    if (token != JsonToken.FIELD_NAME)
                        throw new RuntimeException("Expected END_OBJECT or FIELD_NAME, found " + token);
                    String fieldName = parser.getCurrentName();
                    token = parser.nextToken();
                    switch (fieldName) {
                    case "fieldName": field.fieldName = parser.getValueAsString(); break;
                    case "isAnnotatedField": field.isAnnotatedField = parser.getValueAsBoolean(); break;
                    case "displayName": field.displayName = parser.getValueAsString(); break;
                    case "description": field.description = parser.getValueAsString(); break;
                    case "uiType": field.uiType = parser.getValueAsString(); break;
                    case "type": field.type = parser.getValueAsString(); break;
                    case "analyzer": field.analyzer = parser.getValueAsString(); break;
                    case "unknownCondition": field.unknownCondition = parser.getValueAsString(); break;
                    case "unknownValue": field.unknownValue = parser.getValueAsString(); break;
                    case "displayValues":
                        if (token != JsonToken.START_OBJECT)
                            throw new RuntimeException("Expected START_OBJECT, found " + token);
                        field.displayValues = JacksonUtil.readStringMap(parser);
                        break;
                    case "fieldValues":
                        if (token != JsonToken.START_OBJECT)
                            throw new RuntimeException("Expected START_OBJECT, found " + token);
                        field.fieldValues = JacksonUtil.readIntegerMap(parser);
                        break;
                    case "valueListComplete": field.valueListComplete = parser.getValueAsBoolean(); break;
                    default: throw new RuntimeException("Unexpected field " + fieldName + " in MetadataField");
                    }
                }
                result.add(field);
            }
            return result;
        }
    }

    public String indexName = "";

    public String displayName = "";

    public String description = "";

    public String status = "available";

    public boolean contentViewable = false;

    public String textDirection = "ltr";

    public String documentFormat = "tei";

    public long tokenCount = 0;

    public long documentCount = 0;

    public VersionInfo versionInfo;

    public FieldInfo fieldInfo;

    @XmlElementWrapper(name="annotatedFields")
    @XmlElement(name = "annotatedField")
    @JsonProperty("annotatedFields")
    @JsonSerialize(using = ListAnnotatedFieldSerializer.class)
    @JsonDeserialize(using = ListAnnotatedFieldDeserializer.class)
    public List<AnnotatedField> annotatedFields;

    @XmlElementWrapper(name="metadataFields")
    @XmlElement(name = "metadataField")
    @JsonProperty("metadataFields")
    @JsonSerialize(using = ListMetadataFieldSerializer.class)
    @JsonDeserialize(using = ListMetadataFieldDeserializer.class)
    public List<MetadataField> metadataFields;

    @XmlElementWrapper(name="metadataFieldGroups")
    @XmlElement(name = "metadataFieldGroup")
    @JsonProperty("metadataFieldGroups")
    public List<MetadataFieldGroup> metadataFieldGroups;

    //@XmlElementWrapper(name="annotationGroups")
    @XmlElement(name = "annotatedField")
    @JsonProperty("annotationGroups")
    @JsonSerialize(using = ListAnnotatedFieldAnnotationGroupsSerializer.class)
    @JsonDeserialize(using = ListAnnotatedFieldAnnotationGroupsDeserializer.class)
    public List<AnnotatedFieldAnnotationGroups> annotationGroups;

    // required for Jersey
    @SuppressWarnings("unused")
    private Index() {}

    public Index(String indexName, FieldInfo fieldInfo,
            List<AnnotatedField> annotatedFields, List<MetadataField> metadataFields) {
        this.indexName = indexName;
        this.fieldInfo = fieldInfo;
        this.annotatedFields = annotatedFields;
        this.metadataFields = metadataFields;
    }

    @Override
    public Index clone() throws CloneNotSupportedException {
        return (Index)super.clone();
    }

    @Override
    public String toString() {
        return "Index{" +
                "indexName='" + indexName + '\'' +
                ", displayName='" + displayName + '\'' +
                ", description='" + description + '\'' +
                ", status='" + status + '\'' +
                ", contentViewable=" + contentViewable +
                ", textDirection='" + textDirection + '\'' +
                ", documentFormat='" + documentFormat + '\'' +
                ", tokenCount=" + tokenCount +
                ", documentCount=" + documentCount +
                ", versionInfo=" + versionInfo +
                ", fieldInfo=" + fieldInfo +
                ", annotatedFields=" + annotatedFields +
                ", metadataFields=" + metadataFields +
                ", metadataFieldGroups=" + metadataFieldGroups +
                ", annotationGroups=" + annotationGroups +
                '}';
    }
}
