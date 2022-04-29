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
public class AnnotatedFieldAnnotationGroups {

    @XmlAttribute
    String name;

    @XmlElementWrapper(name="annotationGroups")
    @XmlElement(name = "annotationGroup")
    @JsonProperty("annotationGroups")
    List<AnnotationGroup> annotationGroups = new ArrayList<>();

    @Override
    public String toString() {
        return "AnnotatedFieldAnnotationGroups{" +
                "name='" + name + '\'' +
                ", annotationGroups=" + annotationGroups +
                '}';
    }
}
