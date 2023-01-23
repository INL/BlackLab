package nl.inl.blacklab.server.lib.results;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.Concordance;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.Kwic;
import nl.inl.blacklab.search.QueryExplanation;
import nl.inl.blacklab.search.Span;
import nl.inl.blacklab.search.TermFrequency;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.AnnotationGroup;
import nl.inl.blacklab.search.indexmetadata.AnnotationGroups;
import nl.inl.blacklab.search.indexmetadata.AnnotationSensitivity;
import nl.inl.blacklab.search.indexmetadata.Annotations;
import nl.inl.blacklab.search.indexmetadata.IndexMetadata;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.indexmetadata.ValueListComplete;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.results.Concordances;
import nl.inl.blacklab.search.results.ContextSize;
import nl.inl.blacklab.search.results.CorpusSize;
import nl.inl.blacklab.search.results.DocGroup;
import nl.inl.blacklab.search.results.DocGroups;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.Hit;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.Kwics;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.ResultGroups;
import nl.inl.blacklab.search.results.ResultsStats;
import nl.inl.blacklab.search.results.ResultsStatsStatic;
import nl.inl.blacklab.search.results.SampleParameters;
import nl.inl.blacklab.search.results.WindowStats;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.index.Index;
import nl.inl.blacklab.server.lib.ConcordanceContext;
import nl.inl.blacklab.server.lib.ResultIndexMetadata;
import nl.inl.blacklab.server.lib.SearchTimings;
import nl.inl.blacklab.server.lib.WebserviceParams;

/**
 * Utilities for serializing BlackLab responses using DataStream.
 */
public class DStream {
    static final Logger logger = LogManager.getLogger(DStream.class);

    public static final String KEY_BLACKLAB_BUILD_TIME = "blacklabBuildTime";

    private static final String KEY_BLACKLAB_VERSION = "blacklabVersion";

    private static final String KEY_API_VERSION = "apiVersion";

    /** What version of responses to write. */
    private static ApiVersion apiVersion = ApiVersion.V3;

    public static void setApiVersion(ApiVersion apiVersion) {
        DStream.apiVersion = apiVersion;
    }

    private DStream() {}

    /**
     * Add info about the current logged-in user (if any) to the response.
     *
     * @param ds output stream
     * @param userInfo user info to show
     */
    public static void userInfo(DataStream ds, ResultUserInfo userInfo) {
        ds.startEntry("user").startMap();
        {
            ds.entry("loggedIn", userInfo.isLoggedIn());
            if (userInfo.isLoggedIn())
                ds.entry("id", userInfo.getUserId());
            ds.entry("canCreateIndex", userInfo.canCreateIndex());
        }
        ds.endMap().endEntry();
    }

    /**
     * Add info about metadata fields to hits and docs results.
     *
     * Note that this information can be retrieved using different requests,
     * and it is redundant to send it with every query response. We may want
     * to deprecate this in the future.
     *
     * @param ds output stream
     * @param docFields document field info to write
     * @param metaDisplayNames display name info to write
     */
    public static void metadataFieldInfo(DataStream ds, Map<String, String> docFields, Map<String, String> metaDisplayNames) {
        ds.startEntry("docFields").startMap();
        for (Map.Entry<String, String> e: docFields.entrySet()) {
            ds.entry(e.getKey(), e.getValue());
        }
        ds.endMap().endEntry();

        ds.startEntry("metadataFieldDisplayNames").startMap();
        for (Map.Entry<String, String> e: metaDisplayNames.entrySet()) {
            ds.entry(e.getKey(), e.getValue());
        }
        ds.endMap().endEntry();
    }

    /**
     * Stream document information (metadata, contents authorization)
     *
     * @param ds where to stream information
     * @param docInfos infos to write
     */
    public static void documentInfos(DataStream ds, Map<String, ResultDocInfo> docInfos) {
        ds.startEntry("docInfos").startMap();
        for (Map.Entry<String, ResultDocInfo> e: docInfos.entrySet()) {
            ds.startAttrEntry("docInfo", "pid", e.getKey());
            {
                documentInfo(ds, e.getValue());
            }
            ds.endAttrEntry();
        }
        ds.endMap().endEntry();
    }

    /**
     * Stream document information (metadata, contents authorization)
     *
     * @param ds where to stream information
     * @param docInfo info to stream
     */
    public static void documentInfo(DataStream ds, ResultDocInfo docInfo) {
        ds.startMap();
        {
            for (Map.Entry<String, List<String>> e: docInfo.getMetadata().entrySet()) {
                ds.startEntry(e.getKey()).startList();
                {
                    for (String v: e.getValue()) {
                        ds.item("value", v);
                    }
                }
                ds.endList().endEntry();
            }
            if (docInfo.getLengthInTokens() != null)
                ds.entry("lengthInTokens", docInfo.getLengthInTokens());
            ds.entry("mayView", docInfo.isMayView());
        }
        ds.endMap();
    }

