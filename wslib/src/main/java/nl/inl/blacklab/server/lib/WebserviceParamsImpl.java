package nl.inl.blacklab.server.lib;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.search.Query;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.DocGroupProperty;
import nl.inl.blacklab.resultproperty.DocGroupPropertySize;
import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.resultproperty.HitGroupProperty;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.lucene.SpanQueryPositionFilter;
import nl.inl.blacklab.search.results.ContextSize;
import nl.inl.blacklab.search.results.Results;
import nl.inl.blacklab.search.results.SampleParameters;
import nl.inl.blacklab.search.results.SearchSettings;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.search.textpattern.TextPatternPositionFilter;
import nl.inl.blacklab.search.textpattern.TextPatternTags;
import nl.inl.blacklab.searches.SearchCount;
import nl.inl.blacklab.searches.SearchDocGroups;
import nl.inl.blacklab.searches.SearchDocs;
import nl.inl.blacklab.searches.SearchEmpty;
import nl.inl.blacklab.searches.SearchFacets;
import nl.inl.blacklab.searches.SearchHitGroups;
import nl.inl.blacklab.searches.SearchHits;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.jobs.ContextSettings;
import nl.inl.blacklab.server.jobs.DocGroupSettings;
import nl.inl.blacklab.server.jobs.DocGroupSortSettings;
import nl.inl.blacklab.server.jobs.DocSortSettings;
import nl.inl.blacklab.server.jobs.HitGroupSettings;
import nl.inl.blacklab.server.jobs.HitGroupSortSettings;
import nl.inl.blacklab.server.jobs.HitSortSettings;
import nl.inl.blacklab.server.jobs.WindowSettings;
import nl.inl.blacklab.server.lib.results.ApiVersion;
import nl.inl.blacklab.server.search.SearchManager;
import nl.inl.blacklab.webservice.WebserviceOperation;
import nl.inl.blacklab.webservice.WebserviceParameter;

/**
 * Wraps the WebserviceParams and interprets them to create searches.
 */
public class WebserviceParamsImpl implements WebserviceParams {
    private static final Logger logger = LogManager.getLogger(WebserviceParamsImpl.class);

    /**
     * Get the search-related parameters from the request object.
     * This ignores stuff like the requested output type, etc.
     * Note also that the request type is not part of the SearchParameters, so from
     * looking at these parameters alone, you can't always tell what type of search
     * we're doing. The RequestHandler subclass will add a jobclass parameter when
     * executing the actual search.
     *
     * @param isDocs    is this a docs operation? influences how the "sort" parameter
     *                  is interpreted
     * @param isDebugMode can this client access debug functionality?
     * @param params parameters sent to webservice
     * @return the unique key
     */
    public static WebserviceParamsImpl get(boolean isDocs, boolean isDebugMode, QueryParams params) {
        return new WebserviceParamsImpl(isDocs, isDebugMode, params);
    }

    private final QueryParams params;

    private final boolean debugMode;

    /** The pattern, if parsed already */
    private TextPattern pattern;

    /** The filter query, if parsed already */
    private Query filterQuery;

    /** If set, keep only hits from these global doc ids (filterQuery will be ignored).
        Note that this MUST already be sorted! */
    private Iterable<Integer> acceptedDocs;

    private final boolean isDocsOperation;

    private List<DocProperty> facetProps;

    private String overrideDocPid;

    private String overrideAnnotationName;

    private String fieldName;

    private String inputFormat;

    private WebserviceParamsImpl(boolean isDocsOperation, boolean isDebugMode,
            QueryParams params) {
        this.isDocsOperation = isDocsOperation;
        this.debugMode = isDebugMode;
        this.params = params;
    }

