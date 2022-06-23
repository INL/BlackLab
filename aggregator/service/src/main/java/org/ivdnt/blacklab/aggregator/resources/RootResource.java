package org.ivdnt.blacklab.aggregator.resources;

import java.util.Map;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Client;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.ivdnt.blacklab.aggregator.logic.Aggregation;
import org.ivdnt.blacklab.aggregator.logic.Requests;
import org.ivdnt.blacklab.aggregator.logic.Requests.BlsRequestException;
import org.ivdnt.blacklab.aggregator.representation.Server;

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

        // Query each node and collect responses
        Map<String, Server> nodeResponses;
        try {
            nodeResponses = Requests.getResponses(url -> client.target(url), Server.class);
        } catch (BlsRequestException e) {
            // One of the node requests produced an error. Return it now.
            return Response.status(e.getStatus()).entity(e.getResponse()).build();
        }

        // Merge responses
        Server merged = nodeResponses.values().stream().reduce(Aggregation::mergeServer).orElseThrow();
        return Response.ok().entity(merged).build();
    }

}