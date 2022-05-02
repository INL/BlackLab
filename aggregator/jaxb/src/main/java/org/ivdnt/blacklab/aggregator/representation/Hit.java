package org.ivdnt.blacklab.aggregator.representation;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class Hit {

    public String docPid;

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
