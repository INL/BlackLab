package org.ivdnt.blacklab.proxy.representation;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "blacklabResponse")
@XmlAccessorType(XmlAccessType.FIELD)
public class SolrGeneralErrorResponse {
    private String servlet;

    private String message;

    private String url;

    private String status;

    // required for Jersey
    @SuppressWarnings("unused")
    private SolrGeneralErrorResponse() {}

    @Override
    public String toString() {
        return "SolrGeneralErrorResponse{" +
                "servlet='" + servlet + '\'' +
                ", messagee='" + message + '\'' +
                ", url='" + url + '\'' +
                ", status='" + status + '\'' +
                '}';
    }

    public String getServlet() {
        return servlet;
    }

    public String getMessage() {
        return message;
    }

    public String getUrl() {
        return url;
    }

    public String getStatus() {
        return status;
    }

    public void setServlet(String servlet) {
        this.servlet = servlet;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public SolrGeneralErrorResponse clone() throws CloneNotSupportedException {
        return (SolrGeneralErrorResponse)super.clone();
    }
}
