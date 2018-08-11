package nl.inl.blacklab.server.requesthandlers;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.search.Query;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.DocGroupProperty;
import nl.inl.blacklab.resultproperty.DocGroupPropertySize;
import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.resultproperty.DocPropertyMultiple;
import nl.inl.blacklab.resultproperty.GroupProperty;
import nl.inl.blacklab.resultproperty.GroupPropertySize;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.SingleDocIdFilter;
import nl.inl.blacklab.search.results.HitsSample;
import nl.inl.blacklab.search.results.MaxSettings;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.NotFound;
import nl.inl.blacklab.server.jobs.ContextSettings;
import nl.inl.blacklab.server.jobs.DocGroupSettings;
import nl.inl.blacklab.server.jobs.DocGroupSortSettings;
import nl.inl.blacklab.server.jobs.DocSortSettings;
import nl.inl.blacklab.server.jobs.HitFilterSettings;
import nl.inl.blacklab.server.jobs.HitGroupSettings;
import nl.inl.blacklab.server.jobs.HitGroupSortSettings;
import nl.inl.blacklab.server.jobs.HitSortSettings;
import nl.inl.blacklab.server.jobs.JobDescription;
import nl.inl.blacklab.server.jobs.JobDocs.JobDescDocs;
import nl.inl.blacklab.server.jobs.JobDocsGrouped.JobDescDocsGrouped;
import nl.inl.blacklab.server.jobs.JobDocsSorted.JobDescDocsSorted;
import nl.inl.blacklab.server.jobs.JobDocsTotal.JobDescDocsTotal;
import nl.inl.blacklab.server.jobs.JobDocsWindow.JobDescDocsWindow;
import nl.inl.blacklab.server.jobs.JobFacets.JobDescFacets;
import nl.inl.blacklab.server.jobs.JobHits.JobDescHits;
import nl.inl.blacklab.server.jobs.JobHitsFiltered.JobDescHitsFiltered;
import nl.inl.blacklab.server.jobs.JobHitsGrouped.JobDescHitsGrouped;
import nl.inl.blacklab.server.jobs.JobHitsSample.JobDescSampleHits;
import nl.inl.blacklab.server.jobs.JobHitsSorted.JobDescHitsSorted;
import nl.inl.blacklab.server.jobs.JobHitsTotal.JobDescHitsTotal;
import nl.inl.blacklab.server.jobs.JobHitsWindow.JobDescHitsWindow;
import nl.inl.blacklab.server.jobs.SampleSettings;
import nl.inl.blacklab.server.jobs.SearchSettings;
import nl.inl.blacklab.server.jobs.WindowSettings;
import nl.inl.blacklab.server.search.SearchManager;
import nl.inl.blacklab.server.util.BlsUtils;
import nl.inl.blacklab.server.util.GapFiller;
import nl.inl.blacklab.server.util.ParseUtil;
import nl.inl.blacklab.server.util.ServletUtil;

/**
 * The parameters passed in the request.
 *
 * We create the necessary JobDescriptions from this.
 */
public class SearchParameters {
    private static final Logger logger = LogManager.getLogger(SearchParameters.class);

    /** Default values for request parameters */
    final static private Map<String, String> defaultParameterValues;

    static {
        defaultParameterValues = new HashMap<>();
        defaultParameterValues.put("filterlang", "luceneql");
        defaultParameterValues.put("pattlang", "corpusql");
        defaultParameterValues.put("sort", "");
        defaultParameterValues.put("group", "");
        defaultParameterValues.put("viewgroup", "");
        defaultParameterValues.put("first", "0");
        defaultParameterValues.put("hitstart", "0");
        defaultParameterValues.put("hitend", "1");
        defaultParameterValues.put("includetokencount", "no");
        defaultParameterValues.put("usecontent", "fi");
        defaultParameterValues.put("wordstart", "-1");
        defaultParameterValues.put("wordend", "-1");
        defaultParameterValues.put("calc", "");
        defaultParameterValues.put("property", "word");
        defaultParameterValues.put("waitfortotal", "no");
        defaultParameterValues.put("number", "20");
        defaultParameterValues.put("wordsaroundhit", "5");
        defaultParameterValues.put("maxretrieve", "1000000");
        defaultParameterValues.put("maxcount", "10000000");
        defaultParameterValues.put("sensitive", "no");
        defaultParameterValues.put("fimatch", "-1");
        defaultParameterValues.put("usecache", "yes");
        defaultParameterValues.put("explain", "no");
        defaultParameterValues.put("listvalues", "");
        defaultParameterValues.put("subprops", "");
    }

