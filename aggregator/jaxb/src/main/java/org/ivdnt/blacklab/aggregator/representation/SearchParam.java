package org.ivdnt.blacklab.aggregator.representation;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@XmlAccessorType(XmlAccessType.FIELD)
public class SearchParam {

    @JsonInclude(Include.NON_EMPTY)
    public String indexname = "";

    @JsonInclude(Include.NON_EMPTY)
    public String patt = "";

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

    public SearchParam() {
    }

    @Override
    public String toString() {
        return "SearchParam{" +
                "indexname='" + indexname + '\'' +
                ", patt='" + patt + '\'' +
                ", sort='" + sort + '\'' +
                ", group='" + group + '\'' +
                ", viewgroup='" + viewgroup + '\'' +
                ", first=" + first +
                ", number=" + number +
                ", usecache='" + usecache + '\'' +
                '}';
    }
}
