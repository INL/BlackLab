package org.ivdnt.blacklab.proxy.resources;

import java.util.Map;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Client;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.ivdnt.blacklab.proxy.logic.Requests;
import org.ivdnt.blacklab.proxy.representation.InputFormatInfo;
import org.ivdnt.blacklab.proxy.representation.InputFormatXsltResults;
import org.ivdnt.blacklab.proxy.representation.InputFormats;
import org.ivdnt.blacklab.proxy.representation.Server;

import nl.inl.blacklab.webservice.WebserviceOperation;
import nl.inl.blacklab.webservice.WebserviceParameter;

@Path("")
public class RootResource {

    /** REST client */
    private final Client client;

    @Inject
    public RootResource(Client client) {
        this.client = client;
    }

    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response serverInfo() {
        Map<WebserviceParameter, String> params = Map.of(WebserviceParameter.OPERATION, WebserviceOperation.SERVER_INFO.value());
        return CorpusResource.success(Requests.get(client, params, Server.class));
    }

    @GET
    @Path("/input-formats")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public Response format(
            @Context UriInfo uriInfo,
            @Context HttpHeaders headers) {
        Map<WebserviceParameter, String> params = CorpusResource.getParams(uriInfo, WebserviceOperation.LIST_INPUT_FORMATS);
        InputFormats entity = Requests.get(client, params, InputFormats.class);
        return Response.ok().entity(entity).type(MediaType.APPLICATION_XML).build();
    }

    @GET
    @Path("/input-formats/{formatName}")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public Response format(
            @PathParam("formatName") String formatName,
            @Context UriInfo uriInfo,
            @Context HttpHeaders headers) {
        Map<WebserviceParameter, String> params = CorpusResource.getParams(uriInfo, WebserviceOperation.INPUT_FORMAT_INFO);
        params.put(WebserviceParameter.INPUT_FORMAT, formatName);
        InputFormatInfo entity = Requests.get(client, params, InputFormatInfo.class);
        return Response.ok().entity(entity).type(MediaType.APPLICATION_XML).build();
    }

    @GET
    @Path("/input-formats/{formatName}/xslt")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public Response xslt(
            @PathParam("formatName") String formatName,
            @Context UriInfo uriInfo,
            @Context HttpHeaders headers) {
        Map<WebserviceParameter, String> params = CorpusResource.getParams(uriInfo, WebserviceOperation.INPUT_FORMAT_XSLT);
        params.put(WebserviceParameter.INPUT_FORMAT, formatName);
        InputFormatXsltResults entity = Requests.get(client, params, InputFormatXsltResults.class);
        return Response.ok().entity(entity.xslt).type(MediaType.APPLICATION_XML).build();
    }



}
