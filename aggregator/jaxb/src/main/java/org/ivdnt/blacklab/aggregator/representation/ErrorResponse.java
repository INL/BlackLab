package org.ivdnt.blacklab.aggregator.representation;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name="blacklabResponse")
public class ErrorResponse {

    @XmlAccessorType(XmlAccessType.FIELD)
    static class Desc {
        String code;

        String message;

        private Desc() {}

        public Desc(String code, String message) {
            this.code = code;
            this.message = message;
        }
    }

    Desc error;

    /** Which node returned the error? */
    @JsonInclude(Include.NON_EMPTY)
    String nodeUrl = "";

    private ErrorResponse() {}

    public ErrorResponse(Desc error) {
        this.error = error;
    }

    public String getMessage() {
        return error.code + "||" + error.message;
    }

    public String getNodeUrl() {
        return nodeUrl;
    }

    public void setNodeUrl(String nodeUrl) {
        this.nodeUrl = nodeUrl;
    }

    public void setError(String code, String message) {
        error = new Desc(code, message);
    }

    public Desc getError() {
        return error;
    }
}
