package org.ivdnt.blacklab.proxy.representation;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

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

    @JsonInclude(Include.NON_EMPTY)
    public String first = "";

    @JsonInclude(Include.NON_EMPTY)
    public String number = "";

    @JsonInclude(Include.NON_EMPTY)
    public String wordsaroundhit = "";

    @JsonInclude(Include.NON_EMPTY)
    public String usecache = "";

    @JsonInclude(Include.NON_NULL)
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
                ", wordsaroundhit=" + wordsaroundhit +
                ", usecache='" + usecache + '\'' +
                '}';
    }
}
