package org.ivdnt.blacklab.aggregator.representation;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@XmlAccessorType(XmlAccessType.FIELD)
public class IndexSummary implements Cloneable {

    @XmlAttribute
    public String name = "";

    public String displayName = "";

    public String description = "";

    public String status = "available";

    public String documentFormat = "";

    @JsonInclude(Include.NON_EMPTY)
    public String timeModified = "";

    public long tokenCount = 0;

    // required for Jersey
    IndexSummary() {}

    public IndexSummary(String name, String displayName, String documentFormat) {
        this.name = name;
        this.displayName = displayName;
        this.documentFormat = documentFormat;
    }

    public static IndexSummary merge(IndexSummary i1, IndexSummary i2) {
        IndexSummary cl;
        try {
            cl = (IndexSummary)i1.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
        cl.timeModified = i1.timeModified.compareTo(i2.timeModified) < 0 ? i2.timeModified : i1.timeModified;
        cl.tokenCount = i1.tokenCount + i2.tokenCount;
        return cl;
    }

    @Override
    public String toString() {
        return "IndexSummary{" +
                "name='" + name + '\'' +
                ", displayName='" + displayName + '\'' +
                ", description='" + description + '\'' +
                ", status='" + status + '\'' +
                ", documentFormat='" + documentFormat + '\'' +
                ", timeModified='" + timeModified + '\'' +
                ", tokenCount=" + tokenCount +
                '}';
    }
}
