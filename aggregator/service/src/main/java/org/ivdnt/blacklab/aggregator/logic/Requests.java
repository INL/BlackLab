package org.ivdnt.blacklab.aggregator.logic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

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

        private final ErrorResponse response;

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

    private static List<Pair<String, Future<Response>>> sendNodeRequests(Client client, WebTargetDecorator factory, MediaType mediaType) {
        List<Pair<String, Future<Response>>> futures = new ArrayList<>();
        for (String nodeUrl: AggregatorConfig.get().getNodes()) {
            Future<Response> futureResponse = factory.get(client.target(nodeUrl)) //client.target(nodeUrl)
                    .request(mediaType)
                    .async()
                    .get();
            futures.add(Pair.of(nodeUrl, futureResponse));
        }
        return futures;
    }

    public enum ErrorStrategy {
        /** As soon as a request fails (returns non-200 response), stop waiting for other responses */
        RETURN_ON_FAILURE,

        /** As soon as a request fails (returns non-200 response), throw an exception */
        THROW_ON_FAILURE,

        /** As soon as a request succeeds (returns non-200 response), stop waiting for other responses */
        RETURN_ON_SUCCESS,

        /** Collect responses from all nodes regardless of failure or success */
        COLLECT_ALL
    }

    /**
     * Send requests to all nodes and collect responses.
     *
     * The error strategy determines whether we collect all responses, or
     * return on the first error (used if all nodes should succeed), or
     * return on the first success (used if only one node needs to succeed).
     *
     * @param client REST client
     * @param factory creates our requests
     * @param mediaType request media type
     * @param strategy how to handle error/success
     * @return responses indexed by node URL
     */
    public static Map<String, Response> getResponses(Client client, WebTargetDecorator factory,
            MediaType mediaType, ErrorStrategy strategy) {
        // Send requests and collect futures
        List<Pair<String, Future<Response>>> futures = sendNodeRequests(client, factory, mediaType);

        // Wait for futures to complete and collect response objects
        Map<String, Response> responses = new LinkedHashMap<>();
        for (Pair<String, Future<Response>> p: futures) {
            String nodeUrl = p.getLeft();
            Future<Response> f = p.getRight();
            Response clientResponse;
            try {
                clientResponse = f.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
            responses.put(nodeUrl, clientResponse);

            // See if we should throw, return or keep collecting responses
            boolean isErrorResponse = clientResponse.getStatus() != Status.OK.getStatusCode();
            if (isErrorResponse && strategy == ErrorStrategy.THROW_ON_FAILURE) {
                Status status = Status.fromStatusCode(clientResponse.getStatus());
                ErrorResponse response = clientResponse.readEntity(ErrorResponse.class);
                response.setNodeUrl(nodeUrl);
                throw new BlsRequestException(status, response);
            }
            if (isErrorResponse && strategy == ErrorStrategy.RETURN_ON_FAILURE
                    || !isErrorResponse && strategy == ErrorStrategy.RETURN_ON_SUCCESS)
                break;
        }
        return responses;
    }

    /**
     * Send requests to all nodes and return the first succesful one.
     *
     * Useful for e.g. finding a document if we don't know the node it resides on.
     * (not the way to do it in a "real" distributed system)
     */
    public static <T> Pair<String, T> getFirstSuccesfulResponse(Client client, WebTargetDecorator factory,
            Class<T> cls, MediaType mediaType) {
        Map<String, Response> responses = getResponses(client, factory, mediaType, ErrorStrategy.RETURN_ON_SUCCESS);
        return responses.entrySet().stream()
                .filter(e -> e.getValue().getStatus() == Status.OK.getStatusCode())
                .findFirst()
                .map(e -> Pair.of(e.getKey(), e.getValue().readEntity(cls)))
                .orElse(null);
    }

    public static <T> Map<String, T> getResponses(Client client, WebTargetDecorator factory, Class<T> cls,
            MediaType mediaType) {
        Map<String, Response> responses = getResponses(client, factory, mediaType, ErrorStrategy.THROW_ON_FAILURE);
        return responses.entrySet().stream()
                .map(r -> Pair.of(r.getKey(), r.getValue().readEntity(cls)))
                .collect(Collectors.toMap(Pair::getKey, Pair::getValue, (x, y) -> x));
    }

    /**
     * Perform a hits request and get the requested hits window response.
     */
    public static Response getHitsResponse(Client client, String corpusName, String patt,
            String sort, String group, long first, long number, String viewGroup) {
        ResponseBuilder ourResponse;
        if (StringUtils.isEmpty(group) || !StringUtils.isEmpty(viewGroup)) {
            if (sort.isEmpty())
                sort = "field:pid,hitposition";
            // Hits request
            // Request the search object
            HitsSearch hitsSearch = HitsSearch.get(client, corpusName, patt, sort, group, viewGroup);
            // Requqest the window, waiting for it to be available
            HitsResults results = hitsSearch.window(first, number);
            // Return the response
            ourResponse = Response.ok().entity(results);
        } else {
            if (sort.isEmpty())
                sort = "size";
            // Group request.
            var s = sort;
            HitsResults results = getResponses(client, t -> t.path(corpusName)
                .path("hits")
                .queryParam("patt", patt)
                .queryParam("sort", s)
                .queryParam("group", group),
                HitsResults.class,
                MediaType.APPLICATION_JSON_TYPE
            ).values().stream()
                    .reduce(Aggregation::mergeHitsGrouped)
                    .orElseThrow();

            results.hitGroups.sort(HitGroupComparators.deserialize(sort));

            ourResponse = Response.ok().entity(results);
        }
        return ourResponse.build();
    }

}
