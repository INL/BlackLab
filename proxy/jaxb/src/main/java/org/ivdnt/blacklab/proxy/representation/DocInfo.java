package org.ivdnt.blacklab.proxy.representation;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.ivdnt.blacklab.proxy.helper.DocInfoAdapter;
import org.ivdnt.blacklab.proxy.helper.MapAdapterMetadataValues;
import org.ivdnt.blacklab.proxy.helper.SerializationUtil;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
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
@XmlAccessorType()
@XmlJavaTypeAdapter(DocInfoAdapter.class)
@JsonSerialize(using=DocInfo.Serializer.class)
@JsonDeserialize(using=DocInfo.Deserializer.class)
public class DocInfo {

    /** Use this to serialize this class to JSON */
    public static class Serializer extends JsonSerializer<DocInfo> {
        @Override
        public void serialize(DocInfo el, JsonGenerator jgen, SerializerProvider provider)
                throws IOException {

            if (el == null)
                return;
            jgen.writeStartObject();
            for (Map.Entry<String, MetadataValues> v: el.metadata.entrySet()) {
                jgen.writeArrayFieldStart(v.getKey());
                for (String x: v.getValue().getValue()) {
                    jgen.writeString(x);
                }
                jgen.writeEndArray();
            }
            if (el.lengthInTokens != null)
                jgen.writeNumberField("lengthInTokens", el.lengthInTokens);
            if (el.mayView != null)
                jgen.writeBooleanField("mayView", el.mayView);
            jgen.writeEndObject();
        }
    }

    public static class Deserializer extends JsonDeserializer<DocInfo> {
        @Override
        public DocInfo deserialize(JsonParser parser, DeserializationContext deserializationContext)
                throws IOException {
            JsonToken token = parser.getCurrentToken();
            if (token != JsonToken.START_OBJECT)
                throw new RuntimeException("Expected START_OBJECT, found " + token);

            DocInfo docInfo = new DocInfo();
            docInfo.metadata = new LinkedHashMap<>();

            while (true) {
                token = parser.nextToken();
                if (token == JsonToken.END_OBJECT)
                    break;

                if (token != JsonToken.FIELD_NAME)
                    throw new RuntimeException("Expected END_OBJECT or FIELD_NAME, found " + token);
                String fieldName = parser.getCurrentName();
                token = parser.nextToken();
                if (token == JsonToken.VALUE_NUMBER_INT) {
                    // Special lengthInTokens setting?
                    if (!fieldName.equals("lengthInTokens"))
                        throw new RuntimeException("Unexpected int in metadata");
                    docInfo.lengthInTokens = parser.getValueAsInt();
                } else if (token == JsonToken.VALUE_TRUE || token == JsonToken.VALUE_FALSE) {
                    // Special mayView setting?
                    if (!fieldName.equals("mayView"))
                        throw new RuntimeException("Unexpected boolean in metadata");
                    docInfo.mayView = parser.getValueAsBoolean();
                } else if (token == JsonToken.START_ARRAY) {
                    // A list of metadata values
                    List<String> values = SerializationUtil.readStringList(parser);
                    docInfo.metadata.put(fieldName, new MetadataValues(values));
                }
            }
            return docInfo;
        }
    }

    @XmlAttribute
    public String pid;

    @XmlJavaTypeAdapter(MapAdapterMetadataValues.class)
    public Map<String, MetadataValues> metadata;

    @JsonInclude(Include.NON_NULL)
    public Integer lengthInTokens;

    @JsonInclude(Include.NON_NULL)
    public Boolean mayView;

    public DocInfo() {}

    public DocInfo(String pid, Map<String, MetadataValues> metadata) {
        this.pid = pid;
        this.metadata = metadata;
    }

    public MetadataValues get(String field) {
        return metadata.get(field);
    }

    @Override
    public String toString() {
        return "DocInfo{" +
                "pid='" + pid + '\'' +
                ", metadata=" + metadata +
                '}';
    }

}
