package org.ivdnt.blacklab.aggregator.representation;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

import com.fasterxml.jackson.annotation.JsonProperty;

@XmlAccessorType(XmlAccessType.FIELD)
public class AnnotatedField {

    @XmlAttribute
    private String name = "contents";

    private String fieldName = "contents";

    private boolean isAnnotatedField = true;

    private String displayName = "Annotated field";

    private String description = "A nicely annotated field";

    private boolean hasContentStore = true;

    private boolean hasXmlTags = true;

    private boolean hasLengthTokens = true;

    private String mainAnnotation = "word";

    @XmlElementWrapper(name="displayOrder")
    @XmlElement(name = "fieldName")
    @JsonProperty("displayOrder")
    private List<String> displayOrder = List.of("word", "lemma", "pos");

    @XmlElementWrapper(name="annotations")
    @XmlElement(name = "annotation")
    @JsonProperty("annotations")
    private List<Annotation> annotations = List.of(new Annotation());
}
