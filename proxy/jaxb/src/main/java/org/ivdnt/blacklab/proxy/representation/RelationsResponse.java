package org.ivdnt.blacklab.proxy.representation;

import java.util.Map;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.annotation.JsonInclude;

@XmlRootElement(name="blacklabResponse")
@XmlAccessorType(XmlAccessType.FIELD)
public class RelationsResponse {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Map<String, String> spans;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Map<String, Map<String, Long>> relations;
}
