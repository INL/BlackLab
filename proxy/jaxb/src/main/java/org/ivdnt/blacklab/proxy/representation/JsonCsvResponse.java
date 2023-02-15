package org.ivdnt.blacklab.proxy.representation;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name="blacklabResponse")
@XmlAccessorType(XmlAccessType.FIELD)
public class JsonCsvResponse {

    public String csv;

    // required for Jersey
    public JsonCsvResponse() {}

    @Override
    public String toString() {
        return "JsonCsvResponse{" +
                "csv='" + csv + '\'' +
                '}';
    }
}
