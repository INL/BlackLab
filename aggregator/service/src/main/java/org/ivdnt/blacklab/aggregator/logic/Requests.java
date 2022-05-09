package org.ivdnt.blacklab.aggregator.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.lang3.StringUtils;
import org.ivdnt.blacklab.aggregator.AggregatorConfig;
import org.ivdnt.blacklab.aggregator.representation.ErrorResponse;
import org.ivdnt.blacklab.aggregator.representation.HitsResults;

public class Requests {

    /** How to create the BLS request */
    public interface WebTargetDecorator {
        WebTarget get(WebTarget target);
    }

    /** Thrown when BLS returns an error response */
    public static class BlsRequestException extends RuntimeException {

        private final Response.Status status;

        private ErrorResponse response;

        public BlsRequestException(Response.Status status, ErrorResponse response) {
            super(response.getMessage());
            this.status = status;
            this.response = response;
        }

        public Response.Status getStatus() {
            return status;
        }

        public ErrorResponse getResponse() {
            return response;
        }
    }

    /**
     * Send the same request to all nodes and collect the responses.
     *
     * @param client REST client
     * @param factory how to build our request
     * @param cls response class
     * @return
     * @param <T> response type
     */
    public static <T> List<T> getNodeResponses(Client client, WebTargetDecorator factory, Class<T> cls) {

        // Send requests and collect futures
        List<Future<Response>> futures = new ArrayList<>();
        for (String nodeUrl: AggregatorConfig.get().getNodes()) {
            Future<Response> futureResponse = factory.get(client.target(nodeUrl)) //client.target(nodeUrl)
                    .request(MediaType.APPLICATION_JSON)
                    .async()
                    .get();
            futures.add(futureResponse);
        }

        // Wait for futures to complete and collect response objects
        List<T> nodeResponses = new ArrayList<>();
        for (Future<Response> f: futures) {
            Response clientResponse = null;
            try {
                clientResponse = f.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
            Response.Status status = Response.Status.fromStatusCode(clientResponse.getStatus());
            Response.ResponseBuilder ourResponse;
            if (status == Response.Status.OK)
                nodeResponses.add(clientResponse.readEntity(cls));
            else {
                throw new BlsRequestException(status, clientResponse.readEntity(ErrorResponse.class));
            }
        }
        return nodeResponses;
    }

    public static Response getHitsResponse(Client client, String corpusName, String patt,
            String sort, String group, long first, long number) {
        ResponseBuilder ourResponse;
        if (StringUtils.isEmpty(group)) {
            // Hits request
            // Request the search object
            HitsSearch hitsSearch = HitsSearch.get(client, corpusName, patt, sort);
            // Requqest the window, waiting for it to be available
            HitsResults results = hitsSearch.window(first, number);
            // Return the response
            ourResponse = Response.ok().entity(results);
        } else {
            // Group request.
            // FIXME make distributed
            Response clientResponse = client.target(AggregatorConfig.get().getFirstNodeUrl())
                    .path(corpusName)
                    .path("hits")
                    .queryParam("patt", patt)
                    .queryParam("sort", sort)
                    .queryParam("group", group)
                    .request(MediaType.APPLICATION_JSON)
                    .get();
            Status status = Status.fromStatusCode(clientResponse.getStatus());
            if (status == Status.OK)
                ourResponse = Response.ok().entity(clientResponse.readEntity(HitsResults.class));
            else
                ourResponse = Response.status(status).entity(clientResponse.readEntity(ErrorResponse.class));
        }
        return ourResponse.build();
    }

}
