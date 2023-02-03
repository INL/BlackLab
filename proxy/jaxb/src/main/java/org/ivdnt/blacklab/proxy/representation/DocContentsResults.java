package org.ivdnt.blacklab.proxy.representation;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name="blacklabResponse")
@XmlAccessorType(XmlAccessType.FIELD)
public class DocContentsResults {

    public String contents;

    // required for Jersey
    public DocContentsResults() {}

    @Override
    public String toString() {
        return "DocContents{" +
                "contents='" + contents + '\'' +
                '}';
    }
}
