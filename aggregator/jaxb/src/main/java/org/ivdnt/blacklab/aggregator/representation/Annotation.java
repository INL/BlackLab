package org.ivdnt.blacklab.aggregator.representation;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

import com.fasterxml.jackson.annotation.JsonProperty;

@XmlAccessorType(XmlAccessType.FIELD)
public class Annotation {

    @XmlAttribute
    public String name;

    public String displayName = "";

    public String description = "";

    public String uiType = "";

    public boolean hasForwardIndex;

    public String sensitivity;

    public String offsetsAlternative;

    public boolean isInternal;

    @XmlElementWrapper(name="subannotations")
    @XmlElement(name = "subannotation")
    @JsonProperty("subannotations")
    public List<String> subannotations;

    public String parentAnnotation = "";

    @Override
    public String toString() {
        return "Annotation{" +
                "name='" + name + '\'' +
                ", displayName='" + displayName + '\'' +
                ", description='" + description + '\'' +
                ", uiType='" + uiType + '\'' +
                ", hasForwardIndex=" + hasForwardIndex +
                ", sensitivity='" + sensitivity + '\'' +
                ", offsetsAlternative='" + offsetsAlternative + '\'' +
                ", isInternal=" + isInternal +
                ", subannotations=" + subannotations +
                ", parentAnnotation=" + parentAnnotation +
                '}';
    }
}
