package org.ivdnt.blacklab.solr;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.solr.common.params.SolrParams;

import nl.inl.blacklab.server.lib.ParameterDefaults;
import nl.inl.blacklab.server.lib.QueryParamsAbstract;
import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.search.SearchManager;

/**
 * Extracts the webservice parameters from the Solr request parameters.
 * The parameters must be prefixed with "bl." to distinguish them from Solr parameters.
 * (in the future, we may also support a JSON Solr request that doesn't need these prefixes)
 */
public class QueryParamsSolr extends QueryParamsAbstract {

    private static final String BL_PAR_NAME = "bl";

    private static final String BL_PAR_NAME_PREFIX = BL_PAR_NAME + ".";

    public static final String PAR_NAME_JSON_REQUEST = "req";

    private final SolrParams solrParams;

    public QueryParamsSolr(String corpusName, SearchManager searchManager, SolrParams params, User user) {
        super(corpusName, searchManager, user);
        solrParams = params;
    }

    public static boolean shouldRunComponent(SolrParams params) {
        return params.get(BL_PAR_NAME_PREFIX + PARAM_NAME_OPERATION) != null || params.get(BL_PAR_NAME_PREFIX + PAR_NAME_JSON_REQUEST) != null;
    }

    public static String getOperation(SolrParams params) {
        return params.get(BL_PAR_NAME_PREFIX + PARAM_NAME_OPERATION);
    }

    protected boolean has(String name) {
        return !StringUtils.isEmpty(solrParams.get(BL_PAR_NAME_PREFIX + name));
    }

    protected String get(String name) {
        return solrParams.get(BL_PAR_NAME_PREFIX + name, ParameterDefaults.get(name));
    }

    @Override
    public Map<String, String> getParameters() {
        Stream<Pair<String, String>> params = solrParams.stream()
                .filter(e -> e.getKey().startsWith(BL_PAR_NAME_PREFIX)) // Only BL params
                .map(e -> Pair.of(
                        e.getKey().substring(BL_PAR_NAME_PREFIX.length()), // strip "bl."
                        StringUtils.join(e.getValue(), "; "))) // join multiple (shouldn't happen)
                // only existing params
                .filter(p -> p.getKey().equals(PARAM_NAME_OPERATION) || ParameterDefaults.paramExists(p.getKey()));
        params = Stream.concat(Stream.of(Pair.of(PARAM_CORPUS_NAME, getCorpusName())), params); // add index name "parameter"
        return params.collect(Collectors.toMap(Pair::getKey, Pair::getValue));
    }

}
