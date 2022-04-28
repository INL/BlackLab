package org.ivdnt.blacklab.aggregator.representation;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;

@XmlAccessorType(XmlAccessType.FIELD)
public class IndexSummary {

    @XmlAttribute
    String name = "";

    String displayName = "";

    String description = "";

    String status = "available";

    String documentFormat = "";

    String timeModified = "";

    long tokenCount = 0;

    // required for Jersey
    IndexSummary() {}

    public IndexSummary(String name, String displayName, String documentFormat) {
        this.name = name;
        this.displayName = displayName;
        this.documentFormat = documentFormat;
    }
}
