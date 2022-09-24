package nl.inl.blacklab.server.requesthandlers;

import java.util.ArrayList;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.search.Query;

import nl.inl.blacklab.exceptions.InvalidQuery;
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
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.NotFound;
import nl.inl.blacklab.server.jobs.ContextSettings;
import nl.inl.blacklab.server.jobs.DocGroupSettings;
import nl.inl.blacklab.server.jobs.DocGroupSortSettings;
import nl.inl.blacklab.server.jobs.DocSortSettings;
import nl.inl.blacklab.server.jobs.HitGroupSettings;
import nl.inl.blacklab.server.jobs.HitGroupSortSettings;
import nl.inl.blacklab.server.jobs.HitSortSettings;
import nl.inl.blacklab.server.jobs.WindowSettings;
import nl.inl.blacklab.server.search.SearchManager;
import nl.inl.blacklab.server.util.BlsUtils;
import nl.inl.blacklab.server.util.GapFiller;

/**
 * The parameters passed in the request.
 *
 * We create the necessary JobDescriptions from this.
 */
public class SearchParameters {
    private static final Logger logger = LogManager.getLogger(SearchParameters.class);

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
            "property", // now called "annotation",

            "includegroupcontents", // include hits with the group response? (false)
            "omitemptycaptures"  // omit capture groups of length 0? (false)

    );

    /** Default values for request parameters */
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
        defaultValues.put("property", "word"); // deprecated, use "annotation" now
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
        defaultValues.put("csvsummary", "no");
        defaultValues.put("csvsepline", "no");
        defaultValues.put("includegroupcontents", "no");
        defaultValues.put("omitemptycaptures", "no");
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
        defaultValues.put("sensitive", param.getDefaultSearchSensitivity() == MatchSensitivity.SENSITIVE ? "yes" : "no");
    }

    /**
     * Get the search-related parameters from the request object.
     *
     * This ignores stuff like the requested output type, etc.
     *
     * Note also that the request type is not part of the SearchParameters, so from
     * looking at these parameters alone, you can't always tell what type of search
     * we're doing. The RequestHandler subclass will add a jobclass parameter when
     * executing the actual search.
     *
     * @param isDocs is this a docs operation? influences how the "sort" parameter
     *            is interpreted
     * @param indexName the index to search
     * @param request the HTTP request
     * @return the unique key
     */
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
        param.setDebugMode(searchMan.isDebugMode(ServletUtil.getOriginatingAddress(request)));
        return param;
    }

    private final Map<String, String> map = new TreeMap<>();

    public String put(String key, String value) {
        return map.put(key, value);
    }

    private boolean containsKey(String key) {
        return map.containsKey(key);
    }

    private String getString(String key) {
        String value = map.get(key);
        if (value == null || value.length() == 0) {
            value = defaultValues.get(key);
        }
        return value;
    }

    private static double parse(String value, double defVal) {
        if (value != null) {
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException e) {
                // ok, just return default
            }
        }
        return defVal;
    }

    private static int parse(String value, int defVal) {
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                // ok, just return default
            }
        }
        return defVal;
    }

    private static long parse(String value, long defVal) {
        if (value != null) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                // ok, just return default
            }
        }
        return defVal;
    }

    private static boolean parse(String value, boolean defVal) {
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



    // ----- Specific parameters --------------

    /**
     * Get a view of the parameters.
     *
     * @return the view
     */
    public Map<String, String> getParameters() {
        return Collections.unmodifiableMap(map);
    }

    public String getIndexName() {
        return getString("indexname");
    }

    public String getPattern() {
        return getString("patt");
    }

    public String getPattLanguage() {
        return getString("pattlang");
    }

    public String getPattGapData() {
        return getString("pattgapdata");
    }

    public String getDocPid() {
        return getString("docpid");
    }

    public String getDocumentFilterQuery() {
        return getString("filter");
    }

    public String getDocumentFilterLanguage() {
        return getString("filterlang");
    }

    public String getHitFilterCriterium() {
        return getString("hitfiltercrit");
    }

    public String getHitFilterValue() {
        return getString("hitfilterval");
    }

    public Optional<Double> getSampleFraction() {
        return containsKey("sample") ?
                Optional.of(parse(getString("sample"), 0.0)) :
                Optional.empty();
    }

    public Optional<Integer> getSampleNumber() {
        return containsKey("samplenum") ?
                Optional.of(parse(getString("samplenum"), 0)) :
                Optional.empty();
    }

    public Optional<Long> getSampleSeed() {
        return containsKey("sampleseed") ?
                Optional.of(parse(getString("sampleseed"), 0L)) :
                Optional.empty();
    }

    public boolean getUseCache() {
        return parse(getString("usecache"), true);
    }

    public int getForwardIndexMatchFactor() {
        return parse(getString("fimatch"), 0);
    }

    public long getMaxRetrieve() {
        return parse(getString("maxretrieve"), 0L);
    }

    public long getMaxCount() {
        return parse(getString("maxcount"), 0L);
    }

    public long getFirstResultToShow() {
        return parse(getString("first"), 0L);
    }

    public Optional<Long> optNumberOfResultsToShow() {
        return containsKey("number") ? Optional.of(getNumberOfResultsToShow()) : Optional.empty();
    }

    public long getNumberOfResultsToShow() {
        return parse(getString("number"), 0L);
    }

    public int getWordsAroundHit() {
        return parse(getString("wordsaroundhit"), 0);
    }

    public ConcordanceType getConcordanceType() {
        return getString("usecontent").equals("orig") ? ConcordanceType.CONTENT_STORE :
                ConcordanceType.FORWARD_INDEX;
    }

    public boolean getIncludeGroupContents() {
        return parse(getString("includegroupcontents"), false);
    }

    public boolean getOmitEmptyCaptures() {
        return parse(getString("omitemptycaptures"), false);
    }

    public Optional<String> getFacetProps() {
        String paramFacets = getString("facets");
        return paramFacets.isEmpty() ? Optional.empty() : Optional.of(paramFacets);
    }

    public Optional<String> getGroupProps() {
        String paramGroup = getString("group");
        return paramGroup.isEmpty() ? Optional.empty() : Optional.of(paramGroup);
    }

    public Optional<String> getSortProps() {
        String paramSort = getString("sort");
        return paramSort.isEmpty() ? Optional.empty() : Optional.of(paramSort);
    }

    public Optional<String> getViewGroup() {
        String paramViewGroup = getString("viewgroup");
        return paramViewGroup.isEmpty() ? Optional.empty() : Optional.of(paramViewGroup);
    }

    /**
     * Which annotations to list actual or available values for in hit results/hit exports/indexmetadata requests.
     * IDs are not validated and may not actually exist!
     * @return which annotations to list
     */
    public Set<String> getListValuesFor() {
        String par = getString("listvalues").trim();
        return par.isEmpty() ? Collections.emptySet() : new HashSet<>(Arrays.asList(par.split("\\s*,\\s*")));
    }

    /**
     * Which metadata fields to list actual or available values for in search results/result exports/indexmetadata requests.
     * IDs are not validated and may not actually exist!
     * @return which metadata fields to list
     */
    public Set<String> getListMetadataValuesFor() {
        String par = getString("listmetadatavalues").trim();
        return par.isEmpty() ? Collections.emptySet() : new HashSet<>(Arrays.asList(par.split("\\s*,\\s*")));
    }

    public Set<String> getListSubpropsFor() {
        String par = getString("subprops").trim();
        return par.isEmpty() ? Collections.emptySet() : new HashSet<>(Arrays.asList(par.split("\\s*,\\s*")));
    }

    public boolean getWaitForTotal() {
        return parse(getString("waitfortotal"), false);
    }

    public boolean getIncludeTokenCount() {
        return parse(getString("includetokencount"), false);
    }

    public boolean getCsvIncludeSummary() {
        return parse(getString("csvsummary"), false);
    }

    public boolean getCsvDeclareSeparator() {
        return parse(getString("csvsepline"), false);
    }

    public boolean getExplain() {
        return parse(getString("explain"), false);
    }

    public boolean getSensitive() {
        return parse(getString("sensitive"), false);
    }

    public int getWordStart() {
        return parse(getString("wordstart"), 0);
    }

    public int getWordEnd() {
        return parse(getString("wordend"), 0);
    }

    public Optional<Integer> getHitStart() {
        return containsKey("hitstart") ? Optional.of(parse(getString("hitstart"), 0)) :
                Optional.empty();
    }

    public int getHitEnd() {
        return parse(getString("hitend"), 0);
    }

    public String getAutocompleteTerm() {
        return getString("term");
    }

    public boolean getCalculateCollocations() {
        return getString("calc").equals("colloc");
    }

    public String getAnnotation() {
        String annotName = getString("annotation");
        if (annotName.length() == 0)
            annotName = getString("property"); // old parameter name, deprecated
        return annotName;
    }

    public Set<String> getTerms() {
        String strTerms = getString("terms");
        Set<String> terms = strTerms != null ?
                new HashSet<>(Arrays.asList(strTerms.trim().split("\\s*,\\s*"))) : null;
        return terms;
    }




    // --------- LESS API dependent vars & methods -------------

    /** The search manager, for querying default value for missing parameters */
    private final SearchManager searchManager;

    private boolean debugMode;

    /** The pattern, if parsed already */
    private TextPattern pattern;

    /** The filter query, if parsed already */
    private Query filterQuery;

    private final boolean isDocsOperation;

    private List<DocProperty> facetProps;

    private SearchParameters(SearchManager searchManager, boolean isDocsOperation) {
        this.searchManager = searchManager;
        this.isDocsOperation = isDocsOperation;
    }

    private void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
    }

    public BlackLabIndex blIndex() {
        try {
            return searchManager.getIndexManager().getIndex(getIndexName()).blIndex();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    BLSConfigParameters configParam() {
        return searchManager.config().getParameters();
    }

    public boolean hasPattern() throws BlsException {
        return pattern() != null;
    }

    public TextPattern pattern() throws BlsException {
        if (pattern == null) {
            String patt = getPattern();
            if (!StringUtils.isBlank(patt)) {
                String pattLang = getPattLanguage();
                String pattGapData = getPattGapData();
                TextPattern result;
                if (pattLang.equals("corpusql") && !StringUtils.isBlank(pattGapData) && GapFiller.hasGaps(patt)) {
                    // CQL query with gaps, and TSV data to put in the gaps
                    try {
                        result = GapFiller.parseGapQuery(patt, pattGapData);
                    } catch (InvalidQuery e) {
                        throw new BadRequest("PATT_SYNTAX_ERROR",
                                "Syntax error in gapped CorpusQL pattern: " + e.getMessage());
                    }
                } else {
                    BlackLabIndex index = blIndex();
                    String defaultAnnotation = index.mainAnnotatedField().mainAnnotation().name();
                    result = BlsUtils.parsePatt(index, defaultAnnotation, patt, pattLang, true);
                }
                pattern = result;
            }
        }
        return pattern;
    }

    public boolean hasFilter() throws BlsException {
        return filterQuery() != null;
    }

    Query filterQuery() throws BlsException {
        if (filterQuery == null) {
            BlackLabIndex index = blIndex();
            String docPid = getDocPid();
            String filter = getDocumentFilterQuery();
            Query result;
            if (docPid != null) {
                // Only hits in 1 doc (for highlighting)
                int luceneDocId = BlsUtils.getDocIdFromPid(index, docPid);
                if (luceneDocId < 0)
                    throw new NotFound("DOC_NOT_FOUND", "Document with pid '" + docPid + "' not found.");
                logger.debug("Filtering on single doc-id");
                result = new SingleDocIdFilter(luceneDocId);
            } else if (!StringUtils.isEmpty(filter)) {
                result = BlsUtils.parseFilter(index, filter, getDocumentFilterLanguage());
            } else
                result = null;
            filterQuery = result;
        }
        return filterQuery;
    }

    /**
     * @return hits - filtered then sorted then sampled then windowed
     */
    public SearchHits hitsWindow() throws BlsException {
        WindowSettings windowSettings = windowSettings();
        if (windowSettings == null)
            return hitsSample();
        return hitsSample().window(windowSettings.first(), windowSettings.size());
    }

    public WindowSettings windowSettings() {
        long size = Math.min(Math.max(0, getNumberOfResultsToShow()), configParam().getPageSize().getMax());
        return new WindowSettings(getFirstResultToShow(), size);
    }

    public boolean includeGroupContents() {
        return getIncludeGroupContents() || configParam().isWriteHitsAndDocsInGroupedHits();
    }

    public boolean omitEmptyCapture() {
        return getOmitEmptyCaptures() || configParam().isOmitEmptyCaptures();
    }

    private DocGroupSettings docGroupSettings() throws BlsException {
        if (!isDocsOperation)
            return null; // we're doing per-hits stuff, so sort doesn't apply to docs
        Optional<String> groupProps = getGroupProps();
        DocProperty groupProp = null;
        if (!groupProps.isPresent())
            return null;
        groupProp = DocProperty.deserialize(blIndex(), groupProps.get());
        if (groupProp == null)
            throw new BadRequest("UNKNOWN_GROUP_PROPERTY", "Unknown group property '" + groupProps + "'.");
        return new DocGroupSettings(groupProp);
    }

    private DocGroupSortSettings docGroupSortSettings() {
        DocGroupProperty sortProp = null;
        if (isDocsOperation) {
            Optional<String> groupProps = getGroupProps();
            if (groupProps.isPresent()) {
                Optional<String> sortBy = getSortProps();
                Optional<String> viewGroup = getViewGroup();
                if (sortBy.isPresent() && !viewGroup.isPresent()) {
                    // Sorting refers to results within the group when viewing contents of a group
                    sortProp = DocGroupProperty.deserialize(sortBy.get());
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

        Optional<String> groupProps = getGroupProps();
        if (!groupProps.isPresent()) {
            // looking at groups, or results within a group, don't bother sorting the underlying
            // results themselves (sorting is explicitly ignored anyway in ResultsGrouper::init)
            return null;
        }

        Optional<String> sortBy = getSortProps();
        if (!sortBy.isPresent())
            return null;
        DocProperty sortProp = DocProperty.deserialize(blIndex(), sortBy.get());
        if (sortProp == null)
            return null;
        return new DocSortSettings(sortProp);
    }

    private HitGroupSortSettings hitGroupSortSettings() {
        HitGroupProperty sortProp = null;
        if (!isDocsOperation) {
            // not grouping, so no group sort
            Optional<String> groupProps = getGroupProps();
            if (groupProps.isPresent()) {
                Optional<String> sortBy = getSortProps();
                Optional<String> viewGroup = getViewGroup();
                if (sortBy.isPresent() && !viewGroup.isPresent()) { // Sorting refers to results within the group when viewing contents of a group
                    sortProp = HitGroupProperty.deserialize(sortBy.get());
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
        Optional<String> groupBy = getGroupProps();
        if (!groupBy.isPresent())
            return null;
        return new HitGroupSettings(groupBy.get());
    }

    public HitSortSettings hitsSortSettings() {
        if (isDocsOperation)
            return null; // we're doing per-docs stuff, so sort doesn't apply to hits

        Optional<String> groupBy = getGroupProps();
        if (!groupBy.isPresent())
            return null; // looking at groups, or results within a group, don't bother sorting the underlying results themselves (sorting is explicitly ignored anyway in ResultsGrouper::init)

        Optional<String> sortBy = getSortProps();
        if (!sortBy.isPresent())
            return null;
        HitProperty sortProp = HitProperty.deserialize(blIndex(), blIndex().mainAnnotatedField(), sortBy.get());
        return new HitSortSettings(sortProp);
    }

    public SampleParameters sampleSettings() {
        Optional<Double> sample = getSampleFraction();
        Optional<Integer> sampleNum = getSampleNumber();
        if (!sample.isPresent() && !sampleNum.isPresent())
            return null;
        Optional<Long> sampleSeed = getSampleSeed();
        boolean withSeed = sampleSeed.isPresent();
        SampleParameters p;
        if (sample.isPresent()) {
            double fraction = Math.max(Math.min(sample.get(), 100), 0) / 100.0;
            if (withSeed)
                p = SampleParameters.percentage(fraction, sampleSeed.get());
            else
                p = SampleParameters.percentage(fraction);
        } else {
            if (withSeed) {
                p = SampleParameters.fixedNumber(sampleNum.orElse(0), sampleSeed.get());
            } else
                p = SampleParameters.fixedNumber(sampleNum.orElse(0));
        }
        return p;
    }

    private List<DocProperty> facetProps() {
        if (facetProps == null) {
            Optional<String> facets = getFacetProps();
            if (!facets.isPresent()) {
                facetProps = null;
            } else {
                DocProperty propMultipleFacets = DocProperty.deserialize(blIndex(), facets.get());
                if (propMultipleFacets == null)
                    facetProps = null;
                else {
                    facetProps = new ArrayList<>();
                    if (propMultipleFacets instanceof DocPropertyMultiple) {
                        // Multiple facets requested
                        for (DocProperty prop: (DocPropertyMultiple) propMultipleFacets) {
                            facetProps.add(prop);
                        }
                    } else {
                        // Just a single facet requested
                        facetProps.add(propMultipleFacets);
                    }
                }
            }
        }
        return facetProps;
    }

    public boolean hasFacets() {
        return facetProps() != null;
    }

    public SearchSettings searchSettings() {
        long maxRetrieve = getMaxRetrieve();
        long maxCount = getMaxCount();
        long maxHitsToProcessAllowed = configParam().getProcessHits().getMax();
        if (maxHitsToProcessAllowed >= 0
                && maxRetrieve > maxHitsToProcessAllowed) {
            maxRetrieve = maxHitsToProcessAllowed;
        }
        long maxHitsToCountAllowed = configParam().getCountHits().getMax();
        if (maxHitsToCountAllowed >= 0
                && maxCount > maxHitsToCountAllowed) {
            maxCount = maxHitsToCountAllowed;
        }
        return SearchSettings.get(maxRetrieve, maxCount, forwardIndexMatchFactor());
    }

    public ContextSettings contextSettings() {
        ContextSize contextSize = ContextSize.get(getWordsAroundHit());
        int maxContextSize = configParam().getContextSize().getMaxInt();
        if (contextSize.left() > maxContextSize) { // no check on right needed - same as left
            //debug(logger, "Clamping context size to " + maxContextSize + " (" + contextSize + " requested)");
            contextSize = ContextSize.get(maxContextSize);
        }
        return new ContextSettings(contextSize, getConcordanceType());
    }

    boolean useCache() {
        return !debugMode || getUseCache();
    }

    private int forwardIndexMatchFactor() {
        return debugMode ? getForwardIndexMatchFactor() : -1;
    }



    // -------- Create Search instances -----------

    /**
     * @return hits - filtered then sorted then sampled
     */
    public SearchHits hitsSample() throws BlsException {
        SampleParameters sampleSettings = sampleSettings();
        if (sampleSettings == null)
            return hitsSorted();
        return hitsSorted().sample(sampleSettings);
    }

    /**
     * @return hits - filtered then sorted
     */
    private SearchHits hitsSorted() throws BlsException {
        HitSortSettings hitsSortSettings = hitsSortSettings();
        if (hitsSortSettings == null)
            return hitsFiltered();
        return hitsFiltered().sort(hitsSortSettings.sortBy());
    }

    private SearchHits hitsFiltered() throws BlsException {
        String hitFilterCrit = getHitFilterCriterium();
        String hitFilterVal = getHitFilterValue();
        if (StringUtils.isEmpty(hitFilterCrit) || StringUtils.isEmpty(hitFilterVal))
            return hits();
        HitProperty prop = HitProperty.deserialize(blIndex(), blIndex().mainAnnotatedField(), hitFilterCrit);
        PropertyValue value = PropertyValue.deserialize(blIndex(), blIndex().mainAnnotatedField(), hitFilterVal);
        return hits().filter(prop, value);
    }

    private SearchHits hits() throws BlsException {
        SearchEmpty search = blIndex().search(null, useCache());
        try {
            Query filter = hasFilter() ? filterQuery() : null;
            TextPattern pattern = pattern();
            if (pattern == null)
                throw new BadRequest("NO_PATTERN_GIVEN", "Text search pattern required. Please specify 'patt' parameter.");

            SearchSettings searchSettings = searchSettings();
            return search.find(pattern.toQuery(search.queryInfo(), filter), searchSettings);
        } catch (InvalidQuery e) {
            throw new BadRequest("PATT_SYNTAX_ERROR", "Syntax error in CorpusQL pattern: " + e.getMessage());
        }
    }

    public SearchDocs docsWindow() throws BlsException {
        WindowSettings windowSettings = windowSettings();
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
        if (pattern() != null)
            return hitsSample().docCount();
        return docs().count();
    }

    public SearchDocs docs() throws BlsException {
        TextPattern pattern = pattern();
        if (pattern != null)
            return hitsSample().docs(-1);
        Query docFilterQuery = filterQuery();
        if (pattern == null && docFilterQuery == null) {
            docFilterQuery = blIndex().getAllRealDocsQuery();
        }
        SearchEmpty search = blIndex().search(null, useCache());
        return search.findDocuments(docFilterQuery);
    }

    /**
     * Return our subcorpus.
     *
     * The subcorpus is defined as all documents satisfying the metadata query.
     * If no metadata query is given, the subcorpus is all documents in the corpus.
     *
     * @return subcorpus
     */
    public SearchDocs subcorpus() throws BlsException {
        Query docFilterQuery = filterQuery();
        if (docFilterQuery == null) {
            docFilterQuery = blIndex().getAllRealDocsQuery();
        }
        SearchEmpty search = blIndex().search(null, useCache());
        return search.findDocuments(docFilterQuery);
    }

    public SearchHitGroups hitsGroupedStats() throws BlsException {
        String groupProps = hitGroupSettings().groupBy();
        HitProperty prop = HitProperty.deserialize(blIndex(), blIndex().mainAnnotatedField(), groupProps);
        if (prop == null)
            throw new BadRequest("UNKNOWN_GROUP_PROPERTY", "Unknown group property '" + groupProps + "'.");
        return hitsSample().groupStats(prop, Results.NO_LIMIT).sort(hitGroupSortSettings().sortBy());
    }

    public SearchHitGroups hitsGroupedWithStoredHits() throws BlsException {
        String groupProps = hitGroupSettings().groupBy();
        HitProperty prop = HitProperty.deserialize(blIndex(), blIndex().mainAnnotatedField(), groupProps);
        if (prop == null)
            throw new BadRequest("UNKNOWN_GROUP_PROPERTY", "Unknown group property '" + groupProps + "'.");
        return hitsSample().groupWithStoredHits(prop, Results.NO_LIMIT).sort(hitGroupSortSettings().sortBy());
    }

    public SearchDocGroups docsGrouped() throws BlsException {
        return docs().group(docGroupSettings().groupBy(), Results.NO_LIMIT).sort(docGroupSortSettings().sortBy());
    }

    public SearchFacets facets() throws BlsException {
        return docs().facet(facetProps());
    }

}
