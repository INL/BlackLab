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

    public SearchParam() {
    }

    public SearchParam(String indexname, String patt, String sort, String group) {
        this.indexname = indexname;
        this.patt = patt;
        this.sort = sort;
        this.group = group;
    }

    @Override
    public String toString() {
        return "SearchParam{" +
                "indexname='" + indexname + '\'' +
                ", patt='" + patt + '\'' +
                ", sort='" + sort + '\'' +
                ", group='" + group + '\'' +
                '}';
    }
}
