package org.ivdnt.blacklab.proxy.representation;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import com.fasterxml.jackson.annotation.JsonInclude;

@XmlAccessorType(XmlAccessType.FIELD)
public class MatchInfoDef {

    public String type;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String fieldName;

}
