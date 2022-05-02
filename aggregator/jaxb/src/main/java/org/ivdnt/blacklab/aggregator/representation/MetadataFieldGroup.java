package org.ivdnt.blacklab.aggregator.representation;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

import com.fasterxml.jackson.annotation.JsonProperty;

@XmlAccessorType(XmlAccessType.FIELD)
public class MetadataFieldGroup {

    public String name = "group1";

    @XmlElementWrapper(name="fields")
    @XmlElement(name = "field")
    @JsonProperty("fields")
    public List<String> fields;

    @Override
    public String toString() {
        return "MetadataFieldGroup{" +
                "name='" + name + '\'' +
                ", fields=" + fields +
                '}';
    }
}
