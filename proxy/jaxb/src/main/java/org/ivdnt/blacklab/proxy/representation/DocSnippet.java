package org.ivdnt.blacklab.proxy.representation;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class DocSnippet {

    public ContextWords left;

    public ContextWords match;

    public ContextWords right;

    // required for Jersey
    public DocSnippet() {}

    @Override
    public String toString() {
        return "DocSnippet{" +
                ", left=" + left +
                ", match=" + match +
                ", right=" + right +
                '}';
    }
}
