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
public class AnnotatedField implements Cloneable {

    @XmlAttribute
    public String name;

    public String fieldName;

    public boolean isAnnotatedField = true;

    public String displayName = "";

    public String description = "";

    public boolean hasContentStore;

    public boolean hasXmlTags;

    public boolean hasLengthTokens;

    public String mainAnnotation = "";

    @XmlElementWrapper(name="displayOrder")
    @XmlElement(name = "fieldName")
    @JsonProperty("displayOrder")
    public List<String> displayOrder = new ArrayList<>();

    @XmlElementWrapper(name="annotations")
    @XmlElement(name = "annotation")
    @JsonProperty("annotations")
    public List<Annotation> annotations = new ArrayList<>();

    @Override
    public String toString() {
        return "AnnotatedField{" +
                "name='" + name + '\'' +
                ", fieldName='" + fieldName + '\'' +
                ", isAnnotatedField=" + isAnnotatedField +
                ", displayName='" + displayName + '\'' +
                ", description='" + description + '\'' +
                ", hasContentStore=" + hasContentStore +
                ", hasXmlTags=" + hasXmlTags +
                ", hasLengthTokens=" + hasLengthTokens +
                ", mainAnnotation='" + mainAnnotation + '\'' +
                ", displayOrder=" + displayOrder +
                ", annotations=" + annotations +
                '}';
    }

    private AnnotatedField() {}

    @Override
    public AnnotatedField clone() {
        try {
            return (AnnotatedField)super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    public AnnotatedField(String name) {
        this.name = this.fieldName = name;
    }
}
