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
import nl.inl.blacklab.resultproperty.DocPropertyMultiple;
import nl.inl.blacklab.resultproperty.HitGroupProperty;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.SingleDocIdFilter;
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
 * Wraps the WebserviceParams and interprets them to create searches.
 */
public class SearchCreator implements WebserviceParams {
    private static final Logger logger = LogManager.getLogger(SearchCreator.class);

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
    public static SearchCreator get(SearchManager searchMan, boolean isDocs, boolean isDebugMode,
            WebserviceParams params) {
        return new SearchCreator(searchMan, isDocs, isDebugMode, params);
    }

    private WebserviceParams params;

    /** The search manager, for querying default value for missing parameters */
    private final SearchManager searchManager;

    private boolean debugMode;

    /** The pattern, if parsed already */
    private TextPattern pattern;

    /** The filter query, if parsed already */
    private Query filterQuery;

    private final boolean isDocsOperation;

    private List<DocProperty> facetProps;

    private String filterByDocPid;

    private SearchCreator(SearchManager searchManager, boolean isDocsOperation, boolean isDebugMode,
            WebserviceParams params) {
        this.searchManager = searchManager;
        this.isDocsOperation = isDocsOperation;
        this.debugMode = isDebugMode;
        this.params = params;
    }

    public BlackLabIndex blIndex() {
        try {
            return searchManager.getIndexManager().getIndex(getIndexName()).blIndex();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private BLSConfigParameters configParam() {
        return searchManager.config().getParameters();
    }

    public boolean hasPattern() throws BlsException {
        return pattern().isPresent();
    }

    public Optional<TextPattern> pattern() throws BlsException {
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
        return pattern == null ? Optional.empty() : Optional.of(pattern);
    }

    public boolean hasFilter() throws BlsException {
        return filterQuery() != null;
    }

    public void setFilterByDocumentPid(String pid) {
        filterByDocPid = pid;
    }

    public String getDocPid() {
        if (filterByDocPid != null)
            return filterByDocPid;
        return params.getDocPid();
    }

    public Query filterQuery() throws BlsException {
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
            Optional<TextPattern> pattern = pattern();
            if (!pattern.isPresent())
                throw new BadRequest("NO_PATTERN_GIVEN", "Text search pattern required. Please specify 'patt' parameter.");

            SearchSettings searchSettings = searchSettings();
            return search.find(pattern.get().toQuery(search.queryInfo(), filter), searchSettings);
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
        if (pattern().isPresent())
            return hitsSample().docCount();
        return docs().count();
    }

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

    @Override
    public Map<String, String> getParameters() {
        return params.getParameters();
    }

    @Override
    public String getIndexName() {
        return params.getIndexName();
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
    public int getWordsAroundHit() {
        return params.getWordsAroundHit();
    }

    @Override
    public ConcordanceType getConcordanceType() {
        return params.getConcordanceType();
    }

    @Override
    public boolean getIncludeGroupContents() {
        return params.getIncludeGroupContents();
    }

    @Override
    public boolean getOmitEmptyCaptures() {
        return params.getOmitEmptyCaptures();
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
    public String getAnnotation() {
        return params.getAnnotation();
    }

    @Override
    public Set<String> getTerms() {
        return params.getTerms();
    }
}
