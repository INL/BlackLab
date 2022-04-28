package org.ivdnt.blacklab.aggregator.representation;

import java.io.IOException;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@XmlRootElement(name="blacklabResponse")
@XmlAccessorType(XmlAccessType.FIELD)
public class Server {

    /** Use this to serialize this class to JSON */
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

    /** Use this to deserialize this class from JSON
    private static class ListIndexSummaryDeserializer extends JsonDeserializer<List<IndexSummary>> {

        @Override
        public List<IndexSummary> deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
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
    }*/

    private String blacklabBuildTime;

    private String blacklabVersion;

    @XmlElementWrapper(name="indices")
    @XmlElement(name = "index")
    @JsonProperty("indices")
    @JsonSerialize(using=ListIndexSummarySerializer.class)
    //@JsonDeserialize(using=ListIndexSummaryDeserializer.class)
    private List<IndexSummary> indices;

    private User user;

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
