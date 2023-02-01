package org.ivdnt.blacklab.proxy.logic;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.ivdnt.blacklab.proxy.ProxyConfig;
import org.ivdnt.blacklab.proxy.representation.ErrorResponse;
import org.ivdnt.blacklab.proxy.representation.SolrResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import nl.inl.blacklab.webservice.WebserviceOperation;
import nl.inl.blacklab.webservice.WsPar;
import nl.inl.util.Json;

/** Performs requests to the BLS nodes we're proxying */
public class Requests {

    private static final String BL_PAR_NAME_PREFIX = "bl" + ".";

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

    public static <T> T get(Client client, Map<String, String> queryParams, Class<T> entityType) {
        return request(client, queryParams, "GET", entityType);
    }

    public static <T> T request(Client client, Map<String, String> queryParams, String method, Class<T> entityType) {
        ProxyConfig.ProxyTarget proxyTarget = ProxyConfig.get().getProxyTarget();
        String url = proxyTarget.getUrl();
        WebTarget target = client.target(url);
        if (!queryParams.containsKey(WsPar.CORPUS_NAME)) {
            // Solr always needs a corpus name even for "server-wide" requests.
            if (proxyTarget.getDefaultCorpusName().isEmpty())
                throw new IllegalStateException("No corpus name. Please specify proxyTarget.defaultCorpusName in proxy config file");
            queryParams = new HashMap<>(queryParams);
            queryParams.put(WsPar.CORPUS_NAME, proxyTarget.getDefaultCorpusName());
        }
        return proxyTarget.getProtocol().equalsIgnoreCase("solr") ?
                requestSolr(target, queryParams, method, entityType) :
                requestBls(target, queryParams, method, entityType);
    }

    private static <T> T requestBls(WebTarget target, Map<String, String> queryParams, String method, Class<T> entityType) {
        if (queryParams != null) {
            String corpusName = queryParams.get(WsPar.CORPUS_NAME);
            if (corpusName != null)
                target = target.path(corpusName);
            String operation = queryParams.get(WsPar.OPERATION);
            if (operation != null) {
                WebserviceOperation op = WebserviceOperation.fromValue(operation);
                target = target.path(op.blsPath());
            }
            for (Map.Entry<String, String> e: queryParams.entrySet()) {
                String key = e.getKey();
                if (!key.equals(WsPar.CORPUS_NAME) && !key.equals(WsPar.OPERATION))
                    target = target.queryParam(e.getKey(), e.getValue());
            }
        }
        return target.request(MediaType.APPLICATION_JSON_TYPE).method(method).readEntity(entityType);
    }

    private static <T> T requestSolr(WebTarget target, Map<String, String> queryParams, String method, Class<T> entityType) {
        if (queryParams != null) {
            String corpusName = queryParams.get(WsPar.CORPUS_NAME);
            if (corpusName != null)
                target = target.path(corpusName);
            target = target.path("select");
            for (Map.Entry<String, String> e: queryParams.entrySet()) {
                String key = e.getKey();
                if (!key.equals(WsPar.CORPUS_NAME))
                    target = target.queryParam(BL_PAR_NAME_PREFIX + e.getKey(), e.getValue());
            }
        }
        SolrResponse solrResponse = target.request(MediaType.APPLICATION_JSON_TYPE).method(method).readEntity(SolrResponse.class);

        JsonNode blacklab = solrResponse.getBlacklab();
        ObjectMapper objectMapper = Json.getJsonObjectMapper();
        try {
            return objectMapper.treeToValue(blacklab, entityType);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error interpreting response as " + entityType.getName(), e);
        }

        /*
        MediaType type = solrResponse.getMediaType();
        if (!MediaType.APPLICATION_JSON_TYPE.isCompatible(type)) {
            return Response.status(Status.BAD_REQUEST).
                    entity(new ErrorResponse("INVALID_RESPONSE", "Expected JSON response from Solr, got " + type, "")).build();
        }

        // Get the "blacklab" section from the JSON response and construct a new response from that
        String json = solrResponse.readEntity(String.class);
        JSONObject objBlacklabResponse = new JSONObject(json).getJSONObject(Constants.SOLR_BLACKLAB_SECTION_NAME);
        json = objBlacklabResponse.toString(2);
        Response blacklabResponse = Response.ok().type(MediaType.APPLICATION_JSON_TYPE).entity(json).build();
        return blacklabResponse;*/

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

}
