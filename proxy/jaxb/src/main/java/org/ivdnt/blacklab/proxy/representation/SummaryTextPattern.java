package org.ivdnt.blacklab.proxy.representation;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@XmlAccessorType(XmlAccessType.FIELD)
public class SummaryTextPattern {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String bcql;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Object json;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String error;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    Map<String, MatchInfoDef> matchInfos;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String fieldName;

}