    public static void metadataGroupInfo(DataStream ds, Map<String, List<String>> metadataFieldGroups) {
        ds.startEntry("metadataFieldGroups").startList();
        for (Map.Entry<String, List<String>> e: metadataFieldGroups.entrySet()) {
            ds.startItem("metadataFieldGroup").startMap();
            {
                ds.entry("name", e.getKey());
                ds.startEntry("fields").startList();
                for (String field: e.getValue()) {
                    ds.item("field", field);
                }
                ds.endList().endEntry();
            }
            ds.endMap().endItem();
        }
        ds.endList().endEntry();
    }

    public static void facets(DataStream ds, Map<String, List<Pair<String, Long>>> facetInfo) {
        ds.startMap();
        for (Map.Entry<String, List<Pair<String,  Long>>> e: facetInfo.entrySet()) {
            String facetBy = e.getKey();
            List<Pair<String,  Long>> facetCounts = e.getValue();
            ds.startAttrEntry("facet", "name", facetBy).startList();
            for (Pair<String, Long> count : facetCounts) {
                ds.startItem("item").startMap()
                        .entry("value", count.getLeft())
                        .entry("size", count.getRight())
                        .endMap().endItem();
            }
            ds.endList().endAttrEntry();
        }
        ds.endMap();
    }

    /**
     * Output most of the fields of the search summary.
     *
     * @param ds where to output XML/JSON
     * @param summaryFields info for the fields to write
     */
    public static void summaryCommonFields(DataStream ds, ResultSummaryCommonFields summaryFields) throws BlsException {

        WebserviceParams searchParam = summaryFields.getSearchParam();
        Index.IndexStatus indexStatus = summaryFields.getIndexStatus();
        SearchTimings timings = summaryFields.getTimings();
        ResultGroups<?> groups = summaryFields.getGroups();
        WindowStats window = summaryFields.getWindow();

        // Our search parameters
        ds.startEntry("searchParam");
        ds.startMap();
        for (Map.Entry<String, String> e: searchParam.getParameters().entrySet()) {
            ds.entry(e.getKey(), e.getValue());
        }
        ds.endMap();
        ds.endEntry();

        if (indexStatus != null && indexStatus != Index.IndexStatus.AVAILABLE) {
            ds.entry("indexStatus", indexStatus.toString());
        }

        // Information about hit sampling
        SampleParameters sample = searchParam.sampleSettings();
        if (sample != null) {
            ds.entry("sampleSeed", sample.seed());
            if (sample.isPercentage())
                ds.entry("samplePercentage", Math.round(sample.percentageOfHits() * 100 * 100) / 100.0);
            else
                ds.entry("sampleSize", sample.numberOfHitsSet());
        }

        // Information about search progress
        ds.entry("searchTime", timings.getProcessingTime());
        ds.entry("countTime", timings.getCountTime());

        // Information about grouping operation
        if (groups != null) {
            ds.entry("numberOfGroups", groups.size())
                    .entry("largestGroupSize", groups.largestGroupSize());
        }

        // Information about our viewing window
        if (window != null) {
            ds.entry("windowFirstResult", window.first())
                    .entry("requestedWindowSize", window.requestedWindowSize())
                    .entry("actualWindowSize", window.windowSize())
                    .entry("windowHasPrevious", window.hasPrevious())
                    .entry("windowHasNext", window.hasNext());
        }
    }

    public static void summaryNumHits(DataStream ds, ResultSummaryNumHits result) {
        ResultsStats hitsStats = result.getHitsStats();
        ResultsStats docsStats = result.getDocsStats();
        boolean countFailed = result.isCountFailed();
        boolean waitForTotal = result.isWaitForTotal();
        CorpusSize subcorpusSize = result.getSubcorpusSize();

        // Information about the number of hits/docs, and whether there were too many to retrieve/count
        // We have a hits object we can query for this information

        if (hitsStats == null)
            hitsStats = ResultsStatsStatic.INVALID;
        long hitsCounted = countFailed ? -1 : (waitForTotal ? hitsStats.countedTotal() : hitsStats.countedSoFar());
        long hitsProcessed = waitForTotal ? hitsStats.processedTotal() : hitsStats.processedSoFar();
        if (docsStats == null)
            docsStats = ResultsStatsStatic.INVALID;
        long docsCounted = countFailed ? -1 : (waitForTotal ? docsStats.countedTotal() : docsStats.countedSoFar());
        long docsProcessed = waitForTotal ? docsStats.processedTotal() : docsStats.processedSoFar();

        ds.entry("stillCounting", !hitsStats.done());
        ds.entry("numberOfHits", hitsCounted)
                .entry("numberOfHitsRetrieved", hitsProcessed)
                .entry("stoppedCountingHits", hitsStats.maxStats().hitsCountedExceededMaximum())
                .entry("stoppedRetrievingHits", hitsStats.maxStats().hitsProcessedExceededMaximum());
        ds.entry("numberOfDocs", docsCounted)
                .entry("numberOfDocsRetrieved", docsProcessed);
        if (subcorpusSize != null) {
            subcorpusSize(ds, subcorpusSize);
        }
    }

