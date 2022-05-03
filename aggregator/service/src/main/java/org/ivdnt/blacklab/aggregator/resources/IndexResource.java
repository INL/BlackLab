package org.ivdnt.blacklab.aggregator.resources;

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

import org.ivdnt.blacklab.aggregator.Aggregator;
import org.ivdnt.blacklab.aggregator.representation.ErrorResponse;
import org.ivdnt.blacklab.aggregator.representation.HitsResults;
import org.ivdnt.blacklab.aggregator.representation.Index;

@Path("/{corpus-name}")
public class IndexResource {

    /** REST client */
    private final Client client;

    @Inject
    public IndexResource(Client client) {
        this.client = client;
    }

    /**
     * Get information about a corpus.
     *
     * @param corpusName corpus name
     * @return index information
     */
    @GET
    @Path("")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response indexInfo(@PathParam("corpus-name") String corpusName) {
        Response clientResponse = client.target(Aggregator.BLS_URL)
                .path(corpusName)
                .request(MediaType.APPLICATION_JSON)
                .get();
        Status status = Status.fromStatusCode(clientResponse.getStatus());
        ResponseBuilder ourResponse;
        if (status == Status.OK)
            ourResponse = Response.ok().entity(clientResponse.readEntity(Index.class));
        else
            ourResponse = Response.status(status).entity(clientResponse.readEntity(ErrorResponse.class));
        return ourResponse.build();
    }

    /**
     * Perform a /hits request.
     *
     * @param corpusName corpus name
     * @return index information
     */
    @GET
    @Path("/hits")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response hits(
    		@PathParam("corpus-name") String corpusName,
    		@QueryParam("patt") String cqlPattern,
            @QueryParam("sort") String sort,
            @QueryParam("group") String group) {

        Response clientResponse = client.target(Aggregator.BLS_URL)
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
