package org.ivdnt.blacklab.proxy.representation;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name="blacklabResponse")
public class ErrorResponse {

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Desc {
        String code;

        String message;

        String stackTrace;

        private Desc() {}

        public Desc(String code, String message, String stackTrace) {
            this.code = code;
            this.message = message;
            this.stackTrace = stackTrace;
        }

        public String getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }

        public String getStackTrace() {
            return message;
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

    public ErrorResponse(String code, String message, String stackTrace) {
        this.error = new Desc(code, message, stackTrace);
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

    public void setError(String code, String message, String stackTrace) {
        error = new Desc(code, message, stackTrace);
    }

    public Desc getError() {
        return error;
    }
}