    public static void summaryNumDocs(DataStream ds, ResultSummaryNumDocs result) {
        DocResults docResults = result.getDocResults();
        // Information about the number of hits/docs, and whether there were too many to retrieve/count
        ds.entry("stillCounting", false);
        if (result.isViewDocGroup()) {
            // Viewing single group of documents, possibly based on a hits search.
            // group.getResults().getOriginalHits() returns null in this case,
            // so we have to iterate over the DocResults and sum up the hits ourselves.
            long numberOfHits = docResults.getNumberOfHits();
            ds  .entry("numberOfHits", numberOfHits)
                .entry("numberOfHitsRetrieved", numberOfHits);

        }
        long numberOfDocsRetrieved = docResults.size();
        long numberOfDocsCounted = numberOfDocsRetrieved;
        if (result.isCountFailed())
            numberOfDocsCounted = -1;
        ds  .entry("numberOfDocs", numberOfDocsCounted)
            .entry("numberOfDocsRetrieved", numberOfDocsRetrieved);
        CorpusSize subcorpusSize = result.getSubcorpusSize();
        if (subcorpusSize != null) {
            subcorpusSize(ds, subcorpusSize);
        }
    }

    public static void subcorpusSize(DataStream ds, CorpusSize subcorpusSize) {
        ds.startEntry("subcorpusSize").startMap()
                .entry("documents", subcorpusSize.getDocuments());
        if (subcorpusSize.hasTokenCount())
            ds.entry("tokens", subcorpusSize.getTokens());
        ds.endMap().endEntry();
    }

    public static void listOfHits(DataStream ds, ResultListOfHits result) throws BlsException {
        WebserviceParams params = result.getParams();
        Hits hits = result.getHits();

        ds.startEntry("hits").startList();
        for (Hit hit : hits) {
            ds.startItem("hit");
            {
                String docPid = result.getDocIdToPid().get(hit.doc());
                Map<String, Span> capturedGroups = null;
                if (hits.hasCapturedGroups()) {
                    capturedGroups = hits.capturedGroups().getMap(hit, params.omitEmptyCapture());
                    if (capturedGroups == null && logger != null)
                        logger.warn(
                                "MISSING CAPTURE GROUP: " + docPid + ", query: " + params.getPattern());
                }

                hit(ds, params, result.getConcordanceContext(), result.getAnnotationsToWrite(), hit, docPid,
                        capturedGroups);
            }
            ds.endItem();
        }
        ds.endList().endEntry();
    }

    private static void hit(DataStream ds, WebserviceParams params, ConcordanceContext concordanceContext,
            Collection<Annotation> annotationsToList, Hit hit, String docPid, Map<String, Span> capturedGroups) {
        ds.startMap();
        if (docPid != null) {
            // Add basic hit info
            ds.entry("docPid", docPid);
            ds.entry("start", hit.start());
            ds.entry("end", hit.end());
        }

        if (capturedGroups != null) {
            ds.startEntry("captureGroups").startList();
            for (Map.Entry<String, Span> capturedGroup : capturedGroups.entrySet()) {
                if (capturedGroup.getValue() != null) {
                    ds.startItem("group").startMap();
                    {
                        ds.entry("name", capturedGroup.getKey());
                        ds.entry("start", capturedGroup.getValue().start());
                        ds.entry("end", capturedGroup.getValue().end());
                    }
                    ds.endMap().endItem();
                }
            }
            ds.endList().endEntry();
        }

        ContextSize contextSize = params.contextSettings().size();
        boolean includeContext = contextSize.left() > 0 || contextSize.right() > 0;
        if (concordanceContext.isConcordances()) {
            // Add concordance from original XML
            Concordance c = concordanceContext.getConcordance(hit);
            if (includeContext) {
                ds.startEntry("left").xmlFragment(c.left()).endEntry()
                        .startEntry("match").xmlFragment(c.match()).endEntry()
                        .startEntry("right").xmlFragment(c.right()).endEntry();
            } else {
                ds.startEntry("match").xmlFragment(c.match()).endEntry();
            }
        } else {
            // Add KWIC info
            Kwic c = concordanceContext.getKwic(hit);
            if (includeContext) {
                ds.startEntry("left").contextList(c.annotations(), annotationsToList, c.left()).endEntry()
                        .startEntry("match").contextList(c.annotations(), annotationsToList, c.match()).endEntry()
                        .startEntry("right").contextList(c.annotations(), annotationsToList, c.right()).endEntry();
            } else {
                ds.startEntry("match").contextList(c.annotations(), annotationsToList, c.match()).endEntry();
            }
        }
        ds.endMap();
    }

