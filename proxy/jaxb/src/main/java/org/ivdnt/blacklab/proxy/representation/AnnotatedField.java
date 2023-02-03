package org.ivdnt.blacklab.proxy.representation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.commons.lang3.StringUtils;
import org.ivdnt.blacklab.proxy.helper.SerializationUtil;

import com.fasterxml.jackson.annotation.JsonInclude;
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
public class AnnotatedField implements Cloneable {

    /** Use this to serialize annotatedFields to JSON.
     *
     * Necessary because we convert a list (needed for the XML mapping) to a JSON object structure.
     */
    private static class ListAnnotationSerializer extends JsonSerializer<List<Annotation>> {
        @Override
        public void serialize(List<Annotation> value, JsonGenerator jgen, SerializerProvider provider)
                throws IOException {

            if (value == null)
                return;
            jgen.writeStartObject();
            for (Annotation a: value) {
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
    }

    /** Use this to deserialize annotatedFields from JSON.
     *
     * Necessary because we convert a JSON object structure to a list (because that's what the XML mapping uses).
     */
    private static class ListAnnotationDeserializer extends JsonDeserializer<List<Annotation>> {
        @Override
        public List<Annotation> deserialize(JsonParser parser, DeserializationContext deserializationContext)
                throws IOException {

            JsonToken token = parser.currentToken();
            if (token != JsonToken.START_OBJECT)
                throw new RuntimeException("Expected START_OBJECT, found " + token);
            return SerializationUtil.readAnnotations(parser);
        }
    }

    @XmlAttribute
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String name;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String indexName;

    public String fieldName;

    public boolean isAnnotatedField = true;

    public String displayName = "";

    public String description = "";

    public boolean hasContentStore;

    public boolean hasXmlTags;

    public boolean hasLengthTokens;

    public String mainAnnotation = "";

    @XmlElementWrapper(name="displayOrder")
    @XmlElement(name = "fieldName")
    @JsonProperty("displayOrder")
    public List<String> displayOrder = new ArrayList<>();

    @XmlElementWrapper(name="annotations")
    @XmlElement(name = "annotation")
    @JsonProperty("annotations")
    @JsonSerialize(using = ListAnnotationSerializer.class)
    @JsonDeserialize(using = ListAnnotationDeserializer.class)
    public List<Annotation> annotations = new ArrayList<>();

    @Override
    public String toString() {
        return "AnnotatedField{" +
                "name='" + name + '\'' +
                ", indexName='" + indexName + '\'' +
                ", fieldName='" + fieldName + '\'' +
                ", isAnnotatedField=" + isAnnotatedField +
                ", displayName='" + displayName + '\'' +
                ", description='" + description + '\'' +
                ", hasContentStore=" + hasContentStore +
                ", hasXmlTags=" + hasXmlTags +
                ", hasLengthTokens=" + hasLengthTokens +
                ", mainAnnotation='" + mainAnnotation + '\'' +
                ", displayOrder=" + displayOrder +
                ", annotations=" + annotations +
                '}';
    }

    private AnnotatedField() {}

    @Override
    public AnnotatedField clone() {
        try {
            return (AnnotatedField)super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    public AnnotatedField(String name) {
        this.name = this.fieldName = name;
    }
}
