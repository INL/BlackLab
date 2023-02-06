package org.ivdnt.blacklab.proxy.representation;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@XmlAccessorType(XmlAccessType.FIELD)
public class SearchParam {

    @JsonInclude(Include.NON_EMPTY)
    public String indexname = "";

    @JsonInclude(Include.NON_EMPTY)
    public String op = "";

    @JsonInclude(Include.NON_EMPTY)
    public String patt = "";

    @JsonInclude(Include.NON_EMPTY)
    public String filter = "";

    @JsonInclude(Include.NON_EMPTY)
    public String sort = "";

    @JsonInclude(Include.NON_EMPTY)
    public String group = "";

    @JsonInclude(Include.NON_EMPTY)
    public String viewgroup = "";

    public long first = 0;

    public long number = 20;

    @JsonInclude(Include.NON_EMPTY)
    public String usecache = "";

    @JsonInclude(Include.NON_NULL)
    @XmlElement(name="compatibility")
    @JsonProperty("compatibility")
    public String compatibility;

    public SearchParam() {
    }

    @Override
    public String toString() {
        return "SearchParam{" +
                "indexname='" + indexname + '\'' +
                ", patt='" + patt + '\'' +
                ", filter='" + filter + '\'' +
                ", sort='" + sort + '\'' +
                ", group='" + group + '\'' +
                ", viewgroup='" + viewgroup + '\'' +
                ", first=" + first +
                ", number=" + number +
                ", usecache='" + usecache + '\'' +
                '}';
    }
}