    @Override
    public BlackLabIndex blIndex() {
        try {
            return getSearchManager().getIndexManager().getIndex(getCorpusName()).blIndex();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public SearchManager getSearchManager() {
        return params.getSearchManager();
    }

    @Override
    public User getUser() {
        return params.getUser();
    }

    @Override
    public boolean hasPattern() throws BlsException {
        return pattern().isPresent();
    }

    @Override
    public Optional<TextPattern> pattern() throws BlsException {
        if (pattern == null) {
            pattern = WebserviceParamsUtils.parsePattern(blIndex(), getPattern(), getPattLanguage(), getPattGapData());
        }
        String tagName = getContext().inlineTagName();
        if (tagName != null) {
            if (!isWithinTag(tagName)) {
                // add "within <TAGNAME/>" to the pattern, so we can produce the requested context later
                pattern = new TextPatternPositionFilter(pattern,
                        new TextPatternTags(tagName, null, null),
                        SpanQueryPositionFilter.Operation.WITHIN);
            }
        }
        return pattern == null ? Optional.empty() : Optional.of(pattern);
    }

    private boolean isWithinTag(String tagName) {
        return pattern instanceof TextPatternPositionFilter && ((TextPatternPositionFilter) pattern).isWithinTag(
                tagName);
    }

    public void setDocPid(String pid) {
        overrideDocPid = pid;
    }

    @Override
    public String getDocPid() {
        if (overrideDocPid != null)
            return overrideDocPid;
        return params.getDocPid();
    }

    @Override
    public Query filterQuery() throws BlsException {
        if (filterQuery == null) {
            filterQuery = WebserviceParamsUtils.parseFilterQuery(blIndex(), getDocPid(), getDocumentFilterQuery(),
                    getDocumentFilterLanguage());
        }
        return filterQuery;
    }

    @Override
    public void setFilterQuery(Query query) {
        this.filterQuery = query;
    }

    /**
     * @return hits - filtered then sorted then sampled then windowed
     */
    @Override
    public SearchHits hitsWindow() throws BlsException {
        WindowSettings windowSettings = windowSettings();
        if (windowSettings == null)
            return hitsSample();
        return hitsSample().window(windowSettings.first(), windowSettings.size());
    }

    @Override
    public WindowSettings windowSettings() {
        long size = Math.min(Math.max(0, getNumberOfResultsToShow()), configParam().getPageSize().getMax());
        return new WindowSettings(getFirstResultToShow(), size);
    }

    @Override
    public boolean getIncludeGroupContents() {
        return params.getIncludeGroupContents() || configParam().isWriteHitsAndDocsInGroupedHits();
    }

    @Override
    public boolean getOmitEmptyCaptures() {
        return params.getOmitEmptyCaptures() || configParam().isOmitEmptyCaptures();
    }

    @Override
    public String getReturnMatchInfo() {
        return params.getReturnMatchInfo().isEmpty() ? configParam().getReturnMatchInfo() : params.getReturnMatchInfo();
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
        if (groupProps.isPresent()) {
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

    @Override
    public HitSortSettings hitsSortSettings() {
        if (isDocsOperation)
            return null; // we're doing per-docs stuff, so sort doesn't apply to hits

        Optional<String> groupBy = getGroupProps();
        if (groupBy.isPresent()) {
            // looking at groups, or results within a group, don't bother sorting the underlying results
            // themselves (sorting is explicitly ignored anyway in ResultsGrouper::init)
            return null;
        }

        Optional<String> sortBy = getSortProps();
        if (!sortBy.isPresent())
            return null;
        HitProperty sortProp = HitProperty.deserialize(blIndex(), blIndex().mainAnnotatedField(), sortBy.get());
        return new HitSortSettings(sortProp);
    }

    @Override
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
                DocProperty propFacets = DocProperty.deserialize(blIndex(), facets.get());
                if (propFacets == null)
                    facetProps = null;
                else {
                    facetProps = new ArrayList<>();
                    for (DocProperty prop: propFacets.propsList())
                        facetProps.add(prop);
                }
            }
        }
        return facetProps;
    }

    @Override
    public boolean hasFacets() {
        return facetProps() != null;
    }

    @Override
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

    @Override
    public ContextSettings contextSettings() {
        ContextSize context = getContext().clampedTo(configParam().getContextSize().getMaxInt());
        return new ContextSettings(context, getConcordanceType());
    }

    @Override
    public boolean useCache() {
        return !debugMode || getUseCache();
    }

    private int forwardIndexMatchFactor() {
        return debugMode ? getForwardIndexMatchFactor() : -1;
    }



    // -------- Create Search instances -----------

    /**
     * @return hits - filtered then sorted then sampled
     */
    @Override
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
            Query filter = filterQuery();
            Optional<TextPattern> pattern = pattern();
            if (!pattern.isPresent())
                throw new BadRequest("NO_PATTERN_GIVEN", "Text search pattern required. Please specify 'patt' parameter.");

            SearchSettings searchSettings = searchSettings();
            return search.find(pattern.get().toQuery(search.queryInfo(), filter), searchSettings);
        } catch (InvalidQuery e) {
            throw new BadRequest("PATT_SYNTAX_ERROR", "Syntax error in CorpusQL pattern: " + e.getMessage());
        }
    }

    @Override
    public SearchDocs docsWindow() throws BlsException {
        WindowSettings windowSettings = windowSettings();
        if (windowSettings == null)
            return docsSorted();
        return docsSorted().window(windowSettings.first(), windowSettings.size());
    }

    @Override
    public SearchDocs docsSorted() throws BlsException {
        DocSortSettings docSortSettings = docSortSettings();
        if (docSortSettings == null)
            return docs();
        return docs().sort(docSortSettings.sortBy());
    }

    @Override
    public SearchCount docsCount() throws BlsException {
        if (pattern().isPresent())
            return hitsSample().docCount();
        return docs().count();
    }

    @Override
    public SearchDocs docs() throws BlsException {
        Optional<TextPattern> pattern = pattern();
        if (pattern.isPresent())
            return hitsSample().docs(-1);
        Query docFilterQuery = filterQuery();
        if (!pattern.isPresent() && docFilterQuery == null) {
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
    @Override
    public SearchDocs subcorpus() throws BlsException {
        Query docFilterQuery = filterQuery();
        if (docFilterQuery == null) {
            docFilterQuery = blIndex().getAllRealDocsQuery();
        }
        SearchEmpty search = blIndex().search(null, useCache());
        return search.findDocuments(docFilterQuery);
    }

    @Override
    public SearchHitGroups hitsGroupedStats() throws BlsException {
        String groupProps = hitGroupSettings().groupBy();
        HitProperty prop = HitProperty.deserialize(blIndex(), blIndex().mainAnnotatedField(), groupProps);
        if (prop == null)
            throw new BadRequest("UNKNOWN_GROUP_PROPERTY", "Unknown group property '" + groupProps + "'.");
        return hitsSample().groupStats(prop, Results.NO_LIMIT).sort(hitGroupSortSettings().sortBy());
    }

    @Override
    public SearchHitGroups hitsGroupedWithStoredHits() throws BlsException {
        String groupProps = hitGroupSettings().groupBy();
        HitProperty prop = HitProperty.deserialize(blIndex(), blIndex().mainAnnotatedField(), groupProps);
        if (prop == null)
            throw new BadRequest("UNKNOWN_GROUP_PROPERTY", "Unknown group property '" + groupProps + "'.");
        return hitsSample().groupWithStoredHits(prop, Results.NO_LIMIT).sort(hitGroupSortSettings().sortBy());
    }

    @Override
    public SearchDocGroups docsGrouped() throws BlsException {
        return docs().group(docGroupSettings().groupBy(), Results.NO_LIMIT).sort(docGroupSortSettings().sortBy());
    }

    @Override
    public SearchFacets facets() throws BlsException {
        return docs().facet(facetProps());
    }

    @Override
    public Map<WebserviceParameter, String> getParameters() {
        return params.getParameters();
    }

    @Override
    public String getCorpusName() {
        return params.getCorpusName();
    }

    @Override
    public String getPattern() {
        return params.getPattern();
    }

    @Override
    public String getPattLanguage() {
        return params.getPattLanguage();
    }

    @Override
    public String getPattGapData() {
        return params.getPattGapData();
    }

    @Override
    public String getDocumentFilterQuery() {
        return params.getDocumentFilterQuery();
    }

    @Override
    public String getDocumentFilterLanguage() {
        return params.getDocumentFilterLanguage();
    }

    @Override
    public String getHitFilterCriterium() {
        return params.getHitFilterCriterium();
    }

    @Override
    public String getHitFilterValue() {
        return params.getHitFilterValue();
    }

    @Override
    public Optional<Double> getSampleFraction() {
        return params.getSampleFraction();
    }

    @Override
    public Optional<Integer> getSampleNumber() {
        return params.getSampleNumber();
    }

    @Override
    public Optional<Long> getSampleSeed() {
        return params.getSampleSeed();
    }

    @Override
    public boolean getUseCache() {
        return params.getUseCache();
    }

    @Override
    public int getForwardIndexMatchFactor() {
        return params.getForwardIndexMatchFactor();
    }

    @Override
    public long getMaxRetrieve() {
        return params.getMaxRetrieve();
    }

    @Override
    public long getMaxCount() {
        return params.getMaxCount();
    }

    @Override
    public long getFirstResultToShow() {
        return params.getFirstResultToShow();
    }

    @Override
    public Optional<Long> optNumberOfResultsToShow() {
        return params.optNumberOfResultsToShow();
    }

    @Override
    public long getNumberOfResultsToShow() {
        return params.getNumberOfResultsToShow();
    }

    @Override
    @Deprecated
    public int getWordsAroundHit() {
        return params.getWordsAroundHit();
    }

    public ContextSize getContext() {
        return params.getContext();
    }

    @Override
    public ConcordanceType getConcordanceType() {
        return params.getConcordanceType();
    }

    @Override
    public Optional<String> getFacetProps() {
        return params.getFacetProps();
    }

    @Override
    public Optional<String> getGroupProps() {
        return params.getGroupProps();
    }

    @Override
    public Optional<String> getSortProps() {
        return params.getSortProps();
    }

    @Override
    public Optional<String> getViewGroup() {
        return params.getViewGroup();
    }

    @Override
    public Collection<String> getListValuesFor() {
        return params.getListValuesFor();
    }

    @Override
    public Collection<String> getListMetadataValuesFor() {
        return params.getListMetadataValuesFor();
    }

    @Override
    public Collection<String> getListSubpropsFor() {
        return params.getListSubpropsFor();
    }

    @Override
    public boolean getWaitForTotal() {
        return params.getWaitForTotal();
    }

    @Override
    public boolean getIncludeTokenCount() {
        return params.getIncludeTokenCount();
    }

    @Override
    public boolean getCsvIncludeSummary() {
        return params.getCsvIncludeSummary();
    }

    @Override
    public boolean getCsvDeclareSeparator() {
        return params.getCsvDeclareSeparator();
    }

    @Override
    public boolean getExplain() {
        return params.getExplain();
    }

    @Override
    public boolean getSensitive() {
        return params.getSensitive();
    }

    @Override
    public int getWordStart() {
        return params.getWordStart();
    }

    @Override
    public int getWordEnd() {
        return params.getWordEnd();
    }

    @Override
    public Optional<Integer> getHitStart() {
        return params.getHitStart();
    }

    @Override
    public int getHitEnd() {
        return params.getHitEnd();
    }

    @Override
    public String getAutocompleteTerm() {
        return params.getAutocompleteTerm();
    }

    @Override
    public boolean isCalculateCollocations() {
        return params.isCalculateCollocations();
    }

    @Override
    public String getAnnotationName() {
        if (overrideAnnotationName != null)
            return overrideAnnotationName;
        return params.getAnnotationName();
    }

    public void setAnnotationName(String annotationName) {
        overrideAnnotationName = annotationName;
    }

    @Override
    public String getFieldName() {
        return fieldName == null ? params.getFieldName() : fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    @Override
    public Set<String> getTerms() {
        return params.getTerms();
    }

    @Override
    public boolean isIncludeDebugInfo() {
        return params.isIncludeDebugInfo();
    }

    @Override
    public WebserviceOperation getOperation() {
        return params.getOperation();
    }

    @Override
    public ApiVersion apiCompatibility() { return params.apiCompatibility(); }

    public Optional<String> getInputFormat() {
        if (inputFormat != null)
            return Optional.of(inputFormat);
        return params.getInputFormat();
    }

    public void setInputFormat(String inputFormat) {
        this.inputFormat = inputFormat;
    }
}
