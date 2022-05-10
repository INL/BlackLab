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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
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
     * @return responses
     * @param <T> response type
     */
    public static <T> List<T> getNodeResponses(Client client, WebTargetDecorator factory, Class<T> cls) {
        // Send requests and collect futures
        List<Pair<String, Future<Response>>> futures = sendNodeRequests(client, factory);

        // Wait for futures to complete and collect response objects
        List<T> nodeResponses = new ArrayList<>();
        for (Pair<String, Future<Response>> p: futures) {
            String nodeUrl = p.getLeft();
            Future<Response> f = p.getRight();
            Response clientResponse = null;
            try {
                // TODO if one node returns an error, don't wait for the rest
                clientResponse = f.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
            Response.Status status = Response.Status.fromStatusCode(clientResponse.getStatus());
            Response.ResponseBuilder ourResponse;
            if (status == Response.Status.OK)
                nodeResponses.add(clientResponse.readEntity(cls));
            else {
                ErrorResponse response = clientResponse.readEntity(ErrorResponse.class);
                response.setNodeUrl(nodeUrl);
                throw new BlsRequestException(status, response);
            }
        }
        return nodeResponses;
    }

    private static List<Pair<String, Future<Response>>> sendNodeRequests(Client client, WebTargetDecorator factory) {
        List<Pair<String, Future<Response>>> futures = new ArrayList<>();
        for (String nodeUrl: AggregatorConfig.get().getNodes()) {
            Future<Response> futureResponse = factory.get(client.target(nodeUrl)) //client.target(nodeUrl)
                    .request(MediaType.APPLICATION_JSON)
                    .async()
                    .get();
            futures.add(Pair.of(nodeUrl, futureResponse));
        }
        return futures;
    }

    public static <T> Pair<String, T> getFirstSuccesfulResponse(Client client, WebTargetDecorator factory, Class<T> cls) {
        // Send requests and collect futures
        List<Pair<String, Future<Response>>> futures = sendNodeRequests(client, factory);

        // Wait for futures to complete and collect response objects
        List<T> nodeResponses = new ArrayList<>();
        for (Pair<String, Future<Response>> p: futures) {
            String nodeUrl = p.getLeft();
            Future<Response> f = p.getRight();
            Response clientResponse = null;
            try {
                clientResponse = f.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
            Response.Status status = Response.Status.fromStatusCode(clientResponse.getStatus());
            Response.ResponseBuilder ourResponse;
            if (status == Response.Status.OK)
                return Pair.of(nodeUrl, clientResponse.readEntity(cls));
        }
        return null;
    }

    public static Response getHitsResponse(Client client, String corpusName, String patt,
            String sort, String group, long first, long number) {
        ResponseBuilder ourResponse;
        if (StringUtils.isEmpty(group)) {
            if (sort.isEmpty())
                sort = "field:pid,hitposition";
            // Hits request
            // Request the search object
            HitsSearch hitsSearch = HitsSearch.get(client, corpusName, patt, sort);
            // Requqest the window, waiting for it to be available
            HitsResults results = hitsSearch.window(first, number);
            // Return the response
            ourResponse = Response.ok().entity(results);
        } else {
            if (sort.isEmpty())
                sort = "identity";
            // Group request.
            var s = sort;
            HitsResults results = getNodeResponses(client, t -> {
                return t.path(corpusName)
                    .path("hits")
                    .queryParam("patt", patt)
                    .queryParam("sort", s)
                    .queryParam("group", group);
                },
                HitsResults.class
            ).stream()
                    .reduce(Aggregation::mergeHitsGrouped)
                    .orElseThrow();
            ourResponse = Response.ok().entity(results);
        }
        return ourResponse.build();
    }

}
