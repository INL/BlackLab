package org.ivdnt.blacklab.aggregator.representation;

import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.ivdnt.blacklab.aggregator.helper.JacksonUtil;
import org.ivdnt.blacklab.aggregator.helper.MapAdapter;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@XmlAccessorType(XmlAccessType.FIELD)
public class MetadataField {

    @XmlAttribute
    public String name = "title";

    public String fieldName = "title";

    public boolean isAnnotatedField = false;

    public String displayName = "Title";

    public String description = "Document title";

    public String uiType = "";

    public String type = "";

    public String analyzer = "";

    public String unknownCondition = "";

    public String unknownValue = "";

    @XmlJavaTypeAdapter(MapAdapter.class)
    @JsonSerialize(using= JacksonUtil.StringMapSerializer.class)
    @JsonDeserialize(using= JacksonUtil.StringMapDeserializer.class)
    public Map<String, String> displayValues = new HashMap<>();

    public Map<String, Integer> fieldValues = new HashMap<>();

    public boolean valueListComplete = true;

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
