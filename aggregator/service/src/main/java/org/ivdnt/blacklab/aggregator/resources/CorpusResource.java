package org.ivdnt.blacklab.aggregator.resources;

import java.util.List;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.client.Client;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import org.ivdnt.blacklab.aggregator.logic.Aggregation;
import org.ivdnt.blacklab.aggregator.AggregatorConfig;
import org.ivdnt.blacklab.aggregator.representation.ErrorResponse;
import org.ivdnt.blacklab.aggregator.representation.HitsResults;
import org.ivdnt.blacklab.aggregator.representation.Corpus;

@Path("/{corpus-name}")
public class CorpusResource {

    /** REST client */
    private final Client client;

    @Inject
    public CorpusResource(Client client) {
        this.client = client;
    }

    /**
     * Get information about a corpus.
     *
     * @param corpusName corpus name
     * @return corpus information
     */
    @GET
    @Path("")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response corpusInfo(@PathParam("corpus-name") String corpusName) {
        // Query each node and collect responses
        List<Corpus> nodeResponses;
        try {
            nodeResponses = Aggregation.getNodeResponses(client, nodeUrl -> client.target(nodeUrl).path(corpusName),
                    Corpus.class);
        } catch (Aggregation.BlsRequestException e) {
            // One of the node requests produced an error. Return it now.
            return Response.status(e.getStatus()).entity(e.getResponse()).build();
        }

        // Merge responses
        Corpus merged = nodeResponses.stream().reduce(Aggregation::mergeCorpus).get();
        return Response.ok().entity(merged).build();
    }

    /**
     * Perform a /hits request.
     */
    @GET
    @Path("/hits")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response hits(
    		@PathParam("corpus-name") String corpusName,
    		@QueryParam("patt") String cqlPattern,
            @QueryParam("sort") String sort,
            @QueryParam("group") String group) {

        Response clientResponse = client.target(AggregatorConfig.get().getBlackLabServerUrl())
                .path(corpusName)
                .path("hits")
                .queryParam("patt", cqlPattern)
                .queryParam("sort", sort)
                .queryParam("group", group)
                .request(MediaType.APPLICATION_JSON)
                .get();
        Status status = Status.fromStatusCode(clientResponse.getStatus());
        ResponseBuilder ourResponse;
        if (status == Status.OK)
            ourResponse = Response.ok().entity(clientResponse.readEntity(HitsResults.class));
        else
            ourResponse = Response.status(status).entity(clientResponse.readEntity(ErrorResponse.class));
        return ourResponse.build();
    }
}
