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

    // doesn't work:
    // @XmlAnyElement
    // Map<QName, String> bla = Map.of(new QName("test1"), "v1", new QName("test2"), "v2");

    public DocInfo(String pid, Map<String, MetadataValues> metadata) {
        this.pid = pid;
        this.metadata = metadata;
    }

}
