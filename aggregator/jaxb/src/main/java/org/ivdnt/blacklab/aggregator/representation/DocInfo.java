package org.ivdnt.blacklab.aggregator.representation;

import java.util.Map;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.ivdnt.blacklab.aggregator.helper.MapAdapterMetadataValues;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@XmlAccessorType(XmlAccessType.FIELD)
public class DocInfo {

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
