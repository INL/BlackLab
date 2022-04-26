package org.ivdnt.blacklab.aggregator.representation;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;

@XmlAccessorType(XmlAccessType.FIELD)
public class IndexSummary {

    @XmlAttribute
    private String name = "";

    private String displayName = "";

    private String description = "";

    private String status = "available";

    private String documentFormat = "";

    private String timeModified = "";

    private long tokenCount = 0;

    // required for Jersey
    @SuppressWarnings("unused")
    private IndexSummary() {}

    public IndexSummary(String name, String displayName, String documentFormat) {
        this.name = name;
        this.displayName = displayName;
        this.documentFormat = documentFormat;
    }
}
