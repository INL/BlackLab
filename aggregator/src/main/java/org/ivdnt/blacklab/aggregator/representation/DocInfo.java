package org.ivdnt.blacklab.aggregator.representation;

import java.util.Map;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.ivdnt.blacklab.aggregator.helper.MapAdapterMetadataValues;

@XmlAccessorType(XmlAccessType.FIELD)
public class DocInfo {

    @XmlAttribute
    public String pid;

    @XmlJavaTypeAdapter(MapAdapterMetadataValues.class)
    public Map<String, MetadataValues> metadata;

    public int lengthInTokens;

    public boolean mayView;

    public DocInfo() {}

    public DocInfo(String pid, Map<String, MetadataValues> metadata) {
        this.pid = pid;
        this.metadata = metadata;
    }

    @Override
    public String toString() {
        return "DocInfo{" +
                "pid='" + pid + '\'' +
                ", metadata=" + metadata +
                '}';
    }

}