    public static void indexProgress(DataStream ds, ResultIndexStatus progress)
            throws BlsException {
        if (progress.getIndexStatus().equals(Index.IndexStatus.INDEXING)) {
            ds.startEntry("indexProgress").startMap()
                    .entry("filesProcessed", progress.getFiles())
                    .entry("docsDone", progress.getDocs())
                    .entry("tokensProcessed", progress.getTokens())
                    .endMap().endEntry();
        }

        String formatIdentifier = progress.getDocumentFormat();
        if (formatIdentifier != null && formatIdentifier.length() > 0)
            ds.entry("documentFormat", formatIdentifier);
    }

    public static void metadataField(DataStream ds, ResultMetadataField metadataField) {
        String indexName = metadataField.getIndexName();
        MetadataField fd = metadataField.getFieldDesc();
        boolean listValues = metadataField.isListValues();
        Map<String, Integer> fieldValuesInOrder = metadataField.getFieldValues();

        ds.startMap();
        // (we report false for ValueListComplete.UNKNOWN - this usually means there's no values either way)
        boolean valueListComplete = fd.isValueListComplete().equals(ValueListComplete.YES);

        // Assemble response
        if (indexName != null)
            ds.entry("indexName", indexName);
        ds.entry("fieldName", fd.name())
                .entry("isAnnotatedField", false)
                .entry("displayName", fd.displayName())
                .entry("description", fd.description())
                .entry("uiType", fd.custom().get("uiType"));
        ds
                .entry("type", fd.type().toString())
                .entry("analyzer", fd.analyzerName())
                .entry("unknownCondition", fd.custom().get("unknownCondition").toString().toUpperCase())
                .entry("unknownValue", fd.custom().get("unknownValue"));
        if (listValues) {
            final Map<String, String> displayValues = fd.custom().get("displayValues",
                    Collections.emptyMap());
            ds.startEntry("displayValues").startMap();
            for (Map.Entry<String, String> e : displayValues.entrySet()) {
                ds.attrEntry("displayValue", "value", e.getKey(), e.getValue());
            }
            ds.endMap().endEntry();

            // Show values in display order (if defined)
            // If not all values are mentioned in display order, show the rest at the end,
            // sorted by their displayValue (or regular value if no displayValue specified)
            ds.startEntry("fieldValues").startMap();
            for (Map.Entry<String, Integer> e: fieldValuesInOrder.entrySet()) {
                ds.attrEntry("value", "text", e.getKey(), e.getValue());
            }
            ds.endMap().endEntry();
            ds.entry("valueListComplete", valueListComplete);
        }
        ds.endMap();
    }

    public static void annotatedField(DataStream ds, ResultAnnotatedField annotatedField) {
        String indexName = annotatedField.getIndexName();
        AnnotatedField fieldDesc = annotatedField.getFieldDesc();
        Map<String, ResultAnnotationInfo> annotInfos = annotatedField.getAnnotInfos();

        ds.startMap();
        if (indexName != null)
            ds.entry("indexName", indexName);
        Annotations annotations = fieldDesc.annotations();
        ds
                .entry("fieldName", fieldDesc.name())
                .entry("isAnnotatedField", true)
                .entry("displayName", fieldDesc.displayName())
                .entry("description", fieldDesc.description())
                .entry("hasContentStore", fieldDesc.hasContentStore())
                .entry("hasXmlTags", fieldDesc.hasXmlTags());
        ds.entry("mainAnnotation", annotations.main().name());
        ds.startEntry("displayOrder").startList();
        annotations.stream().map(Annotation::name).forEach(id -> ds.item("fieldName", id));
        ds.endList().endEntry();

        ds.startEntry("annotations").startMap();
        for (Map.Entry<String, ResultAnnotationInfo> e: annotInfos.entrySet()) {
            ds.startAttrEntry("annotation", "name", e.getKey()).startMap();
            ResultAnnotationInfo ai = e.getValue();
            Annotation annotation = ai.getAnnotation();
            AnnotationSensitivity offsetsSensitivity = annotation.offsetsSensitivity();
            String offsetsAlternative = offsetsSensitivity == null ? "" :
                    offsetsSensitivity.sensitivity().luceneFieldSuffix();
            ds
                    .entry("displayName", annotation.displayName())
                    .entry("description", annotation.description())
                    .entry("uiType", annotation.uiType())
                    .entry("hasForwardIndex", annotation.hasForwardIndex())
                    .entry("sensitivity", annotation.sensitivitySetting().stringValueForResponse())
                    .entry("offsetsAlternative", offsetsAlternative)
                    .entry("isInternal", annotation.isInternal());
            if (ai.isShowValues()) {
                ds.startEntry("values").startList();
                for (String term: ai.getTerms()) {
                    ds.item("value", term);
                }
                ds.endList().endEntry();
                ds.entry("valueListComplete", ai.isValueListComplete());
            }
            if (!annotation.subannotationNames().isEmpty()) {
                ds.startEntry("subannotations").startList();
                for (String name: annotation.subannotationNames()) {
                    ds.item("subannotation", name);
                }
                ds.endList().endEntry();
            }
            if (annotation.isSubannotation()) {
                ds.entry("parentAnnotation", annotation.parentAnnotation().name());
            }
            ds.endMap().endAttrEntry();
        }
        ds.endMap().endEntry();
        ds.endMap();
    }

