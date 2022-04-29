package org.ivdnt.blacklab.aggregator.representation;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class Hit {

    private String docPid = "";

    private long start = 0;

    private long end = 0;

    private ContextWords left = new ContextWords();

    private ContextWords match = new ContextWords();

    private ContextWords right = new ContextWords();

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
