package org.ivdnt.blacklab.solr;

import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.solr.common.params.SolrParams;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.server.lib.ParameterDefaults;
import nl.inl.blacklab.server.lib.PlainWebserviceParamsAbstract;
import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.search.SearchManager;

/**
 * Extracts the webservice parameters from the Solr request parameters.
 * The parameters must be prefixed with "bl." to distinguish them from Solr parameters.
 * (in the future, we may also support a JSON Solr request that doesn't need these prefixes)
 */
public class WebserviceParamsSolr extends PlainWebserviceParamsAbstract {

    private static final String BL_PAR_NAME = "bl";

    private static final String BL_PAR_NAME_PREFIX = BL_PAR_NAME + ".";

    public static final String PAR_NAME_OPERATION = "op";

    private final SolrParams solrParams;

    private final BlackLabIndex index;

    private final SearchManager searchManager;

    public WebserviceParamsSolr(SolrParams params, BlackLabIndex index, SearchManager searchManager) {
        solrParams = params;
        this.index = index;
        this.searchManager = searchManager;
    }

    public static boolean shouldRunComponent(SolrParams params) {
        return params.get(BL_PAR_NAME_PREFIX + PAR_NAME_OPERATION) != null;
    }

    protected boolean has(String name) {
        return !StringUtils.isEmpty(solrParams.get(BL_PAR_NAME_PREFIX + name));
    }

    protected String get(String name) {
        return solrParams.get(BL_PAR_NAME_PREFIX + name, ParameterDefaults.get(name));
    }

    @Override
    public Map<String, String> getParameters() {
        return solrParams.stream()
                .filter(e -> e.getKey().startsWith(BL_PAR_NAME_PREFIX)) // Only BL params
                .map(e -> Pair.of(
                    e.getKey().substring(BL_PAR_NAME_PREFIX.length()), // strip "bl."
                        StringUtils.join(e.getValue(), "; "))) // join multiple (shouldn't happen)
                .filter(p -> ParameterDefaults.paramExists(p.getKey())) // only existing params
                .collect(Collectors.toMap(Pair::getKey, Pair::getValue));
    }

    public String getOperation() {
        return get(PAR_NAME_OPERATION);
    }

    @Override
    public SearchManager getSearchManager() {
        return searchManager;
    }

    @Override
    public User getUser() {
        return User.anonymous("12345"); // @@@ FIXME
    }

    @Override
    public String getIndexName() {
        return index.name();
    }
}