    public static void collocationsResponse(DataStream ds, TermFrequencyList tfl) {
        ds.startMap().startEntry("tokenFrequencies").startMap();
        for (TermFrequency tf : tfl) {
            ds.attrEntry("token", "text", tf.term, tf.frequency);
        }
        ds.endMap().endEntry().endMap();
    }

    public static void hitsResponse(DataStream ds, ResultHits resultHits)
            throws InvalidQuery {
        WebserviceParams params = resultHits.getParams();
        BlackLabIndex index = params.blIndex();
        // Search time should be time user (originally) had to wait for the response to this request.
        // Count time is the time it took (or is taking) to iterate through all the results to count the total.
        ResultSummaryNumHits result = resultHits.getSummaryNumHits();
        ResultSummaryCommonFields summaryFields = resultHits.getSummaryCommonFields();
        ResultListOfHits listOfHits = resultHits.getListOfHits();

        ds.startMap();


        // The summary
        ds.startEntry("summary").startMap();
        {
            summaryCommonFields(ds, summaryFields);
            summaryNumHits(ds, result);
            if (params.getIncludeTokenCount())
                ds.entry("tokensInMatchingDocuments", resultHits.getTotalTokens());

            // Write docField (pidField, titleField, etc.) and metadata display names
            // (these arguably shouldn't be included with every hits response; can be read once from the index
            //  metadata response)
            metadataFieldInfo(ds, resultHits.getDocFields(), resultHits.getMetaDisplayNames());

            // Include explanation of how the query was executed?
            if (params.getExplain()) {
                TextPattern tp = params.pattern().orElseThrow();
                try {
                    BLSpanQuery q = tp.toQuery(QueryInfo.create(index));
                    QueryExplanation explanation = index.explain(q);
                    ds.startEntry("explanation").startMap()
                            .entry("originalQuery", explanation.originalQuery())
                            .entry("rewrittenQuery", explanation.rewrittenQuery())
                            .endMap().endEntry();
                } catch (InvalidQuery e) {
                    throw new BadRequest("INVALID_QUERY", e.getMessage());
                }
            }
        }
        ds.endMap().endEntry();

        // Hits and docInfos
        listOfHits(ds, listOfHits);
        documentInfos(ds, resultHits.getDocInfos());

        // Facets (if requested)
        if (resultHits.hasFacets()) {
            // Now, group the docs according to the requested facets.
            ds.startEntry("facets");
            {
                facets(ds, resultHits.getFacetInfo());
            }
            ds.endEntry();
        }

        ds.endMap();
    }

    public static void hitsGroupedResponse(DataStream ds, ResultHitsGrouped hitsGrouped) {
        WebserviceParams params = hitsGrouped.getParams();
        ResultSummaryCommonFields summaryFields = hitsGrouped.getSummaryFields();
        ResultSummaryNumHits result = hitsGrouped.getSummaryNumHits();

        ds.startMap();

        // Summary
        ds.startEntry("summary").startMap();
        {
            summaryCommonFields(ds, summaryFields);
            summaryNumHits(ds, result);
        }
        ds.endMap().endEntry();

        ds.startEntry("hitGroups").startList();

        List<ResultHitGroup> groupInfos = hitsGrouped.getGroupInfos();
        for (ResultHitGroup groupInfo: groupInfos) {

            ds.startItem("hitgroup").startMap();
            {
                ds
                        .entry("identity", groupInfo.getIdentity())
                        .entry("identityDisplay", groupInfo.getIdentityDisplay())
                        .entry("size", groupInfo.getSize());

                ds.startEntry("properties").startList();
                for (Pair<String, String> p: groupInfo.getProperties()) {
                    ds.startItem("property").startMap();
                    {
                        ds.entry("name", p.getKey());
                        ds.entry("value", p.getValue());
                    }
                    ds.endMap().endItem();
                }
                ds.endList().endEntry();

                ds.entry("numberOfDocs", groupInfo.getNumberOfDocsInGroup());
                if (hitsGrouped.getMetadataGroupProperties() != null) {
                    subcorpusSize(ds, groupInfo.getSubcorpusSize());
                }

                if (groupInfo.getListOfHits() != null) {
                    listOfHits(ds, groupInfo.getListOfHits());
                }
            }
            ds.endMap().endItem();
        }
        ds.endList().endEntry();

        if (params.includeGroupContents()) {
            documentInfos(ds, hitsGrouped.getDocInfos());
        }
        ds.endMap();
    }

