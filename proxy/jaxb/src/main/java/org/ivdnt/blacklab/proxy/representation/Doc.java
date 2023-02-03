package org.ivdnt.blacklab.proxy.representation;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class Doc {

    public String docPid;

    public long numberOfHits;

    public DocInfo docInfo;

    public List<DocSnippet> snippets;

    // required for Jersey
    public Doc() {}

    @Override
    public String toString() {
        return "Doc{" +
                "docPid='" + docPid + '\'' +
                ", numberOfHits=" + numberOfHits +
                ", docInfo=" + docInfo +
                ", snippets=" + snippets +
                '}';
    }
}
