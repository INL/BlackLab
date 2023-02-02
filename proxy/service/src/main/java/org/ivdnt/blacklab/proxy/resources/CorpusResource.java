package org.ivdnt.blacklab.proxy.resources;

import java.util.Map;

import javax.inject.Inject;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.client.Client;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.ivdnt.blacklab.proxy.logic.Requests;
import org.ivdnt.blacklab.proxy.representation.Corpus;
import org.ivdnt.blacklab.proxy.representation.DocInfo;
import org.ivdnt.blacklab.proxy.representation.ErrorResponse;
import org.ivdnt.blacklab.proxy.representation.HitsResults;
import org.ivdnt.blacklab.proxy.representation.InputFormats;

import nl.inl.blacklab.webservice.WebserviceOperation;
import nl.inl.blacklab.webservice.WebserviceParameter;

@Path("/{corpusName}")
public class CorpusResource {

    private static Response resourceNotImplemented(String resource) {
        ErrorResponse error = new ErrorResponse("NOT_IMPLEMENTED",
                "The " + resource + " resource hasn't been implemented on the proxy.", null);
        return Response.status(Response.Status.NOT_IMPLEMENTED).entity(error).build();
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

        switch (corpusName) {
        case "cache-clear":
            // POST naar /cache-clear : clear cache (not implemented)
            return resourceNotImplemented("/cache-clear");
        }

        // POST naar /CORPUSNAME ; not supported
        return resourceNotImplemented("POST to /CORPUSNAME");
    }

    /**
     * Get information about a corpus.
     *
     * @param corpusName corpus name
     * @return corpus information
     */
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response corpusInfo(@PathParam("corpusName") String corpusName,
            @DefaultValue("") @QueryParam("listvalues") String listvalues) {

        Map<WebserviceParameter, String> params;
        switch (corpusName) {
        case "input-formats":
            params = Map.of(
                    WebserviceParameter.OPERATION, WebserviceOperation.LIST_INPUT_FORMATS.value());
            return wrap(Requests.get(client, params, InputFormats.class));

        case "cache-info":
            return resourceNotImplemented("/cache-info");
//            params = Map.of(
//                    WsPar.CORPUS_NAME, corpusName,
//                    WsPar.OPERATION, WebserviceOperation.CACHE_INFO.value());
//            return Requests.get(client, params, CacheInfo.class);

        case "help":
            return resourceNotImplemented("/" + corpusName);

        case "cache-clear":
            return resourceNotImplemented("/cache-clear");
        }

        params = Map.of(
                WebserviceParameter.OPERATION, WebserviceOperation.CORPUS_INFO.value(),
                WebserviceParameter.CORPUS_NAME, corpusName);
        return wrap(Requests.get(client, params, Corpus.class));
    }

    public static Response wrap(Object entity) {
        return Response.ok().entity(entity).build();
    }

    /**
     * Perform a /hits request.
     */
    @GET
    @Path("/hits")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response hits(
    		@PathParam("corpusName") String corpusName,
    		@QueryParam("patt") String patt,
            @DefaultValue("") @QueryParam("filter") String filter,
            @DefaultValue("") @QueryParam("sort") String sort,
            @DefaultValue("") @QueryParam("group") String group,
            @DefaultValue("0") @QueryParam("first") long first,
            @DefaultValue("20") @QueryParam("number") long number,
            @DefaultValue("") @QueryParam("viewgroup") String viewGroup,
            @DefaultValue("") @QueryParam("usecache") String useCache) {

        return wrap(Requests.get(client, Map.ofEntries(
                Map.entry(WebserviceParameter.CORPUS_NAME, corpusName),
                Map.entry(WebserviceParameter.OPERATION, WebserviceOperation.HITS.value()),
                Map.entry(WebserviceParameter.PATTERN, patt),
                Map.entry(WebserviceParameter.FILTER, filter),
                Map.entry(WebserviceParameter.SORT_BY, sort),
                Map.entry(WebserviceParameter.GROUP_BY, group),
                Map.entry(WebserviceParameter.FIRST_RESULT, "" + first),
                Map.entry(WebserviceParameter.NUMBER_OF_RESULTS, "" + number),
                Map.entry(WebserviceParameter.VIEW_GROUP, viewGroup),
                Map.entry(WebserviceParameter.USE_CACHE, useCache)), HitsResults.class));
    }

    /**
     * Perform a /hits request.
     */
    @GET
    @Path("/docs/{pid}")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response docOverview(
            @PathParam("corpusName") String corpusName,
            @PathParam("pid") String pid) {

        return wrap(Requests.get(client, Map.of(
                WebserviceParameter.CORPUS_NAME, corpusName,
                WebserviceParameter.OPERATION, WebserviceOperation.DOC_INFO.value(),
                WebserviceParameter.DOC_PID, pid), DocInfo.class));
    }

    @GET
    @Path("/docs")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response docsNotImplemented() {
        return resourceNotImplemented("/CORPUS/docs");
    }

    /**
     * Perform a /hits request.
     */
    @GET
    @Path("/docs/{pid}/contents")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response docContents(
            @PathParam("corpusName") String corpusName,
            @PathParam("pid") String pid) {

        return resourceNotImplemented("/docs/PID/contents");
//        return wrap(Requests.get(client, Map.ofEntries(
//                Map.entry(WsPar.CORPUS_NAME, corpusName),
//                Map.entry(WsPar.OPERATION, WebserviceOperation.DOC_CONTENTS.value()),
//                Map.entry(WsPar.DOC_PID, pid)), ...doc contents... ));
    }

    @GET
    @Path("/termfreq")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response termFreq(
            @DefaultValue("") @QueryParam("annotation") String annotation,
            @DefaultValue("") @QueryParam("terms") String terms) {
        // (TermFreqList resource exists, merge operation not yet, maybe implement later)
        return resourceNotImplemented("/CORPUS/termfreq");
    }

    /**
     * Perform a /hits request.
     */
    @GET
    @Path("/{resource:debug|fields|status|explain|autocomplete|sharing}")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response errorNotImplemented(@PathParam("resource") String resource) {
        return resourceNotImplemented("/CORPUS/" + resource);
    }

}
