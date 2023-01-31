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
import org.ivdnt.blacklab.proxy.representation.ErrorResponse;

import nl.inl.blacklab.webservice.WebserviceOperation;
import nl.inl.blacklab.webservice.WsPar;

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

        switch (corpusName) {
        case "input-formats":
            return Requests.get(client, Map.of(
                    WsPar.OPERATION, WebserviceOperation.LIST_INPUT_FORMATS.value()));

        case "cache-info":
            return Requests.get(client, Map.of(
                    WsPar.CORPUS_NAME, corpusName,
                    WsPar.OPERATION, WebserviceOperation.CACHE_INFO.value()));

        case "help":
            return resourceNotImplemented("/" + corpusName);

        case "cache-clear":
            return resourceNotImplemented("/cache-clear");
        }

        return Requests.get(client, Map.of(
                WsPar.OPERATION, WebserviceOperation.CORPUS_INFO.value(),
                WsPar.CORPUS_NAME, corpusName));
    }

    /**
     * Perform a /hits request.
     */
    @GET
    @Path("/hits")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response hits(
    		@PathParam("corpusName") String corpusName,
    		@QueryParam(WsPar.PATTERN) String patt,
            @DefaultValue("") @QueryParam(WsPar.FILTER) String filter,
            @DefaultValue("") @QueryParam(WsPar.SORT) String sort,
            @DefaultValue("") @QueryParam(WsPar.GROUP_BY) String group,
            @DefaultValue("0") @QueryParam(WsPar.FIRST_RESULT) long first,
            @DefaultValue("20") @QueryParam(WsPar.NUMBER_OF_RESULTS) long number,
            @DefaultValue("") @QueryParam(WsPar.VIEW_GROUP) String viewGroup,
            @DefaultValue("") @QueryParam(WsPar.USE_CACHE) String useCache) {

        return Requests.get(client, Map.ofEntries(
                Map.entry(WsPar.CORPUS_NAME, corpusName),
                Map.entry(WsPar.OPERATION, WebserviceOperation.HITS.value()),
                Map.entry(WsPar.PATTERN, patt),
                Map.entry(WsPar.FILTER, filter),
                Map.entry(WsPar.SORT, sort),
                Map.entry(WsPar.GROUP_BY, group),
                Map.entry(WsPar.FIRST_RESULT, "" + first),
                Map.entry(WsPar.NUMBER_OF_RESULTS, "" + number),
                Map.entry(WsPar.VIEW_GROUP, viewGroup),
                Map.entry(WsPar.USE_CACHE, useCache)));
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

        return Requests.get(client, Map.of(
                WsPar.CORPUS_NAME, corpusName,
                WsPar.OPERATION, WebserviceOperation.DOC_INFO.value(),
                WsPar.DOC_PID, pid));
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

        return Requests.get(client, Map.ofEntries(
                Map.entry(WsPar.CORPUS_NAME, corpusName),
                Map.entry(WsPar.OPERATION, WebserviceOperation.DOC_CONTENTS.value()),
                Map.entry(WsPar.DOC_PID, pid)));
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
