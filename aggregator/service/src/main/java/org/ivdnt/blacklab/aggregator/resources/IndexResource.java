package org.ivdnt.blacklab.aggregator.resources;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.client.Client;
import javax.ws.rs.core.MediaType;

import org.ivdnt.blacklab.aggregator.Aggregator;
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

    private String corpusUrl(String blsUrl, String corpusName) {
        return blsUrl + "/" + URLEncoder.encode(corpusName, StandardCharsets.UTF_8);
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
    public Index indexInfo(@PathParam("corpus-name") String corpusName) {
        return client.target(Aggregator.BLS_URL)
                .path(corpusName)
                .request(MediaType.APPLICATION_JSON)
                .get(Index.class);
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
    public HitsResults hits(
    		@PathParam("corpus-name") String corpusName,
    		@QueryParam("patt") String cqlPattern,
            @QueryParam("sort") String sort,
            @QueryParam("group") String group) {

        return client.target(Aggregator.BLS_URL)
                .path(corpusName)
                .path("hits")
                .queryParam("patt", cqlPattern)
                .queryParam("sort", sort)
                .queryParam("group", group)
                .request(MediaType.APPLICATION_JSON)
                .get(HitsResults.class);
    }
}
