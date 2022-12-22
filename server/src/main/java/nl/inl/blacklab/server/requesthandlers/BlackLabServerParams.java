package nl.inl.blacklab.server.requesthandlers;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.server.config.BLSConfigParameters;
import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.lib.PlainWebserviceParams;
import nl.inl.blacklab.server.search.SearchManager;

/** BLS API-specific implementation of WebserviceParams. */
public class BlackLabServerParams implements PlainWebserviceParams {

    /**
     * Parameters involved in search
     */
    private static final List<String> NAMES = Arrays.asList(
            // What to search for
            "patt", "pattlang", "pattgapdata", // pattern to search for
            "filter", "filterlang", "docpid", // docs to search
            "sample", "samplenum", "sampleseed", // what hits to select
            "hitfiltercrit", "hitfilterval",

            // How to search
            "fimatch", // [debug] set NFA FI matching threshold
            "usecache", // [debug] use cache or bypass it?

            // How to present results
            "sort", // sorting (grouped) hits/docs
            "first", "number", // results window
            "wordsaroundhit", "usecontent", // concordances
            "hitstart", "hitend", // doc snippets
            "wordstart", "wordend",
            "explain", // explain query rewriting?

            // on field info page, show (non-sub) values for annotation?
            // also controls which annotations' values are sent back with hits
            "listvalues",
            // on document info page, list the values for which metadata fields?
            //also controls which metadata fields are sent back with search hits and document search results
            "listmetadatavalues",
            // EXPERIMENTAL, mostly for part of speech, limited to 500 values
            "subprops", // on field info page, show all subannotations and values for annotation
            // EXPERIMENTAL, mostly for part of speech

            // How to process results
            "facets", // include facet information?
            "includetokencount", // count tokens in all matched documents?
            "maxretrieve", "maxcount", // limits to numbers of hits to process

            // Alternative views
            "calc", // collocations, or other context-based calculations
            "group", "viewgroup", // grouping hits/docs
            "annotation", "sensitive", "terms", // for term frequency

            // How to execute request
            "waitfortotal", // wait until total number of results known?
            "term", // term for autocomplete

            // CSV options
            "csvsummary", // include summary of search in the CSV output? [no]
            "csvsepline", // include separator declaration for Excel? [no]

            // Deprecated parameters
            "property", // now called "annotation",

            "includegroupcontents", // include hits with the group response? (false)
            "omitemptycaptures",  // omit capture groups of length 0? (false)

            "debug" // include debug info (cache)

    );

    /**
     * Default values for request parameters
     */
    final static private Map<String, String> defaultValues;

    static {
        defaultValues = new HashMap<>();
        defaultValues.put("filterlang", "luceneql");
        defaultValues.put("pattlang", "corpusql");
        defaultValues.put("sort", "");
        defaultValues.put("group", "");
        defaultValues.put("viewgroup", "");
        defaultValues.put("first", "0");
        defaultValues.put("hitstart", "0");
        defaultValues.put("hitend", "1");
        defaultValues.put("includetokencount", "no");
        defaultValues.put("usecontent", "fi");
        defaultValues.put("wordstart", "-1");
        defaultValues.put("wordend", "-1");
        defaultValues.put("calc", "");
        defaultValues.put("property", AnnotatedFieldNameUtil.DEFAULT_MAIN_ANNOT_NAME); // deprecated, use "annotation" now
        defaultValues.put("annotation", "");   // default empty, because we fall back to the old name, "property".
        defaultValues.put("waitfortotal", "no");
        defaultValues.put("number", "50");
        defaultValues.put("wordsaroundhit", "5");
        defaultValues.put("maxretrieve", "1000000");
        defaultValues.put("maxcount", "10000000");
        defaultValues.put("sensitive", "no");
        defaultValues.put("fimatch", "-1");
        defaultValues.put("usecache", "yes");
        defaultValues.put("explain", "no");
        defaultValues.put("listvalues", "");
        defaultValues.put("listmetadatavalues", "");
        defaultValues.put("subprops", "");
        defaultValues.put("csvsummary", "yes");
        defaultValues.put("csvsepline", "yes");
        defaultValues.put("includegroupcontents", "no");
        defaultValues.put("omitemptycaptures", "no");
        defaultValues.put("debug", "no");
    }

