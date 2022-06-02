package org.ivdnt.blacklab.aggregator.logic;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import javax.ws.rs.WebApplicationException;
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
import org.ivdnt.blacklab.aggregator.representation.HitGroup;
import org.ivdnt.blacklab.aggregator.representation.HitsResults;

/** Performs requests to the BLS nodes we're aggregating */
public class Requests {

    private static final int MAX_GROUPS_TO_GET = Integer.MAX_VALUE - 10;

    /** Is the given value the default value for this parameter?
     *
     * Used to omit some default values for more readable URLs.
     */
    private static boolean isParamDefault(String key, String value) {
        if ("usecache".equals(key)) {
            return value.equals("true") || value.equals("yes");
        }
        return false;
    }

    /**
     * Add query params if not empty or default value.
     *
     * @param src target to add params to
     * @param params params to add (key, value, key, value, etc.)
     * @return new target
     */
    public static WebTarget optParams(WebTarget src, Object... params) {
        WebTarget result = src;
        for (int i = 0; i < params.length; i += 2) {
            if (params[i + 1] != null) {
                String key = params[i].toString();
                String value = params[i + 1].toString();
                if (!value.isEmpty() && !isParamDefault(key, value)) {
                    result = result.queryParam(key, value);
                }
            }
        }
        return result;
    }

    public static RuntimeException translateNodeException(String url, Exception e) {
        e.printStackTrace();
        String msg = e.getMessage() + (e.getCause() != null ? " (" + e.getCause().getMessage() + ")" : "");
        StringWriter stackTrace = new StringWriter();
        e.printStackTrace(new PrintWriter(stackTrace));
        ErrorResponse error = new ErrorResponse("ERROR_ON_NODE", msg, stackTrace.toString());
        error.setNodeUrl(url);
        Response resp = Response.status(Status.INTERNAL_SERVER_ERROR).entity(error).build();
        return new WebApplicationException(resp);
    }

    /** How to create the BLS request to a node */
    public interface NodeRequestFactory {
        WebTarget get(String nodeUrl);
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

