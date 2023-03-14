package org.ivdnt.blacklab.proxy.representation;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.annotation.JsonInclude;

@XmlRootElement(name="blacklabResponse")
@XmlAccessorType(XmlAccessType.FIELD)
public class DocSnippetResponse implements Cloneable {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public ContextWords snippet;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public ContextWords left;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public ContextWords match;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public ContextWords right;

    public DocSnippetResponse() {}

    @Override
    protected DocSnippetResponse clone() throws CloneNotSupportedException {
        return (DocSnippetResponse)super.clone();
    }

    @Override
    public String toString() {
        return "DocSnippetResponse{" +
                "snippet=" + snippet +
                '}';
    }
}
