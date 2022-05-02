package org.ivdnt.blacklab.aggregator.resources;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Client;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import org.ivdnt.blacklab.aggregator.Aggregator;
import org.ivdnt.blacklab.aggregator.representation.ErrorResponse;
import org.ivdnt.blacklab.aggregator.representation.Server;

@Path("/")
public class RootResource {

    /** REST client */
    private final Client client;

    @Inject
    public RootResource(Client client) {
        this.client = client;
    }

    @GET
    @Path("")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response serverInfo() {
        // Get server info
        Response clientResponse = client.target(Aggregator.BLS_URL)
                .request(MediaType.APPLICATION_JSON)
                .get();
        Status status = Status.fromStatusCode(clientResponse.getStatus());
        ResponseBuilder ourResponse;
        if (status == Status.OK)
            ourResponse = Response.ok().entity(clientResponse.readEntity(Server.class));
        else
            ourResponse = Response.status(status).entity(clientResponse.readEntity(ErrorResponse.class));
        return ourResponse.build();
    }

}
