package org.ivdnt.blacklab.aggregator.representation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class Server {

    /** Use this to serialize this class to JSON.
     *
     * Necessary because we convert a list (needed for the XML mapping) to a JSON object structure .
     */
    private static class ListIndexSummarySerializer extends JsonSerializer<List<IndexSummary>> {
        @Override
        public void serialize(List<IndexSummary> value, JsonGenerator jgen, SerializerProvider provider)
                throws IOException {

            if (value == null)
                return;
            jgen.writeStartObject();
            for (IndexSummary index: value) {
                jgen.writeObjectFieldStart(index.name);
                jgen.writeStringField("displayName", index.displayName);
                jgen.writeStringField("description", index.description);
                jgen.writeStringField("status", index.status);
                jgen.writeStringField("documentFormat", index.documentFormat);
                jgen.writeStringField("timeModified", index.timeModified);
                jgen.writeNumberField("tokenCount", index.tokenCount);
                jgen.writeEndObject();
            }
            jgen.writeEndObject();
        }
    }

    /** Use this to deserialize this class from JSON.
     *
     * Necessary because we convert a JSON object structure to a list (because that's what the XML mapping uses).
     */
    private static class ListIndexSummaryDeserializer extends JsonDeserializer<List<IndexSummary>> {

        @Override
        public List<IndexSummary> deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
                throws IOException {

            JsonToken token = jsonParser.currentToken();
            if (token != JsonToken.START_OBJECT)
                throw new RuntimeException("Expected START_OBJECT, found " + token);

            List<IndexSummary> result = new ArrayList<>();
            while (true) {
                token = jsonParser.nextToken();
                if (token == JsonToken.END_OBJECT)
                    break;

                if (token != JsonToken.FIELD_NAME)
                    throw new RuntimeException("Expected END_OBJECT or FIELD_NAME, found " + token);
                IndexSummary index = new IndexSummary();
                index.name = jsonParser.getCurrentName();

                token = jsonParser.nextToken();
                if (token != JsonToken.START_OBJECT)
                    throw new RuntimeException("Expected END_OBJECT or START_OBJECT, found " + token);
                while (true) {
                    token = jsonParser.nextToken();
                    if (token == JsonToken.END_OBJECT)
                        break;

                    if (token != JsonToken.FIELD_NAME)
                        throw new RuntimeException("Expected END_OBJECT or FIELD_NAME, found " + token);
                    String fieldName = jsonParser.getCurrentName();
                    jsonParser.nextToken();
                    switch (fieldName) {
                    case "displayName": index.displayName = jsonParser.getValueAsString(); break;
                    case "description": index.description = jsonParser.getValueAsString(); break;
                    case "status": index.status = jsonParser.getValueAsString(); break;
                    case "documentFormat": index.documentFormat = jsonParser.getValueAsString(); break;
                    case "timeModified": index.timeModified = jsonParser.getValueAsString(); break;
                    case "tokenCount": index.tokenCount = jsonParser.getValueAsLong(); break;
                    default: throw new RuntimeException("Unexpected field " + fieldName + " in IndexSummary");
                    }
                }
                result.add(index);
            }
            return result;
        }
    }

    private String blacklabBuildTime;

    private String blacklabVersion;

    @XmlElementWrapper(name="indices")
    @XmlElement(name = "index")
    @JsonProperty("indices")
    @JsonSerialize(using=ListIndexSummarySerializer.class)
    @JsonDeserialize(using=ListIndexSummaryDeserializer.class)
    private List<IndexSummary> indices;

    private User user;

    // Collect helpPageUrl, cacheStatus here, we ignore those
    @XmlAnyElement(lax = true)
    private List<Object> anything;

    // required for Jersey
    @SuppressWarnings("unused")
    private Server() {}

    public Server(String blacklabBuildTime, String blacklabVersion,
            List<IndexSummary> indices, User user) {
        this.blacklabBuildTime = blacklabBuildTime;
        this.blacklabVersion = blacklabVersion;
        this.indices = indices;
        this.user = user;
    }

    @Override
    public String toString() {
        return "Server{" +
                "blacklabBuildTime='" + blacklabBuildTime + '\'' +
                ", blacklabVersion='" + blacklabVersion + '\'' +
                ", indices=" + indices +
                ", user=" + user +
                '}';
    }
}
