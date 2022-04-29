package org.ivdnt.blacklab.aggregator.representation;

import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;

@XmlAccessorType(XmlAccessType.FIELD)
public class MetadataField {

    @XmlAttribute
    String name = "title";

    String fieldName = "title";

    boolean isAnnotatedField = false;

    String displayName = "Title";

    String description = "Document title";

    String uiType = "";

    String type = "";

    String analyzer = "";

    String unknownCondition = "";

    String unknownValue = "";

    Map<String, String> displayValues = new HashMap<>();

    Map<String, Integer> fieldValues = new HashMap<>();

    boolean valueListComplete = true;

    @Override
    public String toString() {
        return "MetadataField{" +
                "name='" + name + '\'' +
                ", fieldName='" + fieldName + '\'' +
                ", isAnnotatedField=" + isAnnotatedField +
                ", displayName='" + displayName + '\'' +
                ", description='" + description + '\'' +
                ", uiType='" + uiType + '\'' +
                ", type='" + type + '\'' +
                ", analyzer='" + analyzer + '\'' +
                ", unknownCondition='" + unknownCondition + '\'' +
                ", unknownValue='" + unknownValue + '\'' +
                ", displayValues=" + displayValues +
                ", fieldValues=" + fieldValues +
                ", valueListComplete=" + valueListComplete +
                '}';
    }
}
