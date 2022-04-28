package org.ivdnt.blacklab.aggregator.representation;

import java.util.Collections;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;

@XmlAccessorType(XmlAccessType.FIELD)
public class DocInfo {

    @XmlAttribute
    private String pid;

    //@XmlJavaTypeAdapter(MapAdapter.class)
    private List<MetadataEntry> metadata = Collections.emptyList();

    public DocInfo(String pid, List<MetadataEntry> metadata) {
        this.pid = pid;
        this.metadata = metadata;
    }
}
