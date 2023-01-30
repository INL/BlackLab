package org.ivdnt.blacklab.proxy.representation;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlTransient;

@XmlAccessorType(XmlAccessType.FIELD)
public class Hit  {

    public String docPid;

    @XmlTransient
    public DocInfo docInfo;

    public long start;

    public long end;

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

    public int compareTo(Hit other) {
        if (docPid.equals(other.docPid)) {
            if (start == other.start)
                return Long.compare(end, other.end);
            return Long.compare(end, other.end);
        }
        return docPid.compareTo(other.docPid);
    }

    @Override
    public String toString() {
        return "Hit{" +
                "docPid='" + docPid + '\'' +
                ", start=" + start +
                ", end=" + end +
                ", left=" + left +
                ", match=" + match +
                ", right=" + right +
                '}';
    }
}
