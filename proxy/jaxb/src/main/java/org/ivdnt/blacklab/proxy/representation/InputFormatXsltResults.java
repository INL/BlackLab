package org.ivdnt.blacklab.proxy.representation;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name="blacklabResponse")
@XmlAccessorType(XmlAccessType.FIELD)
public class InputFormatXsltResults {

    public String xslt;

    // required for Jersey
    public InputFormatXsltResults() {}

    @Override
    public String toString() {
        return "InputFormatXsltResults{" +
                "xslt='" + xslt + '\'' +
                '}';
    }
}
