package org.ivdnt.blacklab.proxy.representation;

import java.util.Map;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import com.fasterxml.jackson.annotation.JsonInclude;

@XmlAccessorType(XmlAccessType.FIELD)
public class AttributeType {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Boolean valueListComplete;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Map<String, Long> values;
}
