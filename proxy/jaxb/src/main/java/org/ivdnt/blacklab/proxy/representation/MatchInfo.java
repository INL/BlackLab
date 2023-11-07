package org.ivdnt.blacklab.proxy.representation;

import java.util.List;
import java.util.Map;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;

import com.fasterxml.jackson.annotation.JsonInclude;

@XmlAccessorType(XmlAccessType.FIELD)
public class MatchInfo {

    public int start;

    public int end;

    /** What match info type this is (span, tag, relation or list) */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String type;

    /** Relation: the relation type */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String relType;

    /** Inline tag: the tag name */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String tagName;

    /** Relation and inline tag: the attributes, if any */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Map<String, String> attributes;

    /** Relation: source start */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Integer sourceStart;


    /** Relation: source end */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Integer sourceEnd;


    /** Relation: target start */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Integer targetStart;


    /** Relation: target end */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Integer targetEnd;

    /** List of relations: the relations!! */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public List<MatchInfo> infos;

    @Override
    public String toString() {
        return "MatchInfo{" +
                "relType='" + relType + '\'' +
                ", tagName='" + tagName + '\'' +
                ", attributes='" + attributes + '\'' +
                ", start=" + start +
                ", end=" + end +
                ", sourceStart=" + sourceStart +
                ", sourceEnd=" + sourceEnd +
                ", targetStart=" + targetStart +
                ", targetEnd=" + targetEnd +
                ", infos='" + infos + '\'' +
                '}';
    }
}
