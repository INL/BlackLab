package org.ivdnt.blacklab.aggregator.representation;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

import com.fasterxml.jackson.annotation.JsonValue;

@XmlAccessorType(XmlAccessType.FIELD)
@SuppressWarnings("unused")
public class MetadataValues {

    //@XmlElementWrapper(name="indices")
    @XmlElement(name = "value")
    @JsonValue
    private List<String> value;

    private MetadataValues() { }

    public MetadataValues(List<String> value) {
        this.value = value;
    }

    public List<String> getValues() {
        return value;
    }
}
