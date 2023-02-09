package org.ivdnt.blacklab.proxy.representation;

public class SolrGeneralErrorResponse {
    private String servlet;

    private String messagee;

    private String url;

    private String status;

    // required for Jersey
    @SuppressWarnings("unused")
    private SolrGeneralErrorResponse() {}

    @Override
    public String toString() {
        return "SolrGeneralErrorResponse{" +
                "servlet='" + servlet + '\'' +
                ", messagee='" + messagee + '\'' +
                ", url='" + url + '\'' +
                ", status='" + status + '\'' +
                '}';
    }

    public String getServlet() {
        return servlet;
    }

    public String getMessage() {
        return messagee;
    }

    public String getUrl() {
        return url;
    }

    public String getStatus() {
        return status;
    }

    @Override
    public SolrGeneralErrorResponse clone() throws CloneNotSupportedException {
        return (SolrGeneralErrorResponse)super.clone();
    }
}
