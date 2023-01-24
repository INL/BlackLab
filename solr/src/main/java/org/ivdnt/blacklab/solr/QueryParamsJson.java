package org.ivdnt.blacklab.solr;

import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.server.lib.QueryParamsAbstract;
import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.search.SearchManager;
import nl.inl.util.Json;

/**
 * Query parameters, parsed from a JSON structure
 */
public class QueryParamsJson extends QueryParamsAbstract {

    /** JSON structure containing the request */
    private final JsonNode node;

    private final String corpusName;

    private final BlackLabIndex index;

    public QueryParamsJson(String json, String corpusName, BlackLabIndex index, SearchManager searchManager, User user) throws JsonProcessingException {
        super(searchManager, user);
        node = Json.getJsonObjectMapper().readTree(json);
        this.corpusName = corpusName;
        this.index = index;
    }

    @Override
    public Map<String, String> getParameters() {
        return null;
    }

    @Override
    public String getCorpusName() {
        return null;
    }

    @Override
    protected boolean has(String name) {
        return false;
    }

    @Override
    protected String get(String name) {
        return null;
    }
}
