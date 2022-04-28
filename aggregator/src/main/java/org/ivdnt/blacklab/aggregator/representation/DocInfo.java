package org.ivdnt.blacklab.aggregator.representation;

import java.util.Collections;
import java.util.Map;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.ivdnt.blacklab.aggregator.helper.MapAdapterMetadataValues;

@XmlAccessorType(XmlAccessType.FIELD)
public class DocInfo {

    @XmlAttribute
    String pid;

    @XmlJavaTypeAdapter(MapAdapterMetadataValues.class)
    Map<String, MetadataValues> metadata = Collections.emptyMap();

    public DocInfo(String pid, Map<String, MetadataValues> metadata) {
        this.pid = pid;
        this.metadata = metadata;
    }

}
