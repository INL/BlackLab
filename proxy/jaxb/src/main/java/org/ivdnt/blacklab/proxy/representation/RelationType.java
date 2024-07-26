package org.ivdnt.blacklab.proxy.representation;

import java.util.Map;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;

import com.fasterxml.jackson.annotation.JsonInclude;

@XmlAccessorType(XmlAccessType.FIELD)
public class RelationType {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public long count;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Map<String, Map<String, AttributeType>> attributes;
}
