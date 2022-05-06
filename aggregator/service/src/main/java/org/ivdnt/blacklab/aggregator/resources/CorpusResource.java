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

import org.ivdnt.blacklab.aggregator.logic.Aggregation;
import org.ivdnt.blacklab.aggregator.logic.Requests;
import org.ivdnt.blacklab.aggregator.logic.Requests.BlsRequestException;
import org.ivdnt.blacklab.aggregator.representation.Corpus;

@Path("/{corpusName}")
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
    public Response corpusInfo(@PathParam("corpusName") String corpusName) {
        // Query each node and collect responses
        List<Corpus> nodeResponses;
        try {
            nodeResponses = Requests.getNodeResponses(client, target -> target.path(corpusName),
                    Corpus.class);
        } catch (BlsRequestException e) {
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
    		@PathParam("corpusName") String corpusName,
    		@QueryParam("patt") String cqlPattern,
            @QueryParam("sort") String sort,
            @QueryParam("group") String group,
            @QueryParam("first") long first,
            @QueryParam("number") long number) {

        return Requests.getHitsResponse(client, corpusName, cqlPattern, sort,
                group, first, number);
    }

}
