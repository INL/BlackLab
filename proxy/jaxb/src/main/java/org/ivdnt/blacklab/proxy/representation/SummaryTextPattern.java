package org.ivdnt.blacklab.proxy.representation;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

import com.fasterxml.jackson.annotation.JsonInclude;

@XmlAccessorType(XmlAccessType.FIELD)
public class SummaryTextPattern {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String corpusql;

    @XmlElement(name = "corpusql-error")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String corpusqlError;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Object json;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String error;

}
