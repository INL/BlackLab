package org.ivdnt.blacklab.aggregator.representation;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;

import com.fasterxml.jackson.annotation.JsonProperty;

@XmlAccessorType(XmlAccessType.FIELD)
public class AnnotatedFieldAnnotationGroups {

    @XmlAttribute
    public String name;

    //@XmlElementWrapper(name="annotationGroups")
    @XmlElement(name = "annotationGroup")
    @JsonProperty("annotationGroups")
    public List<AnnotationGroup> annotationGroups;

    @Override
    public String toString() {
        return "AnnotatedFieldAnnotationGroups{" +
                "name='" + name + '\'' +
                ", annotationGroups=" + annotationGroups +
                '}';
    }
}