    private static String getDefault(String paramName) {
        return defaultParameterValues.get(paramName);
    }

    public static void setDefault(String name, String value) {
        defaultParameterValues.put(name, value);
    }

    public static SearchParameters get(SearchManager searchMan, boolean isDocs, String indexName,
            HttpServletRequest request) {
        SearchParameters param = new SearchParameters(searchMan, isDocs);
        param.put("indexname", indexName);
        for (String name : SearchParameters.NAMES) {
            String value = ServletUtil.getParameter(request, name, "");
            if (value.length() == 0)
                continue;
            param.put(name, value);
        }
        param.setDebugMode(searchMan.config().isDebugMode(request.getRemoteAddr()));
        return param;
    }

    /** Parameters involved in search */
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
            "listvalues", // on field info page, show (non-sub) values for annotation?
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
            "property", "sensitive", // for term frequency

            // How to execute request
            "waitfortotal", // wait until total number of results known?
            "term" // term for autocomplete
    );

    /** The search manager, for querying default value for missing parameters */
    private SearchManager searchManager;

    private boolean debugMode;

    private Map<String, String> map = new TreeMap<>();

    /** The pattern, if parsed already */
    private TextPattern pattern;

    /** The filter query, if parsed already */
    private Query filterQuery;

    private boolean isDocsOperation;

    private List<DocProperty> facetProps;

    private SearchParameters(SearchManager searchManager, boolean isDocsOperation) {
        this.searchManager = searchManager;
        this.isDocsOperation = isDocsOperation;
    }

    private void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
    }

    public String put(String key, String value) {
        return map.put(key, value);
    }

    public String getString(Object key) {
        String value = map.get(key);
        if (value == null || value.length() == 0) {
            value = getDefault(key.toString());
        }
        return value;
    }

    public int getInteger(String name) {
        String value = getString(name);
        try {
            return ParseUtil.strToInt(value);
        } catch (IllegalArgumentException e) {
            logger.debug("Illegal integer value for parameter '" + name + "': " + value);
            return 0;
        }
    }

    public long getLong(String name) {
        String value = getString(name);
        try {
            return ParseUtil.strToLong(value);
        } catch (IllegalArgumentException e) {
            logger.debug("Illegal integer value for parameter '" + name + "': " + value);
            return 0L;
        }
    }

    public float getFloat(String name) {
        String value = getString(name);
        try {
            return ParseUtil.strToFloat(value);
        } catch (IllegalArgumentException e) {
            logger.debug("Illegal integer value for parameter '" + name + "': " + value);
            return 0L;
        }
    }

    public boolean getBoolean(String name) {
        String value = getString(name);
        try {
            return ParseUtil.strToBool(value);
        } catch (IllegalArgumentException e) {
            logger.debug("Illegal boolean value for parameter '" + name + "': " + value);
            return false;
        }
    }

    /**
     * Get an unmodifiable view of the parameters.
     *
     * @return the view
     */
    public Map<String, String> getParameters() {
        return Collections.unmodifiableMap(map);
    }

    public void dataStream(DataStream ds) {
        ds.startMap();
        for (Map.Entry<String, String> e : map.entrySet()) {
            ds.entry(e.getKey(), e.getValue());
        }
        ds.endMap();
    }

    public String getIndexName() {
        return getString("indexname");
    }

    private BlackLabIndex blIndex() {
        try {
            return searchManager.getIndexManager().getIndex(getIndexName()).blIndex();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public SearchSettings getSearchSettings() {
        int fiMatchNfaFactor = getInteger("fimatch");
        boolean useCache = getBoolean("usecache");
        return new SearchSettings(debugMode, fiMatchNfaFactor, useCache);
    }

    public boolean hasPattern() throws BlsException {
        return getPattern() != null;
    }

    public TextPattern getPattern() throws BlsException {
        if (pattern == null) {
            String patt = getString("patt");
            if (!StringUtils.isBlank(patt)) {
                String pattGapData = getString("pattgapdata");
                String pattLang = getString("pattlang");
                if (pattLang.equals("corpusql") && !StringUtils.isBlank(pattGapData) && GapFiller.hasGaps(patt)) {
                    // CQL query with gaps, and TSV data to put in the gaps
                    try {
                        pattern = GapFiller.parseGapQuery(patt, pattGapData);
                    } catch (InvalidQuery e) {
                        throw new BadRequest("PATT_SYNTAX_ERROR",
                                "Syntax error in gapped CorpusQL pattern: " + e.getMessage());
                    }
                } else {
                    pattern = BlsUtils.parsePatt(blIndex(), patt, pattLang);
                }
            }
        }
        return pattern;
    }

    public boolean hasFilter() throws BlsException {
        return getFilterQuery() != null;
    }

    Query getFilterQuery() throws BlsException {
        if (filterQuery == null) {
            String docId = getString("docpid");
            if (docId != null) {
                // Only hits in 1 doc (for highlighting)
                int luceneDocId = BlsUtils.getDocIdFromPid(blIndex(), docId);
                if (luceneDocId < 0)
                    throw new NotFound("DOC_NOT_FOUND", "Document with pid '" + docId + "' not found.");
                logger.debug("Filtering on single doc-id");
                filterQuery = new SingleDocIdFilter(luceneDocId);
            } else if (containsKey("filter")) {
                filterQuery = BlsUtils.parseFilter(blIndex(), getString("filter"), getString("filterlang"));
            }
        }
        return filterQuery;
    }

    private HitFilterSettings getHitFilterSettings() {
        if (!containsKey("hitfiltercrit") || !containsKey("hitfilterval"))
            return null;
        return new HitFilterSettings(getString("hitfiltercrit"), getString("hitfilterval"));
    }

    private SampleSettings getSampleSettings() {
        if (!(containsKey("sample") || containsKey("samplenum")))
            return null;
        float samplePercentage = containsKey("sample") ? Math.max(Math.min(getFloat("sample"), 100), 0) : -1f;
        int sampleNum = containsKey("samplenum") ? getInteger("samplenum") : -1;
        long sampleSeed = containsKey("sampleseed") ? getLong("sampleseed") : HitsSample.RANDOM_SEED;
        return new SampleSettings(samplePercentage, sampleNum, sampleSeed);
    }

    private MaxSettings getMaxSettings() {
        int maxRetrieve = getInteger("maxretrieve");
        if (searchManager.config().maxHitsToRetrieveAllowed() >= 0
                && maxRetrieve > searchManager.config().maxHitsToRetrieveAllowed()) {
            maxRetrieve = searchManager.config().maxHitsToRetrieveAllowed();
        }
        int maxCount = getInteger("maxcount");
        if (searchManager.config().maxHitsToCountAllowed() >= 0
                && maxCount > searchManager.config().maxHitsToCountAllowed()) {
            maxCount = searchManager.config().maxHitsToCountAllowed();
        }
        return new MaxSettings(maxRetrieve, maxCount);
    }

    WindowSettings getWindowSettings() {
        int first = getInteger("first");
        int size = getInteger("number");
        return new WindowSettings(first, size);
    }

    public ContextSettings getContextSettings() {
        int contextSize = getInteger("wordsaroundhit");
        int maxContextSize = searchManager.config().maxContextSize();
        if (contextSize > maxContextSize) {
            //debug(logger, "Clamping context size to " + maxContextSize + " (" + contextSize + " requested)");
            contextSize = maxContextSize;
        }
        ConcordanceType concType = getString("usecontent").equals("orig") ? ConcordanceType.CONTENT_STORE
                : ConcordanceType.FORWARD_INDEX;
        return new ContextSettings(contextSize, concType);
    }

    private List<DocProperty> getFacets() {
        if (facetProps == null) {
            String facets = getString("facets");
            if (facets == null) {
                return null;
            }
            DocProperty propMultipleFacets = DocProperty.deserialize(facets);
            if (propMultipleFacets == null)
                return null;
            facetProps = new ArrayList<>();
            if (propMultipleFacets instanceof DocPropertyMultiple) {
                // Multiple facets requested
                for (DocProperty prop : (DocPropertyMultiple) propMultipleFacets) {
                    facetProps.add(prop);
                }
            } else {
                // Just a single facet requested
                facetProps.add(propMultipleFacets);
            }
        }
        return facetProps;
    }

    private DocGroupSettings docGroupSettings() throws BlsException {
        if (!isDocsOperation)
            return null; // we're doing per-hits stuff, so sort doesn't apply to docs
        String groupBy = getString("group");
        DocProperty groupProp = null;
        if (groupBy == null || groupBy.length() == 0)
            return null;
        groupProp = DocProperty.deserialize(groupBy);
        if (groupProp == null)
            throw new BadRequest("UNKNOWN_GROUP_PROPERTY", "Unknown group property '" + groupBy + "'.");
        return new DocGroupSettings(groupProp);
    }

    private DocGroupSortSettings docGroupSortSettings() {
        DocGroupProperty sortProp = null;
        boolean reverse = false;
        if (isDocsOperation) {
            if (containsKey("group")) {
                String sortBy = getString("sort");
                if (sortBy != null && sortBy.length() > 0 && !containsKey("viewgroup")) { // Sorting refers to results within the group when viewing contents of a group
                    if (sortBy.length() > 0 && sortBy.charAt(0) == '-') {
                        reverse = true;
                        sortBy = sortBy.substring(1);
                    }
                    sortProp = DocGroupProperty.deserialize(sortBy);
                }
            }
        }
        if (sortProp == null) {
            // By default, show largest group first
            sortProp = new DocGroupPropertySize();
        }
        return new DocGroupSortSettings(sortProp, reverse);
    }

    private DocSortSettings docSortSettings() {
        if (!isDocsOperation)
            return null; // we're doing per-hits stuff, so sort doesn't apply to docs

        String groupBy = getString("groupby");
        if (groupBy != null && !groupBy.isEmpty())
            return null; // looking at groups, or results within a group, don't bother sorting the underlying results themselves (sorting is explicitly ignored anyway in ResultsGrouper::init)

        String sortBy = getString("sort");
        if (sortBy == null || sortBy.length() == 0)
            return null;
        boolean reverse = false;
        if (sortBy.length() > 0 && sortBy.charAt(0) == '-') {
            reverse = true;
            sortBy = sortBy.substring(1);
        }
        DocProperty sortProp = DocProperty.deserialize(sortBy);
        if (sortProp == null)
            return null;
        return new DocSortSettings(sortProp, reverse);
    }

    private HitGroupSortSettings hitGroupSortSettings() {
        GroupProperty sortProp = null;
        boolean reverse = false;
        if (!isDocsOperation) {
            // not grouping, so no group sort
            if (containsKey("group")) {
                String sortBy = getString("sort");
                if (sortBy != null && sortBy.length() > 0 && !containsKey("viewgroup")) { // Sorting refers to results within the group when viewing contents of a group
                    if (sortBy.length() > 0 && sortBy.charAt(0) == '-') {
                        reverse = true;
                        sortBy = sortBy.substring(1);
                    }
                    sortProp = GroupProperty.deserialize(sortBy);
                }
            }
        }
        if (sortProp == null) {
            // By default, show largest group first
            sortProp = new GroupPropertySize();
        }
        return new HitGroupSortSettings(sortProp, reverse);
    }

    private HitGroupSettings hitGroupSettings() {
        if (isDocsOperation)
            return null; // we're doing per-hits stuff, so sort doesn't apply to docs
        String groupBy = getString("group");
        if (groupBy == null || groupBy.length() == 0)
            return null;
        return new HitGroupSettings(groupBy);
    }

    private HitSortSettings hitsSortSettings() {
        if (isDocsOperation)
            return null; // we're doing per-docs stuff, so sort doesn't apply to hits

        String groupBy = getString("groupby");
        if (groupBy != null && !groupBy.isEmpty())
            return null; // looking at groups, or results within a group, don't bother sorting the underlying results themselves (sorting is explicitly ignored anyway in ResultsGrouper::init)

        String sortBy = getString("sort");
        if (sortBy == null || sortBy.length() == 0)
            return null;
        boolean reverse = false;
        if (sortBy.length() > 0 && sortBy.charAt(0) == '-') {
            reverse = true;
            sortBy = sortBy.substring(1);
        }
        return new HitSortSettings(sortBy, reverse);
    }

    public Set<String> listValuesFor() {
        String par = getString("listvalues").trim();
        return new HashSet<>(Arrays.asList(par.split("\\s*,\\s*")));
    }

    public Set<String> listSubpropsFor() {
        String par = getString("subprops").trim();
        return new HashSet<>(Arrays.asList(par.split("\\s*,\\s*")));
    }

    public boolean containsKey(String key) {
        return map.containsKey(key);
    }

    public JobDescription hitsWindow() throws BlsException {
        WindowSettings windowSettings = getWindowSettings();
        if (windowSettings == null)
            return hitsSample();
        return new JobDescHitsWindow(this, hitsSample(), getSearchSettings(), windowSettings);
    }

    public JobDescription hitsSample() throws BlsException {
        SampleSettings sampleSettings = getSampleSettings();
        if (sampleSettings == null)
            return hitsSorted();
        return new JobDescSampleHits(this, hitsSorted(), getSearchSettings(), sampleSettings);
    }

    public JobDescription hitsSorted() throws BlsException {
        HitSortSettings hitsSortSettings = hitsSortSettings();
        if (hitsSortSettings == null)
            return hitsFiltered();
        return new JobDescHitsSorted(this, hitsFiltered(), getSearchSettings(), hitsSortSettings);
    }

    public JobDescription hitsTotal() throws BlsException {
        return new JobDescHitsTotal(this, hitsSample(), getSearchSettings());
    }

    public JobDescription hitsFiltered() throws BlsException {
        HitFilterSettings hitFilterSettings = getHitFilterSettings();
        if (hitFilterSettings == null)
            return hits();
        return new JobDescHitsFiltered(this, hits(), getSearchSettings(), hitFilterSettings);
    }

    public JobDescription hits() throws BlsException {
        return new JobDescHits(this, getSearchSettings(), getIndexName(), getPattern(), getFilterQuery(),
                getMaxSettings(), getContextSettings());
    }

    public JobDescription docsWindow() throws BlsException {
        WindowSettings windowSettings = getWindowSettings();
        if (windowSettings == null)
            return docsSorted();
        return new JobDescDocsWindow(this, docsSorted(), getSearchSettings(), windowSettings);
    }

    public JobDescription docsSorted() throws BlsException {
        DocSortSettings docSortSettings = docSortSettings();
        if (docSortSettings == null)
            return docs();
        return new JobDescDocsSorted(this, docs(), getSearchSettings(), docSortSettings);
    }

    public JobDescription docsTotal() throws BlsException {
        return new JobDescDocsTotal(this, docs(), getSearchSettings());
    }

    public JobDescription docs() throws BlsException {
        TextPattern pattern = getPattern();
        if (pattern != null)
            return new JobDescDocs(this, hitsSample(), getSearchSettings(), getFilterQuery(), getIndexName());
        return new JobDescDocs(this, null, getSearchSettings(), getFilterQuery(), getIndexName());
    }

    public JobDescription hitsGrouped() throws BlsException {
        return new JobDescHitsGrouped(this, hitsSample(), getSearchSettings(), hitGroupSettings(),
                hitGroupSortSettings());
    }

    public JobDescription docsGrouped() throws BlsException {
        return new JobDescDocsGrouped(this, docs(), getSearchSettings(), docGroupSettings(), docGroupSortSettings());
    }

    public JobDescription facets() throws BlsException {
        return new JobDescFacets(this, docs(), getSearchSettings(), getFacets());
    }

    public boolean hasFacets() {
        return getFacets() != null;
    }

    public String getUrlParam() {
        try {
            Set<String> skipEntries = new HashSet<>(Arrays.asList("indexname"));
            StringBuilder b = new StringBuilder();
            for (Entry<String, String> e : map.entrySet()) {
                String name = e.getKey();
                String value = e.getValue();
                if (skipEntries.contains(name)
                        || defaultParameterValues.containsKey(name) && defaultParameterValues.get(name).equals(value))
                    continue;
                if (b.length() > 0)
                    b.append("&");
                b.append(name).append("=").append(URLEncoder.encode(value, "utf-8"));
            }
            return b.toString();
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

}
