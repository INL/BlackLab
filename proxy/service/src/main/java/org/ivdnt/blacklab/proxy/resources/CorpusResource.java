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
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.apache.commons.lang3.StringUtils;
import org.ivdnt.blacklab.proxy.ProxyConfig;
import org.ivdnt.blacklab.proxy.logic.Requests;
import org.ivdnt.blacklab.proxy.representation.AnnotatedField;
import org.ivdnt.blacklab.proxy.representation.AutocompleteResponse;
import org.ivdnt.blacklab.proxy.representation.Corpus;
import org.ivdnt.blacklab.proxy.representation.CorpusStatus;
import org.ivdnt.blacklab.proxy.representation.DocContentsResults;
import org.ivdnt.blacklab.proxy.representation.DocInfoResponse;
import org.ivdnt.blacklab.proxy.representation.DocSnippetResponse;
import org.ivdnt.blacklab.proxy.representation.DocsResults;
import org.ivdnt.blacklab.proxy.representation.ErrorResponse;
import org.ivdnt.blacklab.proxy.representation.HitsResults;
import org.ivdnt.blacklab.proxy.representation.InputFormats;
import org.ivdnt.blacklab.proxy.representation.JsonCsvResponse;
import org.ivdnt.blacklab.proxy.representation.MetadataField;
import org.ivdnt.blacklab.proxy.representation.TermFreqList;
import org.ivdnt.blacklab.proxy.representation.TokenFreqList;

import nl.inl.blacklab.webservice.WebserviceOperation;
import nl.inl.blacklab.webservice.WebserviceParameter;

@Path("/{corpusName}")
public class CorpusResource {

    private static final String MIME_TYPE_CSV = "text/csv";

    private static final MediaType MEDIA_TYPE_CSV = MediaType.valueOf(MIME_TYPE_CSV);

    public static Response error(Response.Status status, String code, String message) {
        return error(status, code, message, null);
    }

    public static Response error(Response.Status status, String code, String message, String stackTrace) {
        ErrorResponse error = new ErrorResponse(code, message, stackTrace);
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
        String defaultCorpusName = ProxyConfig.get().getProxyTarget().getDefaultCorpusName();

        switch (corpusName) {
        case "input-formats":
            return success(Requests.get(client, getParams(uriInfo, defaultCorpusName, WebserviceOperation.LIST_INPUT_FORMATS),
                    InputFormats.class));

        case "cache-info":
            return notImplemented("/cache-info");

        case "help":
            return notImplemented("/help");

        case "cache-clear":
            return error(Response.Status.BAD_REQUEST, "WRONG_METHOD", "/cache-clear works only with POST");
        }

        return success(Requests.get(client, getParams(uriInfo, corpusName, WebserviceOperation.CORPUS_INFO),
                Corpus.class));
    }

    @GET
    @Path("/hits")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, MIME_TYPE_CSV })
    public Response hits(
            @PathParam("corpusName") String corpusName,
            @Context UriInfo uriInfo,
            @Context HttpHeaders headers) {
        boolean isCsv = isCsvRequest(headers);
        WebserviceOperation op = isCsv ? WebserviceOperation.HITS_CSV : WebserviceOperation.HITS;
        List<Class<?>> resultTypes = isCsv ? List.of(JsonCsvResponse.class) : List.of(TokenFreqList.class, HitsResults.class);
        return handlePossibleCsvResponse(corpusName, uriInfo, op, resultTypes);
    }

    @GET
    @Path("/docs")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, MIME_TYPE_CSV })
    public Response docs(
            @PathParam("corpusName") String corpusName,
            @Context UriInfo uriInfo,
            @Context HttpHeaders headers) {
        boolean isCsv = isCsvRequest(headers);
        WebserviceOperation op = isCsv ? WebserviceOperation.DOCS_CSV : WebserviceOperation.DOCS;
        List<Class<?>> resultTypes = List.of(isCsv ? JsonCsvResponse.class : DocsResults.class);
        return handlePossibleCsvResponse(corpusName, uriInfo, op, resultTypes);
    }

    /**
     * Does this request accept a CSV response?
     *
     * @param headers HTTP headers
     * @return true if CSV is accepted
     */
    private static boolean isCsvRequest(HttpHeaders headers) {
        return headers.getAcceptableMediaTypes().stream().anyMatch(m -> m.equals(MEDIA_TYPE_CSV));
    }

    /**
     * Process a request that could return a CSV response.
     *
     * @param corpusName corpus we're querying
     * @param uriInfo URI info
     * @param op operation to perform
     * @param resultTypes what types the result entity could be
     * @return response
     */
    private Response handlePossibleCsvResponse(String corpusName, UriInfo uriInfo, WebserviceOperation op,
            List<Class<?>> resultTypes) {
        Object entity = Requests.get(client, getParams(uriInfo, corpusName, op), resultTypes);
        if (entity instanceof JsonCsvResponse) {
            // Return actual CSV contents instead of JSON
            String csv = ((JsonCsvResponse) entity).csv;
            return Response.ok().type(MIME_TYPE_CSV).entity(csv).build();
        } else {
            return success(entity);
        }
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
    @Path("/docs/{pid}/snippet")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response docSnippet(
            @PathParam("corpusName") String corpusName,
            @PathParam("pid") String docPid,
            @Context UriInfo uriInfo) {
        Map<WebserviceParameter, String> params = getParams(uriInfo, corpusName, WebserviceOperation.DOC_SNIPPET);
        params.put(WebserviceParameter.DOC_PID, docPid);
        return success(Requests.get(client, params, DocSnippetResponse.class));
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
    @Path("/autocomplete/{fieldName}")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response autocompleteMetadata(
            @PathParam("corpusName") String corpusName,
            @PathParam("fieldName") String fieldName,
            @Context UriInfo uriInfo) {
        Map<WebserviceParameter, String> params = getParams(uriInfo, corpusName, WebserviceOperation.AUTOCOMPLETE);
        params.put(WebserviceParameter.FIELD, fieldName);
        return success(Requests.get(client, params, List.of(AutocompleteResponse.class, List.class)));
    }

    @GET
    @Path("/autocomplete/{fieldName}/{annotationName}")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response autocompleteAnnotated(
            @PathParam("corpusName") String corpusName,
            @PathParam("fieldName") String fieldName,
            @PathParam("annotationName") String annotationName,
            @Context UriInfo uriInfo) {
        Map<WebserviceParameter, String> params = getParams(uriInfo, corpusName, WebserviceOperation.AUTOCOMPLETE);
        params.put(WebserviceParameter.FIELD, fieldName);
        params.put(WebserviceParameter.ANNOTATION, annotationName);
        return success(Requests.get(client, params, List.of(AutocompleteResponse.class, List.class)));
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
