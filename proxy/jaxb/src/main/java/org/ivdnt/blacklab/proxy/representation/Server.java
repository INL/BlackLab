package org.ivdnt.blacklab.proxy.representation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;

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
@XmlType(propOrder={"blacklabBuildTime", "blacklabVersion", "apiImplementation", "indices", "user", "helpPageUrl" })
//@JsonIgnoreProperties(ignoreUnknown = true)
public class Server implements Cloneable {

    /** Use this to serialize indices to JSON.
     *
     * Necessary because we convert a list (needed for the XML mapping) to a JSON object structure .
     */
    private static class ListCorpusSummarySerializer extends JsonSerializer<List<CorpusSummary>> {
        @Override
        public void serialize(List<CorpusSummary> value, JsonGenerator jgen, SerializerProvider provider)
                throws IOException {

            if (value == null)
                return;
            jgen.writeStartObject();
            for (CorpusSummary corpus: value) {
                jgen.writeFieldName(corpus.name);
                provider.defaultSerializeValue(corpus, jgen);
            }
            jgen.writeEndObject();
        }
    }

    /** Use this to deserialize indices from JSON.
     *
     * Necessary because we convert a JSON object structure to a list (because that's what the XML mapping uses).
     */
    private static class ListCorpusSummaryDeserializer extends JsonDeserializer<List<CorpusSummary>> {

        @Override
        public List<CorpusSummary> deserialize(JsonParser parser, DeserializationContext deserializationContext)
                throws IOException {

            JsonToken token = parser.currentToken();
            if (token != JsonToken.START_OBJECT)
                throw new RuntimeException("Expected START_OBJECT, found " + token);

            List<CorpusSummary> result = new ArrayList<>();
            while (true) {
                token = parser.nextToken();
                if (token == JsonToken.END_OBJECT)
                    break;

                if (token != JsonToken.FIELD_NAME)
                    throw new RuntimeException("Expected END_OBJECT or FIELD_NAME, found " + token);
                //CorpusSummary corpus = new CorpusSummary();
                String corpusName = parser.getCurrentName();

                token = parser.nextToken();
                if (token != JsonToken.START_OBJECT)
                    throw new RuntimeException("Expected END_OBJECT or START_OBJECT, found " + token);

                CorpusSummary corpus = deserializationContext.readValue(parser, CorpusSummary.class);
                corpus.name = corpusName;
                result.add(corpus);
            }
            return result;
        }
    }

    @XmlElement
    public String blacklabBuildTime;

    @XmlElement
    public String blacklabVersion;

    @XmlElement
    public String apiImplementation = "aggregator-experimental";

    @XmlElementWrapper(name="indices")
    @XmlElement(name = "index")
    @JsonProperty("indices")
    @JsonSerialize(using= ListCorpusSummarySerializer.class)
    @JsonDeserialize(using= ListCorpusSummaryDeserializer.class)
    public List<CorpusSummary> indices;

    @XmlElement
    public User user;

    @SuppressWarnings("unused")
    @XmlElement
    public String helpPageUrl;

    @SuppressWarnings("unused")
    @XmlTransient
    public Object cacheStatus;

    // required for Jersey
    @SuppressWarnings("unused")
    private Server() {}

    public Server(String blacklabBuildTime, String blacklabVersion,
            List<CorpusSummary> indices, User user) {
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

    @Override
    public Server clone() throws CloneNotSupportedException {
        return (Server)super.clone();
    }
}