    public static void docsGroupedResponse(DataStream ds, ResultDocsGrouped result) {
        DocGroups groups = result.getGroups();
        WindowStats ourWindow = result.getOurWindow();

        ds.startMap();

        // The summary
        ds.startEntry("summary").startMap();

        summaryCommonFields(ds, result.getSummaryFields());

        if (result.getNumResultDocs() != null) {
            summaryNumDocs(ds, result.getNumResultDocs());
        } else {
            summaryNumHits(ds, result.getNumResultHits());
        }

        ds.endMap().endEntry();

        ds.startEntry("docGroups").startList();
        Iterator<CorpusSize> it = result.getCorpusSizes().iterator();
        /* Gather group values per property:
         * In the case we're grouping by multiple values, the DocPropertyMultiple and PropertyValueMultiple will
         * contain the sub properties and values in the same order.
         */
        List<DocProperty> prop = groups.groupCriteria().propsList();
        for (long i = ourWindow.first(); i <= ourWindow.last(); ++i) {
            DocGroup group = groups.get(i);

            ds.startItem("docgroup").startMap()
                    .entry("identity", group.identity().serialize())
                    .entry("identityDisplay", group.identity().toString())
                    .entry("size", group.size());

            // Write the raw values for this group
            ds.startEntry("properties").startList();
            List<PropertyValue> valuesForGroup = group.identity().valuesList();
            for (int j = 0; j < prop.size(); ++j) {
                ds.startItem("property").startMap();
                ds.entry("name", prop.get(j).serialize());
                ds.entry("value", valuesForGroup.get(j).toString());
                ds.endMap().endItem();
            }
            ds.endList().endEntry();

            ds.entry("numberOfTokens", group.totalTokens());
            if (result.getParams().hasPattern()) {
                subcorpusSize(ds, it.next());
            }
            ds.endMap().endItem();
        }
        ds.endList().endEntry();

        ds.endMap();
    }

    public static void docsResponse(DataStream ds, ResultDocsResponse result) {
        ds.startMap();
        {
            // The summary
            ds.startEntry("summary").startMap();
            {
                summaryCommonFields(ds, result.getSummaryFields());
                if (result.getNumResultDocs() != null) {
                    summaryNumDocs(ds, result.getNumResultDocs());
                } else {
                    summaryNumHits(ds, result.getNumResultHits());
                }
                if (result.isIncludeTokenCount())
                    ds.entry("tokensInMatchingDocuments", result.getTotalTokens());

                metadataFieldInfo(ds, result.getDocFields(), result.getMetaDisplayNames());
            }
            ds.endMap().endEntry();

            // The hits and document info
            ds.startEntry("docs").startList();
            for (ResultDocResult docResult: result.getDocResults()) {
                docResult(ds, docResult);
            }
            ds.endList().endEntry();
            if (result.getFacetInfo() != null) {
                // Now, group the docs according to the requested facets.
                ds.startEntry("facets");
                {
                    facets(ds, result.getFacetInfo());
                }
                ds.endEntry();
            }
        }
        ds.endMap();
    }

    public static void docResult(DataStream ds, ResultDocResult result) {
        ds.startItem("doc").startMap();
        {
            // Combine all
            ds.entry("docPid", result.getPid());
            if (result.numberOfHits() > 0)
                ds.entry("numberOfHits", result.numberOfHits());

            // Doc info (metadata, etc.)
            ds.startEntry("docInfo");
            {
                documentInfo(ds, result.getDocInfo());
            }
            ds.endEntry();

            // Snippets
            Collection<Annotation> annotationsToList = result.getAnnotationsToList();
            if (result.numberOfHitsToShow() > 0) {
                ds.startEntry("snippets").startList();
                if (!result.hasConcordances()) {
                    // KWICs
                    for (Kwic k: result.getKwicsToShow()) {
                        ds.startItem("snippet").startMap();
                        {
                            // Add KWIC info
                            ds.startEntry("left").contextList(k.annotations(), annotationsToList, k.left())
                                    .endEntry();
                            ds.startEntry("match").contextList(k.annotations(), annotationsToList, k.match())
                                    .endEntry();
                            ds.startEntry("right").contextList(k.annotations(), annotationsToList, k.right())
                                    .endEntry();
                        }
                        ds.endMap().endItem();
                    }
                } else {
                    // Concordances from original content
                    for (Concordance c: result.getConcordancesToShow()) {
                        ds.startItem("snippet").startMap();
                        {
                            // Add concordance from original XML
                            ds.startEntry("left").xmlFragment(c.left()).endEntry()
                                    .startEntry("match").xmlFragment(c.match()).endEntry()
                                    .startEntry("right").xmlFragment(c.right()).endEntry();
                        }
                        ds.endMap().endItem();
                    }
                }
                ds.endList().endEntry();
            } // if snippets

        }
        ds.endMap().endItem();
    }

