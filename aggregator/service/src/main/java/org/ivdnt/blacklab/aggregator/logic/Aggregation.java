package org.ivdnt.blacklab.aggregator.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.ivdnt.blacklab.aggregator.AggregatorConfig;
import org.ivdnt.blacklab.aggregator.representation.ErrorResponse;
import org.ivdnt.blacklab.aggregator.representation.Corpus;
import org.ivdnt.blacklab.aggregator.representation.CorpusSummary;
import org.ivdnt.blacklab.aggregator.representation.Server;

public class Aggregation {
    /**
     * Merge server info pages from two nodes.
     *
     * Will determine intersection of available corpora.
     */
    public static Server mergeServer(Server s1, Server s2) {
        Server cl;
        try {
            cl = s1.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }

        // Determine intersection of corpus list and merge the IndexSummaries found
        cl.indices = s1.indices.stream()
                .map(i -> i.name)
                .filter(name -> s2.indices.stream().anyMatch(i2 -> i2.name.equals(name)))
                .map(name -> {
                    CorpusSummary i1 = s1.indices.stream().filter(i -> name.equals(i.name)).findFirst().orElseThrow();
                    CorpusSummary i2 = s2.indices.stream().filter(i -> name.equals(i.name)).findFirst().orElseThrow();
                    return mergeIndexSummary(i1, i2);
                })
                .collect(Collectors.toList());
        return cl;
    }

    /**
     * Merge corpus summary from two nodes.
     *
     * Will add tokenCount for corpus and take max. of timeModified.
     */
    public static CorpusSummary mergeIndexSummary(CorpusSummary i1, CorpusSummary i2) {
        CorpusSummary cl;
        try {
            cl = i1.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
        cl.timeModified = i1.timeModified.compareTo(i2.timeModified) < 0 ? i2.timeModified : i1.timeModified;
        cl.tokenCount = i1.tokenCount + i2.tokenCount;
        return cl;
    }

    public static Corpus mergeCorpus(Corpus i1, Corpus i2) {
        Corpus cl;
        try {
            cl = i1.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
        cl.tokenCount = i1.tokenCount + i2.tokenCount;
        cl.documentCount = i1.documentCount + i2.documentCount;
        return cl;
    }

    public static <T> List<T> getNodeResponses(Client client, WebTargetFactory factory, Class<T> cls) {

        // Send requests and collect futures
        List<Future<Response>> futures = new ArrayList<>();
        for (String nodeUrl: AggregatorConfig.get().getNodes()) {
            Future<Response> futureResponse = factory.get(nodeUrl) //client.target(nodeUrl)
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
            } catch (InterruptedException|ExecutionException e) {
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

    public interface WebTargetFactory {
        WebTarget get(String nodeUrl);
    }

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
}
