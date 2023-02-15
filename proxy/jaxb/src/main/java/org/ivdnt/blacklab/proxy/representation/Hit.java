package org.ivdnt.blacklab.proxy.representation;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlTransient;

import com.fasterxml.jackson.annotation.JsonInclude;

@XmlAccessorType(XmlAccessType.FIELD)
public class Hit  {

    public String docPid;

    @XmlTransient
    public DocInfo docInfo;

    public long start;

    public long end;

    @XmlElementWrapper(name="captureGroups")
    @XmlElement(name = "captureGroup")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public List<CaptureGroup> captureGroups;

    public ContextWords left;

    public ContextWords match;

    public ContextWords right;

    // required for Jersey
    public Hit() {}

    public Hit(String docPid, long start, long end) {
        this.docPid = docPid;
        this.start = start;
        this.end = end;
    }

    @Override
    public String toString() {
        return "Hit{" +
                "docPid='" + docPid + '\'' +
                ", docInfo=" + docInfo +
                ", start=" + start +
                ", end=" + end +
                ", captureGroups=" + captureGroups +
                ", left=" + left +
                ", match=" + match +
                ", right=" + right +
                '}';
    }
}
