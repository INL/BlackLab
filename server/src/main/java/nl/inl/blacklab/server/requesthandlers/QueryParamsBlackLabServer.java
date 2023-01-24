package nl.inl.blacklab.server.requesthandlers;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.server.lib.ParameterDefaults;
import nl.inl.blacklab.server.lib.QueryParamsAbstract;
import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.search.SearchManager;
import nl.inl.blacklab.server.util.ServletUtil;

/** BLS API-specific implementation of WebserviceParams.
 *
 * Extracts the webservice parameters from a HttpServletRequest.
 */
public class QueryParamsBlackLabServer extends QueryParamsAbstract {

    private final Map<String, String> map = new TreeMap<>();

    public QueryParamsBlackLabServer(String corpusName, HttpServletRequest request, SearchManager searchMan, User user) {
        super(searchMan, user);
        map.put(PARAM_CORPUS_NAME, corpusName);
        for (String name: ParameterDefaults.NAMES) {
            String value = ServletUtil.getParameter(request, name, "");
            if (value.length() == 0)
                continue;
            map.put(name, value);
        }
    }

    @Override
    protected boolean has(String key) {
        return map.containsKey(key);
    }

    @Override
    protected String get(String key) {
        String value = map.get(key);
        if (StringUtils.isEmpty(value)) {
            value = ParameterDefaults.get(key);
        }
        return value;
    }

    /**
     * Get a view of the parameters.
     *
     * @return the view
     */
    @Override
    public Map<String, String> getParameters() {
        return Collections.unmodifiableMap(map);
    }

    @Override
    public String getCorpusName() {
        return get(PARAM_CORPUS_NAME);
    }

}
