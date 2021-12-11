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
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.requestlogging.SearchLogger;
import nl.inl.blacklab.resultproperty.DocGroupProperty;
import nl.inl.blacklab.resultproperty.DocGroupPropertySize;
import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.resultproperty.DocPropertyMultiple;
import nl.inl.blacklab.resultproperty.HitGroupProperty;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.SingleDocIdFilter;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.results.ContextSize;
import nl.inl.blacklab.search.results.Results;
import nl.inl.blacklab.search.results.SampleParameters;
import nl.inl.blacklab.search.results.SearchSettings;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.searches.SearchCount;
import nl.inl.blacklab.searches.SearchDocGroups;
import nl.inl.blacklab.searches.SearchDocs;
import nl.inl.blacklab.searches.SearchEmpty;
import nl.inl.blacklab.searches.SearchFacets;
import nl.inl.blacklab.searches.SearchHitGroups;
import nl.inl.blacklab.searches.SearchHits;
import nl.inl.blacklab.server.config.BLSConfigParameters;
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
        defaultParameterValues.put("property", "word"); // deprecated, use "annotation" now
        defaultParameterValues.put("annotation", "");   // default empty, because we fall back to the old name, "property".
        defaultParameterValues.put("waitfortotal", "no");
        defaultParameterValues.put("number", "50");
        defaultParameterValues.put("wordsaroundhit", "5");
        defaultParameterValues.put("maxretrieve", "1000000");
        defaultParameterValues.put("maxcount", "10000000");
        defaultParameterValues.put("sensitive", "no");
        defaultParameterValues.put("fimatch", "-1");
        defaultParameterValues.put("usecache", "yes");
        defaultParameterValues.put("explain", "no");
        defaultParameterValues.put("listvalues", "");
        defaultParameterValues.put("listmetadatavalues", "");
        defaultParameterValues.put("subprops", "");
        defaultParameterValues.put("csvsummary", "no");
        defaultParameterValues.put("csvsepline", "no");
    }

    private static String getDefault(String paramName) {
        return defaultParameterValues.get(paramName);
    }

    public static void setDefault(String name, String value) {
        defaultParameterValues.put(name, value);
    }

    /**
     * Set up parameter default values from the configuration.
     */
    public static void setDefaults(BLSConfigParameters param) {
        // Set up the parameter default values
        setDefault("number", "" + param.getPageSize().getDefaultValue());
        setDefault("wordsaroundhit", "" + param.getContextSize().getDefaultValue());
        setDefault("maxretrieve", "" + param.getProcessHits().getDefaultValue());
        setDefault("maxcount", "" + param.getCountHits().getDefaultValue());
        setDefault("sensitive", param.getDefaultSearchSensitivity() == MatchSensitivity.SENSITIVE ? "yes" : "no");
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
        param.setDebugMode(searchMan.config().getDebug().isDebugMode(request.getRemoteAddr()));
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
            "property" // now called "annotation"

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

    private SearchLogger searchLogger;

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

    public BlackLabIndex blIndex() {
        try {
            return searchManager.getIndexManager().getIndex(getIndexName()).blIndex();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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

    public SampleParameters getSampleSettings() {
        if (!(containsKey("sample") || containsKey("samplenum")))
            return null;
        SampleParameters p;
        boolean withSeed = containsKey("sampleseed");
        if (containsKey("sample")) {
            if (withSeed)
                p = SampleParameters.percentage(Math.max(Math.min(getFloat("sample"), 100), 0) / 100.0, getLong("sampleseed"));
            else
                p = SampleParameters.percentage(Math.max(Math.min(getFloat("sample"), 100), 0) / 100.0);
        } else {
            if (withSeed)
                p = SampleParameters.fixedNumber(getInteger("samplenum"), getLong("sampleseed"));
            else
                p = SampleParameters.fixedNumber(getInteger("samplenum"));
        }
        return p;
    }

    boolean getUseCache() {
        return debugMode ? getBoolean("usecache") : true;
    }

    public SearchSettings getSearchSettings() {
        int fiMatchNfaFactor = debugMode ? getInteger("fimatch") : -1;
        int maxRetrieve = getInteger("maxretrieve");
        int maxHitsToProcessAllowed = searchManager.config().getParameters().getProcessHits().getMax();
        if (maxHitsToProcessAllowed >= 0
                && maxRetrieve > maxHitsToProcessAllowed) {
            maxRetrieve = maxHitsToProcessAllowed;
        }
        int maxCount = getInteger("maxcount");
        int maxHitsToCountAllowed = searchManager.config().getParameters().getCountHits().getMax();
        if (maxHitsToCountAllowed >= 0
                && maxCount > maxHitsToCountAllowed) {
            maxCount = maxHitsToCountAllowed;
        }
        return SearchSettings.get(maxRetrieve, maxCount, fiMatchNfaFactor);
    }

    WindowSettings getWindowSettings() {
        int first = getInteger("first");
        int size = Math.min(Math.max(0, getInteger("number")), searchManager.config().getParameters().getPageSize().getMax());
        return new WindowSettings(first, size);
    }

    public ContextSettings getContextSettings() {
        ContextSize contextSize = ContextSize.get(getInteger("wordsaroundhit"));
        int maxContextSize = searchManager.config().getParameters().getContextSize().getMax();
        if (contextSize.left() > maxContextSize) { // no check on right needed - same as left
            //debug(logger, "Clamping context size to " + maxContextSize + " (" + contextSize + " requested)");
            contextSize = ContextSize.get(maxContextSize);
        }
        ConcordanceType concType = getString("usecontent").equals("orig") ? ConcordanceType.CONTENT_STORE
                : ConcordanceType.FORWARD_INDEX;
        return new ContextSettings(contextSize, concType);
    }

    public boolean includeGroupContents() {
        if (containsKey("includegroupcontents")) {
            return getBoolean("includegroupcontents");
        }
        return searchManager.config().getParameters().isWriteHitsAndDocsInGroupedHits();
    }

    private List<DocProperty> getFacets() {
        if (facetProps == null) {
            String facets = getString("facets");
            if (facets == null) {
                return null;
            }
            DocProperty propMultipleFacets = DocProperty.deserialize(blIndex(), facets);
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
        groupProp = DocProperty.deserialize(blIndex(), groupBy);
        if (groupProp == null)
            throw new BadRequest("UNKNOWN_GROUP_PROPERTY", "Unknown group property '" + groupBy + "'.");
        return new DocGroupSettings(groupProp);
    }

    private DocGroupSortSettings docGroupSortSettings() {
        DocGroupProperty sortProp = null;
        if (isDocsOperation) {
            if (containsKey("group")) {
                String sortBy = getString("sort");
                if (sortBy != null && sortBy.length() > 0 && !containsKey("viewgroup")) { // Sorting refers to results within the group when viewing contents of a group
                    sortProp = DocGroupProperty.deserialize(sortBy);
                }
            }
        }
        if (sortProp == null) {
            // By default, show largest group first
            sortProp = new DocGroupPropertySize();
        }
        return new DocGroupSortSettings(sortProp);
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
        DocProperty sortProp = DocProperty.deserialize(blIndex(), sortBy);
        if (sortProp == null)
            return null;
        return new DocSortSettings(sortProp);
    }

    private HitGroupSortSettings hitGroupSortSettings() {
        HitGroupProperty sortProp = null;
        if (!isDocsOperation) {
            // not grouping, so no group sort
            if (containsKey("group")) {
                String sortBy = getString("sort");
                if (sortBy != null && sortBy.length() > 0 && !containsKey("viewgroup")) { // Sorting refers to results within the group when viewing contents of a group
                    sortProp = HitGroupProperty.deserialize(sortBy);
                }
            }
        }
        if (sortProp == null) {
            // By default, show largest group first
            sortProp = HitGroupProperty.size();
        }
        return new HitGroupSortSettings(sortProp);
    }

    private HitGroupSettings hitGroupSettings() {
        if (isDocsOperation)
            return null; // we're doing per-hits stuff, so sort doesn't apply to docs
        String groupBy = getString("group");
        if (groupBy == null || groupBy.length() == 0)
            return null;
        return new HitGroupSettings(groupBy);
    }

    public HitSortSettings hitsSortSettings() {
        if (isDocsOperation)
            return null; // we're doing per-docs stuff, so sort doesn't apply to hits

        String groupBy = getString("group");
        if (groupBy != null && !groupBy.isEmpty())
            return null; // looking at groups, or results within a group, don't bother sorting the underlying results themselves (sorting is explicitly ignored anyway in ResultsGrouper::init)

        String sortBy = getString("sort");
        if (sortBy == null || sortBy.length() == 0)
            return null;
        HitProperty sortProp = HitProperty.deserialize(blIndex(), blIndex().mainAnnotatedField(), sortBy);
        return new HitSortSettings(sortProp);
    }

    /**
     * Which annotations to list actual or available values for in hit results/hit exports/indexmetadata requests.
     * IDs are not validated and may not actually exist!
     * @return which annotations to list
     */
    public Set<String> listValuesFor() {
        String par = getString("listvalues").trim();
        return par.isEmpty() ? Collections.emptySet() : new HashSet<>(Arrays.asList(par.split("\\s*,\\s*")));
    }

    /**
     * Which metadata fields to list actual or available values for in search results/result exports/indexmetadata requests.
     * IDs are not validated and may not actually exist!
     * @return which metadata fields to list
     */
    public Set<String> listMetadataValuesFor() {
        String par = getString("listmetadatavalues").trim();
        return par.isEmpty() ? Collections.emptySet() : new HashSet<>(Arrays.asList(par.split("\\s*,\\s*")));
    }

    public Set<String> listSubpropsFor() {
        String par = getString("subprops").trim();
        return par.isEmpty() ? Collections.emptySet() : new HashSet<>(Arrays.asList(par.split("\\s*,\\s*")));
    }

    public boolean containsKey(String key) {
        return map.containsKey(key);
    }

    /**
     * @return hits - filtered then sorted then sampled then windowed
     * @throws BlsException
     */
    public SearchHits hitsWindow() throws BlsException {
        WindowSettings windowSettings = getWindowSettings();
        if (windowSettings == null)
            return hitsSample();
        return hitsSample().window(windowSettings.first(), windowSettings.size());
    }

    /**
     * @return hits - filtered then sorted then sampled
     * @throws BlsException
     */
    public SearchHits hitsSample() throws BlsException {
        SampleParameters sampleSettings = getSampleSettings();
        if (sampleSettings == null)
            return hitsSorted();
        return hitsSorted().sample(sampleSettings);
    }

    /**
     * @return hits - filtered then sorted
     * @throws BlsException
     */
    public SearchHits hitsSorted() throws BlsException {
        HitSortSettings hitsSortSettings = hitsSortSettings();
        if (hitsSortSettings == null)
            return hitsFiltered();
        return hitsFiltered().sort(hitsSortSettings.sortBy());
    }

    /**
     * @return hits - filtered then sorted then sampled then counted
     * @throws BlsException
     */
    public SearchCount hitsCount() throws BlsException {
        return hitsSample().hitCount();
    }

    public SearchHits hitsFiltered() throws BlsException {
        HitFilterSettings hitFilterSettings = getHitFilterSettings();
        if (hitFilterSettings == null)
            return hits();
        HitProperty prop = HitProperty.deserialize(blIndex(), blIndex().mainAnnotatedField(), hitFilterSettings.getProperty());
        PropertyValue value = PropertyValue.deserialize(blIndex(), blIndex().mainAnnotatedField(), hitFilterSettings.getValue());
        return hits().filter(prop, value);
    }

    public SearchHits hits() throws BlsException {
        SearchEmpty search = blIndex().search(null, getUseCache(), searchLogger);
        try {
            Query filter = hasFilter() ? getFilterQuery() : null;
            return search.find(getPattern().toQuery(search.queryInfo(), filter), getSearchSettings());
        } catch (InvalidQuery e) {
            throw new BadRequest("PATT_SYNTAX_ERROR", "Syntax error in CorpusQL pattern: " + e.getMessage());
        } catch (BlsException e) {
            throw e;
        }
    }

    public SearchDocs docsWindow() throws BlsException {
        WindowSettings windowSettings = getWindowSettings();
        if (windowSettings == null)
            return docsSorted();
        return docsSorted().window(windowSettings.first(), windowSettings.size());
    }

    public SearchDocs docsSorted() throws BlsException {
        DocSortSettings docSortSettings = docSortSettings();
        if (docSortSettings == null)
            return docs();
        return docs().sort(docSortSettings.sortBy());
    }

    public SearchCount docsCount() throws BlsException {
        if (getPattern() != null)
            return hitsSample().docCount();
        return docs().count();
    }

    public SearchDocs docs() throws BlsException {
        TextPattern pattern = getPattern();
        if (pattern != null)
            return hitsSample().docs(-1);
        Query docFilterQuery = getFilterQuery();
        if (pattern == null && docFilterQuery == null) {
            docFilterQuery = new MatchAllDocsQuery();
        }
        SearchEmpty search = blIndex().search(null, getUseCache(), searchLogger);
        return search.findDocuments(docFilterQuery);
    }

    /**
     * Return our subcorpus.
     *
     * The subcorpus is defined as all documents satisfying the metadata query.
     * If no metadata query is given, the subcorpus is all documents in the corpus.
     *
     * @return subcorpus
     * @throws BlsException
     */
    public SearchDocs subcorpus() throws BlsException {
        Query docFilterQuery = getFilterQuery();
        if (docFilterQuery == null) {
            docFilterQuery = new MatchAllDocsQuery();
        }
        SearchEmpty search = blIndex().search(null, getUseCache(), searchLogger);
        return search.findDocuments(docFilterQuery);
    }

    public SearchHitGroups hitsGrouped() throws BlsException {
        String groupBy = hitGroupSettings().groupBy();
        HitProperty prop = HitProperty.deserialize(blIndex(), blIndex().mainAnnotatedField(), groupBy);
        if (prop == null)
            throw new BadRequest("UNKNOWN_GROUP_PROPERTY", "Unknown group property '" + groupBy + "'.");
        return hitsSample().group(prop, Results.NO_LIMIT).sort(hitGroupSortSettings().sortBy());
    }

    public SearchDocGroups docsGrouped() throws BlsException {
        return docs().group(docGroupSettings().groupBy(), Results.NO_LIMIT).sort(docGroupSortSettings().sortBy());
    }

    public SearchFacets facets() throws BlsException {
        return docs().facet(getFacets());
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

    public void setLogger(SearchLogger searchLogger) {
        this.searchLogger = searchLogger;
    }

}
