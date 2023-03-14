package org.ivdnt.blacklab.proxy.representation;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.ivdnt.blacklab.proxy.helper.MapAdapter;
import org.ivdnt.blacklab.proxy.helper.MapAdapterFieldValues;
import org.ivdnt.blacklab.proxy.helper.SerializationUtil;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@XmlRootElement(name="blacklabResponse")
@XmlAccessorType(XmlAccessType.FIELD)
@JsonIgnoreProperties(value = { "name" }) // this is the key of the object
public class MetadataField implements Cloneable {

    public static class FieldValuesSerializer extends JsonSerializer<Object> {
        @Override
        public void serialize(Object value, JsonGenerator jgen, SerializerProvider provider)
                throws IOException {
            if (value instanceof MapAdapterFieldValues.WrapperFieldValues) {
                // This happens because of using MapAdapterFieldValues with Jersey
                jgen.writeStartObject();
                for (MapAdapterFieldValues.FieldValueFreq item: ((MapAdapterFieldValues.WrapperFieldValues) value).elements) {
                    jgen.writeNumberField(item.text, item.freq);
                }
                jgen.writeEndObject();
            } else if (value instanceof Map) {
                // This happens if we use Jackson directly without Jersey
                jgen.writeStartObject();
                for (Map.Entry<String, Integer> entry: ((Map<String, Integer>) value).entrySet()) {
                    jgen.writeNumberField(entry.getKey(), entry.getValue());
                }
                jgen.writeEndObject();
            }
        }
    }

    public static class FieldValuesDeserializer extends JsonDeserializer<Map<String, Integer>> {

        @Override
        public Map<String, Integer> deserialize(JsonParser parser, DeserializationContext deserializationContext)
                throws IOException {
            return SerializationUtil.readIntegerMap(parser);
        }
    }


    @XmlAttribute
    public String name = "title";

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String indexName;

    public String fieldName = "title";

    public boolean isAnnotatedField = false;

    public String displayName = "Title";

    public String description = "Document title";

    public String uiType = "";

    public String type = "";

    public String analyzer = "";

    public String unknownCondition = "";

    public String unknownValue = "";

    @XmlJavaTypeAdapter(MapAdapter.class)
    @JsonSerialize(using = SerializationUtil.StringMapSerializer.class)
    @JsonDeserialize(using = SerializationUtil.StringMapDeserializer.class)
    public Map<String, String> displayValues = new LinkedHashMap<>();

    @XmlJavaTypeAdapter(MapAdapterFieldValues.class)
    @JsonSerialize(using = FieldValuesSerializer.class)
    @JsonDeserialize(using = FieldValuesDeserializer.class)
    public Map<String, Integer> fieldValues = new LinkedHashMap<>();

    public boolean valueListComplete = true;

    @Override
    public MetadataField clone() {
        try {
            return (MetadataField)super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String toString() {
        return "MetadataField{" +
                "name='" + name + '\'' +
                ", indexName='" + indexName + '\'' +
                ", fieldName='" + fieldName + '\'' +
                ", isAnnotatedField=" + isAnnotatedField +
                ", displayName='" + displayName + '\'' +
                ", description='" + description + '\'' +
                ", uiType='" + uiType + '\'' +
                ", type='" + type + '\'' +
                ", analyzer='" + analyzer + '\'' +
                ", unknownCondition='" + unknownCondition + '\'' +
                ", unknownValue='" + unknownValue + '\'' +
                ", displayValues=" + displayValues +
                ", fieldValues=" + fieldValues +
                ", valueListComplete=" + valueListComplete +
                '}';
    }
}
