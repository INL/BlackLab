package org.ivdnt.blacklab.aggregator.resources;

import java.util.List;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Client;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.ivdnt.blacklab.aggregator.Aggregation;
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

        // Query each node and collect responses
        List<Server> nodeResponses;
        try {
            nodeResponses = Aggregation.getNodeResponses(client, nodeUrl -> client.target(nodeUrl), Server.class);
        } catch (Aggregation.BlsRequestException e) {
            // One of the node requests produced an error. Return it now.
            // TODO: indicate which node returned the error
            return Response.status(e.getStatus()).entity(e.getResponse()).build();
        }

        // Merge responses
        Server merged = nodeResponses.stream().reduce(Aggregation::mergeServer).get();
        return Response.ok().entity(merged).build();
    }

}