    /** Send requests to all nodes */
    private static List<Pair<String, Future<Response>>> sendNodeRequests(NodeRequestFactory factory, MediaType mediaType) {
        List<Pair<String, Future<Response>>> futures = new ArrayList<>();
        for (String nodeUrl: AggregatorConfig.get().getNodes()) {
            WebTarget webTarget = factory.get(nodeUrl);
            Future<Response> futureResponse = webTarget //client.target(nodeUrl)
                    .request(mediaType)
                    .async()
                    .get();
            futures.add(Pair.of(webTarget.getUri().toString(), futureResponse));
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
    }

    /**
     * Send requests to all nodes and collect responses.
     *
     * The error strategy determines whether we collect all responses, or
     * return on the first error (used if all nodes should succeed), or
     * return on the first success (used if only one node needs to succeed).
     *
     * @param factory creates our requests
     * @param mediaType request media type
     * @param strategy how to handle error/success
     * @return responses indexed by node URL
     */
    public static Map<String, Response> getResponses(NodeRequestFactory factory,
            MediaType mediaType, ErrorStrategy strategy) {
        // Send requests and collect futures
        List<Pair<String, Future<Response>>> futures = sendNodeRequests(factory, mediaType);

        // Wait for futures to complete and collect response objects
        Map<String, Response> responses = new LinkedHashMap<>();
        for (Pair<String, Future<Response>> p: futures) {
            String nodeUrl = p.getLeft();
            Future<Response> f = p.getRight();
            Response clientResponse;
            try {
                clientResponse = f.get();
            } catch (InterruptedException | ExecutionException e) {
                throw translateNodeException(nodeUrl, e);
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
    public static <T> Pair<String, T> getFirstSuccesfulResponse(NodeRequestFactory factory,
            Class<T> cls, MediaType mediaType) {
        Map<String, Response> responses = getResponses(factory, mediaType, ErrorStrategy.RETURN_ON_SUCCESS);
        return responses.entrySet().stream()
                .filter(e -> e.getValue().getStatus() == Status.OK.getStatusCode())
                .findFirst()
                .map(e -> Pair.of(e.getKey(), e.getValue().readEntity(cls)))
                .orElse(null);
    }

    /**
     * Send requests to all nodes and return the responses if all succeed.
     *
     * @param factory how to build our requests
     * @param cls response object type
     * @return response objects indexed by nodeUrl
     * @param <T> response object type
     */
    public static <T> Map<String, T> getResponses(NodeRequestFactory factory, Class<T> cls) {
        Map<String, Response> responses = getResponses(factory, MediaType.APPLICATION_JSON_TYPE,
                ErrorStrategy.THROW_ON_FAILURE);
        return responses.entrySet().stream()
                .map(r -> {
                    try {
                        return Pair.of(r.getKey(), r.getValue().readEntity(cls));
                    } catch (Exception e) {
                        throw translateNodeException(r.getKey(), e);
                    }
                })
                .collect(Collectors.toMap(Pair::getKey, Pair::getValue, (x, y) -> x));
    }

    enum UseCache {
        YES,
        NO,
        NODES_ONLY; // use cache on nodes but not in aggregator

        public static UseCache fromStringValue(String str) {
            switch (str.toLowerCase()) {
            case "nodes":
                return NODES_ONLY;
            case "no": case "false": case "0":
                return NO;
            case "yes": case "true": case "1": default:
                return YES;
            }
        }

        public boolean onAggregator() {
            return this == YES;
        }

        public boolean onNodes() {
            return this != NO;
        }
    }

    /**
     * Perform a hits request and get the requested hits window response.
     */
    public static Response getHitsResponse(Client client, String corpusName, String patt,
            String sort, String group, long first, long number, String viewGroup, String strUseCache) {
        UseCache useCache = UseCache.fromStringValue(strUseCache);

        ResponseBuilder ourResponse;
        if (StringUtils.isEmpty(group) || !StringUtils.isEmpty(viewGroup)) {
            // Regular hits request, or viewing a single group in a group request.
            // Response is a list of hits.

            // Request the search object
            HitsSearch hitsSearch = HitsSearch.get(client, corpusName, patt, sort, group, viewGroup, useCache, first + number);
            // Request the window, waiting for it to be available
            HitsResults results = hitsSearch.window(first, number);
            // Return the response
            ourResponse = Response.ok().entity(results);
        } else {
            // Grouped request.
            // Response is a list of groups.
            if (sort.isEmpty())
                sort = "size";
            // Group request.
            var s = sort;
            Map<String, HitsResults> responses = getResponses(
                    url -> Requests.optParams(client.target(url).path(corpusName).path("hits"),
                            "patt", patt,
                            "sort", s,
                            "group", group,
                            "number", MAX_GROUPS_TO_GET,
                            "usecache", Boolean.toString(useCache.onNodes())),
                    HitsResults.class
            );
            HitsResults results = responses.values().stream()
                    .reduce(Aggregation::mergeHitsGrouped)
                    .orElseThrow();

            Comparator<HitGroup> comparator = HitGroupComparators.deserialize(sort);
            if (comparator != null)
                results.hitGroups.sort(comparator);

            // Only return requested window in the response
            int end = (int)(first + number);
            if (results.hitGroups.size() < end)
                end = results.hitGroups.size();
            results.hitGroups = results.hitGroups.subList((int)first, end);
            results.summary.windowFirstResult = first;
            results.summary.requestedWindowSize = number;
            results.summary.actualWindowSize = end - first;
            results.summary.windowHasPrevious = first > 0;
            results.summary.windowHasNext = end < results.hitGroups.size();

            ourResponse = Response.ok().entity(results);
        }
        return ourResponse.build();
    }

}