    /**
     * Set up parameter default values from the configuration.
     */
    public static void setDefaults(BLSConfigParameters param) {
        // Set up the parameter default values
        defaultValues.put("number", "" + param.getPageSize().getDefaultValue());
        defaultValues.put("wordsaroundhit", "" + param.getContextSize().getDefaultValue());
        defaultValues.put("maxretrieve", "" + param.getProcessHits().getDefaultValue());
        defaultValues.put("maxcount", "" + param.getCountHits().getDefaultValue());
        defaultValues.put("sensitive",
                param.getDefaultSearchSensitivity() == MatchSensitivity.SENSITIVE ? "yes" : "no");
    }

    static double parse(String value, double defVal) {
        if (value != null) {
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException e) {
                // ok, just return default
            }
        }
        return defVal;
    }

    static int parse(String value, int defVal) {
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                // ok, just return default
            }
        }
        return defVal;
    }

    static long parse(String value, long defVal) {
        if (value != null) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                // ok, just return default
            }
        }
        return defVal;
    }

    static boolean parse(String value, boolean defVal) {
        if (value != null) {
            switch (value) {
            case "true":
            case "1":
            case "yes":
            case "on":
                return true;
            case "false":
            case "0":
            case "no":
            case "off":
                return false;
            }
        }
        return defVal;
    }

    private final Map<String, String> map = new TreeMap<>();

    private final SearchManager searchMan;

    private final User user;

    public BlackLabServerParams(String indexName, HttpServletRequest request, SearchManager searchMan, User user) {
        this.searchMan = searchMan;
        this.user = user;
        put("indexname", indexName);
        for (String name: NAMES) {
            String value = ServletUtil.getParameter(request, name, "");
            if (value.length() == 0)
                continue;
            put(name, value);
        }
    }

    boolean containsKey(String key) {
        return map.containsKey(key);
    }

    String getString(String key) {
        String value = map.get(key);
        if (value == null || value.length() == 0) {
            value = defaultValues.get(key);
        }
        return value;
    }

    public String put(String key, String value) {
        return map.put(key, value);
    }

    // ----- Specific parameters --------------

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
    public String getIndexName() {
        return getString("indexname");
    }

    @Override
    public String getPattern() {
        return getString("patt");
    }

    @Override
    public String getPattLanguage() {
        return getString("pattlang");
    }

    @Override
    public String getPattGapData() {
        return getString("pattgapdata");
    }

    @Override
    public SearchManager getSearchManager() {
        return searchMan;
    }

    @Override
    public User getUser() {
        return user;
    }

    @Override
    public String getDocPid() {
        return getString("docpid");
    }

    @Override
    public String getDocumentFilterQuery() {
        return getString("filter");
    }

    @Override
    public String getDocumentFilterLanguage() {
        return getString("filterlang");
    }

    @Override
    public String getHitFilterCriterium() {
        return getString("hitfiltercrit");
    }

    @Override
    public String getHitFilterValue() {
        return getString("hitfilterval");
    }

    @Override
    public Optional<Double> getSampleFraction() {
        return containsKey("sample") ?
                Optional.of(parse(getString("sample"), 0.0)) :
                Optional.empty();
    }

    @Override
    public Optional<Integer> getSampleNumber() {
        return containsKey("samplenum") ?
                Optional.of(parse(getString("samplenum"), 0)) :
                Optional.empty();
    }

    @Override
    public Optional<Long> getSampleSeed() {
        return containsKey("sampleseed") ?
                Optional.of(parse(getString("sampleseed"), 0L)) :
                Optional.empty();
    }

    @Override
    public boolean getUseCache() {
        return parse(getString("usecache"), true);
    }

    @Override
    public int getForwardIndexMatchFactor() {
        return parse(getString("fimatch"), 0);
    }

    @Override
    public long getMaxRetrieve() {
        return parse(getString("maxretrieve"), 0L);
    }

    @Override
    public long getMaxCount() {
        return parse(getString("maxcount"), 0L);
    }

    @Override
    public long getFirstResultToShow() {
        return parse(getString("first"), 0L);
    }

    @Override
    public Optional<Long> optNumberOfResultsToShow() {
        return containsKey("number") ?
                Optional.of(parse(getString("number"), 0L)) :
                Optional.empty();
    }

    @Override
    public int getWordsAroundHit() {
        return parse(getString("wordsaroundhit"), 0);
    }

    @Override
    public ConcordanceType getConcordanceType() {
        return getString("usecontent").equals("orig") ? ConcordanceType.CONTENT_STORE :
                ConcordanceType.FORWARD_INDEX;
    }

    @Override
    public boolean getIncludeGroupContents() {
        return parse(getString("includegroupcontents"), false);
    }

    @Override
    public boolean getOmitEmptyCaptures() {
        return parse(getString("omitemptycaptures"), false);
    }

    @Override
    public Optional<String> getFacetProps() {
        String paramFacets = getString("facets");
        return StringUtils.isEmpty(paramFacets) ? Optional.empty() : Optional.of(paramFacets);
    }

    @Override
    public Optional<String> getGroupProps() {
        String paramGroup = getString("group");
        return StringUtils.isEmpty(paramGroup) ? Optional.empty() : Optional.of(paramGroup);
    }

    @Override
    public Optional<String> getSortProps() {
        String paramSort = getString("sort");
        return StringUtils.isEmpty(paramSort) ? Optional.empty() : Optional.of(paramSort);
    }

    @Override
    public Optional<String> getViewGroup() {
        String paramViewGroup = getString("viewgroup");
        return StringUtils.isEmpty(paramViewGroup) ? Optional.empty() : Optional.of(paramViewGroup);
    }

    /**
     * Which annotations to list actual or available values for in hit results/hit exports/indexmetadata requests.
     * IDs are not validated and may not actually exist!
     *
     * @return which annotations to list
     */
    @Override
    public Set<String> getListValuesFor() {
        String par = getString("listvalues").trim();
        return StringUtils.isEmpty(par) ? Collections.emptySet() : new HashSet<>(Arrays.asList(par.split("\\s*,\\s*")));
    }

    /**
     * Which metadata fields to list actual or available values for in search results/result exports/indexmetadata requests.
     * IDs are not validated and may not actually exist!
     *
     * @return which metadata fields to list
     */
    @Override
    public Set<String> getListMetadataValuesFor() {
        String par = getString("listmetadatavalues").trim();
        return StringUtils.isEmpty(par) ? Collections.emptySet() : new HashSet<>(Arrays.asList(par.split("\\s*,\\s*")));
    }

    @Override
    public Set<String> getListSubpropsFor() {
        String par = getString("subprops").trim();
        return StringUtils.isEmpty(par) ? Collections.emptySet() : new HashSet<>(Arrays.asList(par.split("\\s*,\\s*")));
    }

    @Override
    public boolean getWaitForTotal() {
        return parse(getString("waitfortotal"), false);
    }

    @Override
    public boolean getIncludeTokenCount() {
        return parse(getString("includetokencount"), false);
    }

    @Override
    public boolean getCsvIncludeSummary() {
        return parse(getString("csvsummary"), true);
    }

    @Override
    public boolean getCsvDeclareSeparator() {
        return parse(getString("csvsepline"), true);
    }

    @Override
    public boolean getExplain() {
        return parse(getString("explain"), false);
    }

    @Override
    public boolean getSensitive() {
        return parse(getString("sensitive"), false);
    }

    @Override
    public int getWordStart() {
        return parse(getString("wordstart"), 0);
    }

    @Override
    public int getWordEnd() {
        return parse(getString("wordend"), 0);
    }

    @Override
    public Optional<Integer> getHitStart() {
        return containsKey("hitstart") ? Optional.of(parse(getString("hitstart"), 0)) :
                Optional.empty();
    }

    @Override
    public int getHitEnd() {
        return parse(getString("hitend"), 0);
    }

    @Override
    public String getAutocompleteTerm() {
        return getString("term");
    }

    @Override
    public boolean isCalculateCollocations() {
        return getString("calc").equals("colloc");
    }

    @Override
    public String getAnnotationName() {
        String annotName = getString("annotation");
        if (annotName.length() == 0)
            annotName = getString("property"); // old parameter name, deprecated
        return annotName;
    }

    public String getFieldName() {
        // this is not passed as a parameter but part of URL; is injected via SearchCreatorImpl
        return null;
    }

    @Override
    public Set<String> getTerms() {
        String strTerms = getString("terms");
        Set<String> terms = strTerms != null ?
                new HashSet<>(Arrays.asList(strTerms.trim().split("\\s*,\\s*"))) : null;
        return terms;
    }

    @Override
    public boolean isIncludeDebugInfo() {
        return parse(getString("debug"), false);
    }
}
