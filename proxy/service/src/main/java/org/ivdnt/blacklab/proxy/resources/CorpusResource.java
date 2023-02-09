package org.ivdnt.blacklab.proxy.resources;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Client;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.apache.commons.lang3.StringUtils;
import org.ivdnt.blacklab.proxy.logic.Requests;
import org.ivdnt.blacklab.proxy.representation.AnnotatedField;
import org.ivdnt.blacklab.proxy.representation.Corpus;
import org.ivdnt.blacklab.proxy.representation.CorpusStatus;
import org.ivdnt.blacklab.proxy.representation.DocContentsResults;
import org.ivdnt.blacklab.proxy.representation.DocInfoResponse;
import org.ivdnt.blacklab.proxy.representation.DocsResults;
import org.ivdnt.blacklab.proxy.representation.ErrorResponse;
import org.ivdnt.blacklab.proxy.representation.HitsResults;
import org.ivdnt.blacklab.proxy.representation.InputFormats;
import org.ivdnt.blacklab.proxy.representation.MetadataField;
import org.ivdnt.blacklab.proxy.representation.TermFreqList;
import org.ivdnt.blacklab.proxy.representation.TokenFreqList;

import nl.inl.blacklab.webservice.WebserviceOperation;
import nl.inl.blacklab.webservice.WebserviceParameter;

@Path("/{corpusName}")
public class CorpusResource {

    private static Response error(Response.Status status, String code, String message) {
        ErrorResponse error = new ErrorResponse(code, message, null);
        return Response.status(status).entity(error).build();
    }

    private static Response notImplemented(String resource) {
        return error(Response.Status.NOT_IMPLEMENTED, "NOT_IMPLEMENTED", "The " + resource + " resource hasn't been implemented on the proxy.");
    }

    public static Response success(Object entity) {
        return Response.ok().entity(entity).build();
    }

    private static Map<WebserviceParameter, String> getParams(UriInfo uriInfo, String corpusName, WebserviceOperation op) {
        Map<WebserviceParameter, String> params = getParams(uriInfo, op);
        params.put(WebserviceParameter.CORPUS_NAME, corpusName);
        return params;
    }

    private static Map<WebserviceParameter, String> getParams(UriInfo uriInfo, WebserviceOperation op) {
        Map<WebserviceParameter, String> params = uriInfo.getQueryParameters().entrySet().stream()
                .filter(e -> WebserviceParameter.fromValue(e.getKey()).isPresent()) // keep only known parameters
                .map(e -> Map.entry(WebserviceParameter.fromValue(e.getKey()).orElse(null),
                        StringUtils.join(e.getValue(), ",")))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        params.put(WebserviceParameter.OPERATION, op.value());
        return params;
    }

    /** REST client */
    private final Client client;

    @Inject
    public CorpusResource(Client client) {
        this.client = client;
    }

    @POST
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response corpusInfo(@PathParam("corpusName") String corpusName) {

        if (corpusName.equals("cache-clear")) {// POST naar /cache-clear : clear cache (not implemented)
            return notImplemented("/cache-clear");
        }

        // POST naar /CORPUSNAME ; not supported
        return notImplemented("POST to /CORPUSNAME");
    }

    /**
     * Get information about a corpus.
     *
     * @param corpusName corpus name
     * @return corpus information
     */
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response corpusInfo(@PathParam("corpusName") String corpusName, @Context UriInfo uriInfo) {

        switch (corpusName) {
        case "input-formats":
            return success(Requests.get(client, getParams(uriInfo, corpusName, WebserviceOperation.LIST_INPUT_FORMATS),
                    InputFormats.class));

        case "cache-info":
            return notImplemented("/cache-info");

        case "help":
            return notImplemented("/" + corpusName);

        case "cache-clear":
            return error(Response.Status.BAD_REQUEST, "WRONG_METHOD", "/cache-clear works only with POST");
        }

        return success(Requests.get(client, getParams(uriInfo, corpusName, WebserviceOperation.CORPUS_INFO),
                Corpus.class));
    }

    @GET
    @Path("/hits")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response hits(@PathParam("corpusName") String corpusName, @Context UriInfo uriInfo) {
        Object entity = Requests.get(client, getParams(uriInfo, corpusName, WebserviceOperation.HITS),
                List.of(TokenFreqList.class, HitsResults.class));
        return success(entity);
    }

    @GET
    @Path("/docs/{pid}")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response docInfo(
            @PathParam("corpusName") String corpusName,
            @PathParam("pid") String docPid,
            @Context UriInfo uriInfo) {

        Map<WebserviceParameter, String> params = getParams(uriInfo, corpusName, WebserviceOperation.DOC_INFO);
        params.put(WebserviceParameter.DOC_PID, docPid);
        return success(Requests.get(client, params, DocInfoResponse.class));
    }

    @GET
    @Path("/docs")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response docs(@PathParam("corpusName") String corpusName, @Context UriInfo uriInfo) {
        return success(Requests.get(client, getParams(uriInfo, corpusName, WebserviceOperation.DOCS), DocsResults.class));
    }

    @GET
    @Path("/docs/{pid}/contents")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response docContents(
            @PathParam("corpusName") String corpusName,
            @PathParam("pid") String docPid,
            @Context UriInfo uriInfo) {
        Map<WebserviceParameter, String> params = getParams(uriInfo, corpusName, WebserviceOperation.DOC_CONTENTS);
        params.put(WebserviceParameter.DOC_PID, docPid);
        DocContentsResults entity = (DocContentsResults)Requests.get(client, params, DocContentsResults.class);
        return Response.ok().entity(entity.contents).type(MediaType.APPLICATION_XML).build();
    }

    @GET
    @Path("/termfreq")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response termFreq(@PathParam("corpusName") String corpusName, @Context UriInfo uriInfo) {
        return success(Requests.get(client, getParams(uriInfo, corpusName,
                WebserviceOperation.TERM_FREQUENCIES), TermFreqList.class));
    }

    @GET
    @Path("/fields/{fieldName}")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response fields(
            @PathParam("corpusName") String corpusName,
            @PathParam("fieldName") String fieldName,
            @Context UriInfo uriInfo) {
        Map<WebserviceParameter, String> params = getParams(uriInfo, corpusName, WebserviceOperation.FIELD_INFO);
        params.put(WebserviceParameter.FIELD, fieldName);
        return success(Requests.get(client, params, List.of(MetadataField.class, AnnotatedField.class)));
    }

    @GET
    @Path("/status")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response status(
            @PathParam("corpusName") String corpusName,
            @Context UriInfo uriInfo) {
        return success(Requests.get(client, getParams(uriInfo, corpusName, WebserviceOperation.CORPUS_STATUS), CorpusStatus.class));
    }

    @GET
    @Path("/autocomplete")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response autocomplete() {
        return notImplemented("/autocomplete");
    }

    @GET
    @Path("/sharing")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response sharing() {
        return notImplemented("/sharing");
    }

    @GET
    @Path("/{resource:debug|explain}")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response errorNotImplemented(@PathParam("resource") String resource) {
        return notImplemented("/CORPUS/" + resource);
    }

}
