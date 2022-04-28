package org.ivdnt.blacklab.aggregator.representation;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@XmlRootElement(name="blacklabResponse")
@XmlAccessorType(XmlAccessType.FIELD)
public class HitsResults {

    /** Use this to serialize this class to JSON */
    private static class Serializer extends JsonSerializer<List<DocInfo>> {
        @Override
        public void serialize(List<DocInfo> value, JsonGenerator jgen, SerializerProvider provider)
                throws IOException {

            if (value == null)
                return;
            jgen.writeStartObject();
            for (DocInfo el: value) {
                String pid = el.pid;
                if (pid == null)
                    pid = "UNKNOWN";
                jgen.writeObjectFieldStart(pid);
                for (Map.Entry<String, MetadataValues> v: el.metadata.entrySet()) {
                    jgen.writeArrayFieldStart(v.getKey());
                    for (String x: v.getValue().getValues()) {
                        jgen.writeString(x);
                    }
                    jgen.writeEndArray();
                }
                jgen.writeEndObject();
            }
            jgen.writeEndObject();
        }
    }

    private SearchSummary summary = new SearchSummary();

    @XmlElementWrapper(name="hits")
    @XmlElement(name = "hit")
    @JsonProperty("hits")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<Hit> hits = Collections.emptyList();

    @XmlElementWrapper(name="docInfos")
    @XmlElement(name = "docInfo")
    @JsonProperty("docInfos")
    @JsonSerialize(using = Serializer.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<DocInfo> docInfos = Collections.emptyList();

    @XmlElementWrapper(name="hitGroups")
    @XmlElement(name = "hitGroup")
    @JsonProperty("hitGroups")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<HitGroup> hitGroups;

    // required for Jersey
    public HitsResults() {}

    public HitsResults(SearchSummary summary, List<Hit> hits,
            List<DocInfo> docInfos) {
        this.summary = summary;
        this.hits = hits;
        this.docInfos = docInfos;
        this.hitGroups = null;
    }

    public HitsResults(SearchSummary summary, List<HitGroup> groups) {
        this.summary = summary;
        this.hits = null;
        this.docInfos = null;
        this.hitGroups = groups;
    }
}