    public static void serverInfo(DataStream ds, ResultServerInfo result) {
        ds.startMap();
        ds.entry(KEY_API_VERSION, apiVersion.versionString());
        ds.entry(KEY_BLACKLAB_BUILD_TIME, BlackLab.buildTime())
                .entry(KEY_BLACKLAB_VERSION, BlackLab.version());

        ds.startEntry("indices").startMap();
        for (ResultIndexStatus indexStatus: result.getIndexStatuses()) {
            indexInfo(ds, indexStatus);
        }
        ds.endMap().endEntry();

        userInfo(ds, result.getUserInfo());

        if (result.isDebugMode()) {
            ds.startEntry("cacheStatus");
            ds.value(result.getParams().getSearchManager().getBlackLabCache().getStatus());
            ds.endEntry();
        }
        ds.endMap();
    }

    public static void indexInfo(DataStream ds, ResultIndexStatus progress) {
        Index index = progress.getIndex();
        IndexMetadata indexMetadata = progress.getMetadata();
        ds.startAttrEntry("index", "name", index.getId());
        {
            ds.startMap();
            {
                ds.entry("displayName", indexMetadata.custom().get("displayName", ""));
                ds.entry("description", indexMetadata.custom().get("description", ""));
                ds.entry("status", index.getStatus());
                indexProgress(ds, progress);
                ds.entry("timeModified", indexMetadata.timeModified());
                ds.entry("tokenCount", indexMetadata.tokenCount());
            }
            ds.endMap();
        }
        ds.endAttrEntry();
    }

    public static void indexMetadataResponse(DataStream ds, ResultIndexMetadata result) {
        IndexMetadata metadata = result.getMetadata();
        ds.startMap();
        {
            ds.entry("indexName", result.getProgress().getIndex().getId())
                    .entry("displayName", metadata.custom().get("displayName", ""))
                    .entry("description", metadata.custom().get("description", ""))
                    .entry("status", result.getProgress().getIndexStatus())
                    .entry("contentViewable", metadata.contentViewable())
                    .entry("textDirection", metadata.custom().get("textDirection", "ltr"));

            indexProgress(ds, result.getProgress());
            ds.entry("tokenCount", metadata.tokenCount());
            ds.entry("documentCount", metadata.documentCount());

            ds.startEntry("versionInfo").startMap()
                    .entry(apiVersion == ApiVersion.V3 ? "blackLabBuildTime" : KEY_BLACKLAB_BUILD_TIME, metadata.indexBlackLabBuildTime())
                    .entry(apiVersion == ApiVersion.V3 ? "blackLabVersion" : KEY_BLACKLAB_VERSION, metadata.indexBlackLabVersion())
                    .entry("indexFormat", metadata.indexFormat())
                    .entry("timeCreated", metadata.timeCreated())
                    .entry("timeModified", metadata.timeModified())
                    .endMap().endEntry();

            ds.startEntry("fieldInfo").startMap()
                    .entry("pidField", metadata.metadataFields().pidField() == null ? "" : metadata.metadataFields().pidField())
                    .entry("titleField", metadata.custom().get("titleField", ""))
                    .entry("authorField", metadata.custom().get("authorField", ""))
                    .entry("dateField", metadata.custom().get("dateField", ""))
                    .endMap().endEntry();

            ds.startEntry("annotatedFields").startMap();
            for (ResultAnnotatedField annotatedField: result.getAnnotatedFields()) {
                ds.startAttrEntry("annotatedField", "name", annotatedField.getFieldDesc().name());
                {
                    annotatedField(ds, annotatedField);
                }
                ds.endAttrEntry();
            }
            ds.endMap().endEntry();

            ds.startEntry("metadataFields").startMap();
            for (ResultMetadataField metadataField: result.getMetadataFields()) {
                ds.startAttrEntry("metadataField", "name", metadataField.getFieldDesc().name());
                {
                    metadataField(ds, metadataField);
                }
                ds.endAttrEntry();
            }
            ds.endMap().endEntry();

            metadataGroupInfo(ds, result.getMetadataFieldGroups());

            ds.startEntry("annotationGroups").startMap();
            for (AnnotatedField f: metadata.annotatedFields()) {
                AnnotationGroups groups = metadata.annotatedFields().annotationGroups(f.name());
                if (groups != null) {
                    @SuppressWarnings("FuseStreamOperations") // LinkedHashSet - preserve order!
                    Set<Annotation> annotationsNotInGroups = new LinkedHashSet<>(
                            f.annotations().stream().collect(Collectors.toList()));
                    for (AnnotationGroup group: groups) {
                        for (String annotationName: group) {
                            Annotation annotation = f.annotation(annotationName);
                            annotationsNotInGroups.remove(annotation);
                        }
                    }
                    ds.startAttrEntry("annotatedField", "name", f.name()).startList();
                    boolean addedRemainingAnnots = false;
                    for (AnnotationGroup group: groups) {
                        ds.startItem("annotationGroup").startMap();
                        ds.entry("name", group.groupName());
                        ds.startEntry("annotations").startList();
                        for (String annotation: group) {
                            ds.item("annotation", annotation);
                        }
                        if (!addedRemainingAnnots && group.addRemainingAnnotations()) {
                            addedRemainingAnnots = true;
                            for (Annotation annotation: annotationsNotInGroups) {
                                if (!annotation.isInternal())
                                    ds.item("annotation", annotation.name());
                            }
                        }
                        ds.endList().endEntry();
                        ds.endMap().endItem();
                    }
                    ds.endList().endAttrEntry();
                }
            }
            ds.endMap().endEntry();
        }
        ds.endMap();
    }

