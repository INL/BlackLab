package org.ivdnt.blacklab.solr;

import java.util.Collections;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.handler.component.ResponseBuilder;

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

    private final SolrParams solrParams;

    private final BlackLabIndex index;

    private final SearchManager searchManager;

    public WebserviceParamsSolr(ResponseBuilder rb, BlackLabIndex index, SearchManager searchManager) {
        solrParams = rb.req.getParams();
        this.index = index;
        this.searchManager = searchManager;
    }

    protected boolean has(String name) {
        return !StringUtils.isEmpty(solrParams.get(BL_PAR_NAME_PREFIX + name));
    }

    protected String get(String name) {
        return solrParams.get(BL_PAR_NAME_PREFIX + name, ParameterDefaults.get(name));
    }

    @Override
    public Map<String, String> getParameters() {
        return Collections.emptyMap(); // @@@ FIXME
    }

    // TODO: merge into "op" parameter?
    public boolean isRunBlackLab() {
        return solrParams.getBool(BL_PAR_NAME, false);
    }

    public String getOperation() {
        return get("op");
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
