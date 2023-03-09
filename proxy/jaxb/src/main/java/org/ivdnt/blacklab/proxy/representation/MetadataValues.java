package org.ivdnt.blacklab.proxy.representation;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

import com.fasterxml.jackson.annotation.JsonValue;

@XmlAccessorType(XmlAccessType.FIELD)
public class MetadataValues {

    @XmlElement(name = "value")
    @JsonValue
    public List<String> value;

    @SuppressWarnings("unused")
    private MetadataValues() { }

    public MetadataValues(List<String> value) {
        this.value = value;
    }

    public List<String> getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "MetadataValues{" +
                "value=" + value +
                '}';
    }
}