    public static void indexStatusResponse(DataStream ds, ResultIndexStatus progress) {
        IndexMetadata metadata = progress.getMetadata();
        ds.startMap();
        {
            ds.entry("indexName", progress.getIndex().getId());
            ds.entry("displayName", metadata.custom().get("displayName", ""));
            ds.entry("description", metadata.custom().get("description", ""));
            ds.entry("status", progress.getIndexStatus());
            if (!StringUtils.isEmpty(metadata.documentFormat()))
                ds.entry("documentFormat", metadata.documentFormat());
            ds.entry("timeModified", metadata.timeModified());
            ds.entry("tokenCount", metadata.tokenCount());
            indexProgress(ds, progress);
        }
        ds.endMap();
    }

    public static void docContentsResponse(ResultDocContents result, DataStream ds) {
        ds.startMap();
        ds.entry("contents", result.getContent());
        ds.endMap();
    }

    public static void docInfoResponse(DataStream ds, ResultDocInfo docInfo, Map<String, List<String>> metadataFieldGroups,
            Map<String, String> docFields, Map<String, String> metaDisplayNames) {
        ds.startMap().entry("docPid", docInfo.getPid());
        {
            ds.startEntry("docInfo");
            {
                documentInfo(ds, docInfo);
            }
            ds.endEntry();

            if (apiVersion == ApiVersion.V3) {
                // (this information is not specific to this document and can be found elsewhere,
                //  so it probably shouldn't be here)
                metadataGroupInfo(ds, metadataFieldGroups);
                metadataFieldInfo(ds, docFields, metaDisplayNames);
            }
        }
        ds.endMap();
    }

    /**
     * Output a hit (or just a document fragment with no hit in it)
     *
     * @param ds output stream
     * @param result hit to output
     */
    public static void hitOrFragmentInfo(DataStream ds, ResultDocSnippet result) {

        Hits hits = result.getHits();
        Hit hit = hits.get(0);
        ContextSize wordsAroundHit = result.getWordsAroundHit();
        boolean useOrigContent = result.isOrigContent();
        boolean isFragment = !result.isHit();
        String docPid = null; // (not sure why this is always null..?) result.getParams().getDocPid();
        List<Annotation> annotationsToList = result.getAnnotsToWrite();

        // TODO: can we merge this with hit()...?
        ds.startMap();
        if (docPid != null) {  // always false, see above? weird!
            // Add basic hit info
            ds.entry("docPid", docPid);
            ds.entry("start", hit.start());
            ds.entry("end", hit.end());
        }

        Hits singleHit = hits.window(hit);
        if (useOrigContent) {
            // We're using original content.
            Concordances concordances = singleHit.concordances(wordsAroundHit, ConcordanceType.CONTENT_STORE);
            Concordance c = concordances.get(hit);
            if (!isFragment) {
                ds.startEntry("left").xmlFragment(c.left()).endEntry()
                        .startEntry("match").xmlFragment(c.match()).endEntry()
                        .startEntry("right").xmlFragment(c.right()).endEntry();
            } else {
                ds.xmlFragment(c.match());
            }
        } else {
            Kwics kwics = singleHit.kwics(wordsAroundHit);
            Kwic c = kwics.get(hit);
            if (!isFragment) {
                ds.startEntry("left").contextList(c.annotations(), annotationsToList, c.left()).endEntry()
                        .startEntry("match").contextList(c.annotations(), annotationsToList, c.match()).endEntry()
                        .startEntry("right").contextList(c.annotations(), annotationsToList, c.right()).endEntry();
            } else {
                ds.startEntry("snippet").contextList(c.annotations(), annotationsToList, c.tokens()).endEntry();
            }
        }
        ds.endMap();
    }

    public static void termFreqResponse(DataStream ds, TermFrequencyList tfl) {
        // Assemble all the parts
        ds.startMap();
        ds.startEntry("termFreq").startMap();
        //DataObjectMapAttribute termFreq = new DataObjectMapAttribute("term", "text");
        for (TermFrequency tf : tfl) {
            ds.attrEntry("term", "text", tf.term, tf.frequency);
        }
        ds.endMap().endEntry();
        ds.endMap();
    }

    public static void autoComplete(DataStream ds, ResultAutocomplete result) {
        ds.startList();
        result.getTerms().forEach((v) -> ds.item("term", v));
        ds.endList();
    }
}
