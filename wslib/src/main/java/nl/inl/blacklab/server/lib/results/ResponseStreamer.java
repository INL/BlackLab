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
import nl.inl.blacklab.index.DocIndexerFactory;
import nl.inl.blacklab.index.annotated.AnnotationSensitivities;
import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.Concordance;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.Kwic;
import nl.inl.blacklab.search.QueryExplanation;
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
import nl.inl.blacklab.search.indexmetadata.MetadataFields;
import nl.inl.blacklab.search.indexmetadata.RelationUtil;
import nl.inl.blacklab.search.indexmetadata.ValueListComplete;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.lucene.RelationInfo;
import nl.inl.blacklab.search.lucene.RelationListInfo;
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
import nl.inl.blacklab.searches.SearchCache;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.index.Index;
import nl.inl.blacklab.server.lib.ConcordanceContext;
import nl.inl.blacklab.server.lib.ResultIndexMetadata;
import nl.inl.blacklab.server.lib.SearchTimings;
import nl.inl.blacklab.server.lib.WebserviceParams;
import nl.inl.blacklab.webservice.WebserviceParameter;

/**
 * For serializing BlackLab response objects.
 *
 * Takes a DataStream and an API version to attempt compatibility with.
 */
public class ResponseStreamer {
    /** Root element to use for XML responses. */
    public static final String BLACKLAB_RESPONSE_ROOT_ELEMENT = "blacklabResponse";
    static final Logger logger = LogManager.getLogger(ResponseStreamer.class);

    public static final String KEY_BLACKLAB_BUILD_TIME = "blacklabBuildTime";

    private static final String KEY_BLACKLAB_VERSION = "blacklabVersion";

    private static final String KEY_API_VERSION = "apiVersion";

    public static ResponseStreamer get(DataStream ds, ApiVersion v) {
        return new ResponseStreamer(ds, v);
    }

    /** What version of responses to write. */
    private ApiVersion apiVersion;

    /** DataStream to write to. */
    private DataStream ds;

    private ResponseStreamer(DataStream ds, ApiVersion v) {
        this.ds = ds;
        this.apiVersion = v;
    }

    /**
     * Add info about the current logged-in user (if any) to the response.
     *
     * @param userInfo user info to show
     */
    public void userInfo(ResultUserInfo userInfo) {
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
     * @param docFields document field info to write
     * @param metaDisplayNames display name info to write
     */
    public void metadataFieldInfo(Map<String, String> docFields, Map<String, String> metaDisplayNames) {
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
     * @param docInfos infos to write
     */
    public void documentInfos(Map<String, ResultDocInfo> docInfos) {
        ds.startEntry("docInfos").startMap();
        for (Map.Entry<String, ResultDocInfo> e: docInfos.entrySet()) {
            ds.startAttrEntry("docInfo", "pid", e.getKey());
            {
                documentInfo(e.getValue());
            }
            ds.endAttrEntry();
        }
        ds.endMap().endEntry();
    }

    /**
     * Stream document information (metadata, contents authorization)
     *
     * @param docInfo info to stream
     */
    public void documentInfo(ResultDocInfo docInfo) {
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

    public void metadataGroupInfo(Map<String, List<String>> metadataFieldGroups) {
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

    public void facets(Map<String, List<Pair<String, Long>>> facetInfo) {
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
     * @param summaryFields info for the fields to write
     */
    public void summaryCommonFields(ResultSummaryCommonFields summaryFields) throws BlsException {
        WebserviceParams params = summaryFields.getSearchParam();
        Index.IndexStatus indexStatus = summaryFields.getIndexStatus();
        SearchTimings timings = summaryFields.getTimings();
        ResultGroups<?> groups = summaryFields.getGroups();
        WindowStats window = summaryFields.getWindow();

        // Our search parameters
        ds.startEntry("searchParam").startMap();
        for (Map.Entry<WebserviceParameter, String> e: params.getParameters().entrySet()) {
            ds.entry(e.getKey().value(), e.getValue());
        }
        ds.endMap().endEntry();

        if (indexStatus != null && indexStatus != Index.IndexStatus.AVAILABLE) {
            ds.entry("indexStatus", indexStatus.toString());
        }

        // Information about hit sampling
        SampleParameters sample = params.sampleSettings();
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

    public void summaryNumHits(ResultSummaryNumHits result) {
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
            subcorpusSize(subcorpusSize);
        }
    }

    public void summaryNumDocs(ResultSummaryNumDocs result) {
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
            subcorpusSize(subcorpusSize);
        }
    }

    public void subcorpusSize(CorpusSize subcorpusSize) {
        ds.startEntry("subcorpusSize").startMap()
                .entry("documents", subcorpusSize.getDocuments());
        if (subcorpusSize.hasTokenCount())
            ds.entry("tokens", subcorpusSize.getTokens());
        ds.endMap().endEntry();
    }

    public void listOfHits(ResultListOfHits result) throws BlsException {
        nl.inl.blacklab.server.lib.WebserviceParams params = result.getParams();
        Hits hits = result.getHits();

        ds.startEntry("hits").startList();
        for (Hit hit : hits) {
            ds.startItem("hit");
            {
                String docPid = result.getDocIdToPid().get(hit.doc());
                Map<String, MatchInfo> capturedGroups = null;
                if (hits.hasMatchInfo()) {
                    capturedGroups = hits.getMatchInfoMap(hit, params.getOmitEmptyCaptures());
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

    private static void hit(DataStream ds, nl.inl.blacklab.server.lib.WebserviceParams params, ConcordanceContext concordanceContext,
            Collection<Annotation> annotationsToList, Hit hit, String docPid, Map<String, MatchInfo> matchInfo) {
        ds.startMap();
        if (docPid != null) {
            // Add basic hit info
            ds.entry("docPid", docPid);
            ds.entry("start", hit.start());
            ds.entry("end", hit.end());
        }

        // If any groups were captured, include them in the response
        // (legacy, replaced by matchInfos)
        Set<Map.Entry<String, MatchInfo>> capturedGroups = filterMatchInfo(matchInfo, MatchInfo.Type.SPAN);
        if (!capturedGroups.isEmpty()) {
            ds.startEntry("captureGroups").startList();
            for (Map.Entry<String, MatchInfo> capturedGroup: capturedGroups) {
                ds.startItem("group");
                legacyCapturedGroup(ds, capturedGroup);
                ds.endItem();
            }
            ds.endList().endEntry();
        }

        // Captured groups, (list of) relations, inline tags as a map
        String returnMatchInfo = params.getReturnMatchInfo();
        if (returnMatchInfo.isEmpty() || returnMatchInfo.equalsIgnoreCase("all")) {
            // If there's any match info, include it here
            if (matchInfo != null && !matchInfo.isEmpty()) {
                ds.startEntry("matchInfos").startMap();
                for (Map.Entry<String, MatchInfo> e: matchInfo.entrySet()) {
                    ds.startElEntry(e.getKey());
                    matchInfo(ds, e.getValue());
                    ds.endElEntry();
                }
                ds.endMap().endEntry();
            }
        }

        ContextSize contextSize = params.contextSettings().size();
        boolean includeContext = contextSize.inlineTagName() != null || contextSize.before() > 0 || contextSize.after() > 0;
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

    private static void legacyCapturedGroup(DataStream ds, Map.Entry<String, MatchInfo> capturedGroup) {
        ds.startMap();
        {
            ds.entry("name", capturedGroup.getKey());
            ds.entry("start", capturedGroup.getValue().getSpanStart());
            ds.entry("end", capturedGroup.getValue().getSpanEnd());
        }
        ds.endMap();
    }

    private static void matchInfo(DataStream ds, MatchInfo matchInfo) {
        switch (matchInfo.getType()) {
        case INLINE_TAG:
            matchInfoInlineTag(ds, (RelationInfo) matchInfo);
            break;
        case RELATION:
            matchInfoRelation(ds, (RelationInfo) matchInfo);
            break;
        case LIST_OF_RELATIONS:
            matchInfoListOfRelations(ds, (RelationListInfo) matchInfo);
            break;
        default:
            matchInfoCapturedGroup(ds, matchInfo);
            break;
        }
    }

    private static void matchInfoListOfRelations(DataStream ds, RelationListInfo listOfRelations) {
        ds.startMap();
        {
            ds.entry("type", "list");
            ds.entry("start", listOfRelations.getSpanStart());
            ds.entry("end", listOfRelations.getSpanEnd());
            ds.startEntry("infos").startList();
            {
                for (RelationInfo relationInfo: listOfRelations.getRelations()) {
                    ds.startItem("info");
                    matchInfo(ds, relationInfo);
                    ds.endItem();
                }
            }
            ds.endList().endEntry();
        }
        ds.endMap();
    }

    private static void matchInfoCapturedGroup(DataStream ds, MatchInfo capturedGroup) {
        ds.startMap();
        {
            ds.entry("type", "span");
            ds.entry("start", capturedGroup.getSpanStart());
            ds.entry("end", capturedGroup.getSpanEnd());
        }
        ds.endMap();
    }

    private static void matchInfoInlineTag(DataStream ds, RelationInfo inlineTag) {
        ds.startMap();
        {
            String fullRelationType = inlineTag.getFullRelationType();
            String tagName = RelationUtil.classAndType(fullRelationType)[1];
            ds.entry("type", "tag");
            ds.entry("tagName", tagName);
            if (!inlineTag.getAttributes().isEmpty()) {
                ds.startEntry("attributes").startMap();
                for (Map.Entry<String, String> attr: inlineTag.getAttributes().entrySet()) {
                    ds.elEntry(attr.getKey(), attr.getValue());
                }
            }
            ds.endMap().endEntry();
            ds.entry("start", inlineTag.getSourceStart());
            ds.entry("end", inlineTag.getTargetStart());
        }
        ds.endMap();
    }

    private static void matchInfoRelation(DataStream ds, RelationInfo relationInfo) {
        ds.startMap();
        {
            ds.entry("type", "relation");
            ds.entry("relType", relationInfo.getFullRelationType());
            if (!relationInfo.getAttributes().isEmpty()) {
                ds.startEntry("attributes").startMap();
                for (Map.Entry<String, String> attr: relationInfo.getAttributes().entrySet()) {
                    ds.elEntry(attr.getKey(), attr.getValue());
                }
            }
            if (!relationInfo.isRoot()) {
                ds.entry("sourceStart", relationInfo.getSourceStart());
                ds.entry("sourceEnd", relationInfo.getSourceEnd());
            }
            ds.entry("targetStart", relationInfo.getTargetStart());
            ds.entry("targetEnd", relationInfo.getTargetEnd());
            ds.entry("start", relationInfo.getSpanStart());
            ds.entry("end", relationInfo.getSpanEnd());
        }
        ds.endMap();
    }

    private static Set<Map.Entry<String, MatchInfo>> filterMatchInfo(Map<String, MatchInfo> matchInfo, MatchInfo.Type type) {
        Set<Map.Entry<String, MatchInfo>> capturedGroups = matchInfo == null ? Collections.emptySet() :
                matchInfo.entrySet().stream()
                        .filter(e -> e.getValue() != null && e.getValue().getType() == type)
                        .collect(Collectors.toSet());
        return capturedGroups;
    }

    public void indexProgress(ResultIndexStatus progress)
            throws BlsException {
        if (progress.getIndexStatus().equals(Index.IndexStatus.INDEXING)) {
            ds.startEntry("indexProgress").startMap()
                    .entry("filesProcessed", progress.getFiles())
                    .entry("docsDone", progress.getDocs())
                    .entry("tokensProcessed", progress.getTokens());
            ds.endMap().endEntry();
        }
    }

    public void metadataField(ResultMetadataField metadataField) {
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
                .entry("analyzer", fd.analyzerName());
        Object unknownCondition = fd.custom().get("unknownCondition");
        if (unknownCondition != null)
            ds.entry("unknownCondition", unknownCondition.toString().toUpperCase());
        ds.entry("unknownValue", fd.custom().get("unknownValue"));
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

    public void annotatedField(ResultAnnotatedField annotatedField) {
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
        boolean internalAnnotsInDisplayOrder = apiVersion == ApiVersion.V3;
        annotations.stream()
                .filter(a -> internalAnnotsInDisplayOrder || !a.isInternal())
                .map(Annotation::name)
                .forEach(id -> ds.item("fieldName", id));
        ds.endList().endEntry();

        ds.startEntry("annotations").startMap();
        for (Map.Entry<String, ResultAnnotationInfo> e: annotInfos.entrySet()) {
            ds.startAttrEntry("annotation", "name", e.getKey()).startMap();
            ResultAnnotationInfo ai = e.getValue();
            Annotation annotation = ai.getAnnotation();
            AnnotationSensitivity offsetsSensitivity = annotation.offsetsSensitivity();
            String offsetsAlternative = offsetsSensitivity == null ? "" :
                    offsetsSensitivity.sensitivity().luceneFieldSuffix();
            AnnotationSensitivities annotationSensitivities = annotation.sensitivitySetting();
            String sensitivity = annotationSensitivities == null ? "" : annotationSensitivities.stringValueForResponse();
            ds
                    .entry("displayName", annotation.displayName())
                    .entry("description", annotation.description())
                    .entry("uiType", annotation.uiType())
                    .entry("hasForwardIndex", annotation.hasForwardIndex())
                    .entry("sensitivity", sensitivity)
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

    public void collocationsResponse(TermFrequencyList tfl) {
        ds.startMap().startEntry("tokenFrequencies").startMap();
        for (TermFrequency tf : tfl) {
            ds.attrEntry("token", "text", tf.term, tf.frequency);
        }
        ds.endMap().endEntry().endMap();
    }

    public void hitsResponse(ResultHits resultHits, boolean includeDeprecatedFieldInfo)
            throws InvalidQuery {
        nl.inl.blacklab.server.lib.WebserviceParams params = resultHits.getParams();
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
            summaryCommonFields(summaryFields);
            summaryNumHits(result);
            if (params.getIncludeTokenCount())
                ds.entry("tokensInMatchingDocuments", resultHits.getTotalTokens());

            // Write docField (pidField, titleField, etc.) and metadata display names
            // (these arguably shouldn't be included with every hits response; can be read once from the index
            //  metadata response)
            if (apiVersion == ApiVersion.V3 || includeDeprecatedFieldInfo) {
                // (this information is not specific to this request and can be found elsewhere,
                //  so it probably shouldn't be here)
                metadataFieldInfo(resultHits.getDocFields(), resultHits.getMetaDisplayNames());
            }

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
        listOfHits(listOfHits);
        documentInfos(resultHits.getDocInfos());

        // Facets (if requested)
        if (resultHits.hasFacets()) {
            // Now, group the docs according to the requested facets.
            ds.startEntry("facets");
            {
                facets(resultHits.getFacetInfo());
            }
            ds.endEntry();
        }

        ds.endMap();
    }

    public void hitsGroupedResponse(ResultHitsGrouped hitsGrouped) {
        nl.inl.blacklab.server.lib.WebserviceParams params = hitsGrouped.getParams();
        ResultSummaryCommonFields summaryFields = hitsGrouped.getSummaryFields();
        ResultSummaryNumHits result = hitsGrouped.getSummaryNumHits();

        ds.startMap();

        // Summary
        ds.startEntry("summary").startMap();
        {
            summaryCommonFields(summaryFields);
            summaryNumHits(result);
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
                    subcorpusSize(groupInfo.getSubcorpusSize());
                }

                if (groupInfo.getListOfHits() != null) {
                    listOfHits(groupInfo.getListOfHits());
                }
            }
            ds.endMap().endItem();
        }
        ds.endList().endEntry();

        if (params.getIncludeGroupContents()) {
            documentInfos(hitsGrouped.getDocInfos());
        }
        ds.endMap();
    }

    public void docsGroupedResponse(ResultDocsGrouped result) {
        DocGroups groups = result.getGroups();
        WindowStats ourWindow = result.getOurWindow();

        ds.startMap();

        // The summary
        ds.startEntry("summary").startMap();

        summaryCommonFields(result.getSummaryFields());

        if (result.getNumResultDocs() != null) {
            summaryNumDocs(result.getNumResultDocs());
        } else {
            summaryNumHits(result.getNumResultHits());
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
                subcorpusSize(it.next());
            }
            ds.endMap().endItem();
        }
        ds.endList().endEntry();

        ds.endMap();
    }

    public void docsResponse(ResultDocsResponse result, boolean includeDeprecatedFieldInfo) {
        ds.startMap();
        {
            // The summary
            ds.startEntry("summary").startMap();
            {
                summaryCommonFields(result.getSummaryFields());
                if (result.getNumResultDocs() != null) {
                    summaryNumDocs(result.getNumResultDocs());
                } else {
                    summaryNumHits(result.getNumResultHits());
                }
                if (result.isIncludeTokenCount())
                    ds.entry("tokensInMatchingDocuments", result.getTotalTokens());

                if (apiVersion == ApiVersion.V3 || includeDeprecatedFieldInfo) {
                    // (this information is not specific to this request and can be found elsewhere,
                    //  so it probably shouldn't be here)
                    metadataFieldInfo(result.getDocFields(), result.getMetaDisplayNames());
                }
            }
            ds.endMap().endEntry();

            // The hits and document info
            ds.startEntry("docs").startList();
            for (ResultDocResult docResult: result.getDocResults()) {
                docResult(docResult);
            }
            ds.endList().endEntry();
            if (result.getFacetInfo() != null) {
                // Now, group the docs according to the requested facets.
                ds.startEntry("facets");
                {
                    facets(result.getFacetInfo());
                }
                ds.endEntry();
            }
        }
        ds.endMap();
    }

    public void docResult(ResultDocResult result) {
        ds.startItem("doc").startMap();
        {
            // Combine all
            ds.entry("docPid", result.getPid());
            if (result.numberOfHits() > 0)
                ds.entry("numberOfHits", result.numberOfHits());

            // Doc info (metadata, etc.)
            ds.startEntry("docInfo");
            {
                documentInfo(result.getDocInfo());
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

    public void serverInfo(ResultServerInfo result, ApiVersion apiCompatibility) {
        ds.startMap();
        if (apiVersion != ApiVersion.V3)
            ds.entry(KEY_API_VERSION, apiCompatibility.versionString());
        ds.entry(KEY_BLACKLAB_BUILD_TIME, BlackLab.buildTime())
                .entry(KEY_BLACKLAB_VERSION, BlackLab.version());

        ds.startEntry("indices").startMap();
        for (ResultIndexStatus indexStatus: result.getIndexStatuses()) {
            indexInfo(indexStatus);
        }
        ds.endMap().endEntry();

        userInfo(result.getUserInfo());

        if (result.isDebugMode()) {
            ds.startEntry("cacheStatus");
            ds.value(result.getParams().getSearchManager().getBlackLabCache().getStatus());
            ds.endEntry();
        }
        ds.endMap();
    }

    public void indexInfo(ResultIndexStatus progress) {
        Index index = progress.getIndex();
        IndexMetadata indexMetadata = progress.getMetadata();
        ds.startAttrEntry("index", "name", index.getId());
        {
            ds.startMap();
            {
                ds.entry("displayName", indexMetadata.custom().get("displayName", ""));
                ds.entry("description", indexMetadata.custom().get("description", ""));
                ds.entry("status", index.getStatus());
                String formatIdentifier = indexMetadata.documentFormat();
                if (formatIdentifier != null && formatIdentifier.length() > 0)
                    ds.entry("documentFormat", formatIdentifier);
                ds.entry("timeModified", indexMetadata.timeModified());
                ds.entry("tokenCount", indexMetadata.tokenCount());
                indexProgress(progress);
            }
            ds.endMap();
        }
        ds.endAttrEntry();
    }

    public void indexMetadataResponse(ResultIndexMetadata result) {
        IndexMetadata metadata = result.getMetadata();
        ds.startMap();
        {
            ds.entry("indexName", result.getProgress().getIndex().getId());

            ds.entry("displayName", metadata.custom().get("displayName", ""));
            ds.entry("description", metadata.custom().get("description", ""));
            ds.entry("status", result.getProgress().getIndexStatus());
            ds.entry("contentViewable", metadata.contentViewable());
            ds.entry("textDirection", metadata.custom().get("textDirection", "ltr"));

            String formatIdentifier = metadata.documentFormat();
            if (formatIdentifier != null && formatIdentifier.length() > 0)
                ds.entry("documentFormat", formatIdentifier);
            ds.entry("tokenCount", metadata.tokenCount());
            ds.entry("documentCount", metadata.documentCount());
            indexProgress(result.getProgress());

            ds.startEntry("versionInfo").startMap()
                    .entry(apiVersion == ApiVersion.V3 ? "blackLabBuildTime" : KEY_BLACKLAB_BUILD_TIME, metadata.indexBlackLabBuildTime())
                    .entry(apiVersion == ApiVersion.V3 ? "blackLabVersion" : KEY_BLACKLAB_VERSION, metadata.indexBlackLabVersion())
                    .entry("indexFormat", metadata.indexFormat())
                    .entry("timeCreated", metadata.timeCreated())
                    .entry("timeModified", metadata.timeModified())
                    .endMap().endEntry();

            ds.startEntry("fieldInfo").startMap()
                    .entry(MetadataFields.SPECIAL_FIELD_SETTING_PID, metadata.metadataFields().pidField() == null ? "" : metadata.metadataFields().pidField())
                    .entry("titleField", metadata.custom().get("titleField", ""))
                    .entry("authorField", metadata.custom().get("authorField", ""))
                    .entry("dateField", metadata.custom().get("dateField", ""))
                    .endMap().endEntry();

            ds.startEntry("annotatedFields").startMap();
            for (ResultAnnotatedField annotatedField: result.getAnnotatedFields()) {
                ds.startAttrEntry("annotatedField", "name", annotatedField.getFieldDesc().name());
                {
                    annotatedField(annotatedField);
                }
                ds.endAttrEntry();
            }
            ds.endMap().endEntry();

            ds.startEntry("metadataFields").startMap();
            for (ResultMetadataField metadataField: result.getMetadataFields()) {
                ds.startAttrEntry("metadataField", "name", metadataField.getFieldDesc().name());
                {
                    metadataField(metadataField);
                }
                ds.endAttrEntry();
            }
            ds.endMap().endEntry();

            metadataGroupInfo(result.getMetadataFieldGroups());

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

    public void indexStatusResponse(ResultIndexStatus progress) {
        IndexMetadata metadata = progress.getMetadata();
        ds.startMap();
        {
            ds.entry("indexName", progress.getIndex().getId());
            ds.entry("displayName", metadata.custom().get("displayName", ""));
            ds.entry("description", metadata.custom().get("description", ""));
            ds.entry("status", progress.getIndexStatus());
            ds.entry("timeModified", metadata.timeModified());
            ds.entry("tokenCount", metadata.tokenCount());
            String formatIdentifier = metadata.documentFormat();
            if (!StringUtils.isEmpty(formatIdentifier))
                ds.entry("documentFormat", formatIdentifier);
            indexProgress(progress);
        }
        ds.endMap();
    }

    public String getDocContentsResponsePlain(ResultDocContents resultDocContents) {
        StringBuilder b = new StringBuilder();

        if (resultDocContents.needsXmlDeclaration()) {
            // We haven't outputted an XML declaration yet, and there's none in the document. Do so now.
            b.append(DataStream.XML_PROLOG);
        }

        // Output root element and namespaces if necessary
        // (i.e. when we're not returning the full document, only part of it)
        if (!resultDocContents.isFullDocument()) {
            // Surround with root element and make sure it has the required namespaces
            b.append(DataStream.XML_PROLOG);
            b.append("<" + BLACKLAB_RESPONSE_ROOT_ELEMENT);
            for (String ns: resultDocContents.getNamespaces()) {
                b.append(" ").append(ns);
            }
            for (String anon: resultDocContents.getAnonNamespaces()) {
                b.append(" ").append(anon);
            }
            b.append(">");
        }

        // Output (part of) the document
        b.append(resultDocContents.getContent());

        if (!resultDocContents.isFullDocument()) {
            // Close the root el we opened
            b.append("</" + BLACKLAB_RESPONSE_ROOT_ELEMENT + ">");
        }

        return b.toString();
    }

    public void docContentsResponseAsCdata(ResultDocContents result) {
        ds.startMap();
        ds.entry("contents", getDocContentsResponsePlain(result));
        ds.endMap();
    }

    public void docContentsResponsePlain(ResultDocContents resultDocContents) {
        ds.plain(getDocContentsResponsePlain(resultDocContents));
    }

    public void docInfoResponse(ResultDocInfo docInfo, Map<String, List<String>> metadataFieldGroups,
            Map<String, String> docFields, Map<String, String> metaDisplayNames, boolean includeDeprecatedFieldInfo) {
        ds.startMap().entry("docPid", docInfo.getPid());
        {
            ds.startEntry("docInfo");
            {
                documentInfo(docInfo);
            }
            ds.endEntry();

            if (apiVersion == ApiVersion.V3 || includeDeprecatedFieldInfo) {
                // (this information is not specific to this document and can be found elsewhere,
                //  so it probably shouldn't be here)
                metadataGroupInfo(metadataFieldGroups);
                metadataFieldInfo(docFields, metaDisplayNames);
            }
        }
        ds.endMap();
    }

    /**
     * Output a hit (or just a document fragment with no hit in it)
     *
     * @param result hit to output
     */
    public void hitOrFragmentInfo(ResultDocSnippet result) {

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

    public void termFreqResponse(TermFrequencyList tfl) {
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

    public void autoComplete(ResultAutocomplete result) {
        ds.startList();
        result.getTerms().forEach((v) -> ds.item("term", v));
        ds.endList();
    }

    public void listFormatsResponse(ResultListInputFormats result) {

        ds.startMap();
        {
            userInfo(result.getUserInfo());

            // List supported input formats
            // Formats from other users are hidden in the master list, but are considered public for all other purposes (if you know the name)
            ds.startEntry("supportedInputFormats").startMap();
            for (DocIndexerFactory.Format format: result.getFormats()) {
                ds.startAttrEntry("format", "name", format.getId());
                {
                    ds.startMap();
                    {
                        ds.entry("displayName", format.getDisplayName())
                                .entry("description", format.getDescription())
                                .entry("helpUrl", format.getHelpUrl())
                                .entry("configurationBased", format.isConfigurationBased())
                                .entry("isVisible", format.isVisible());
                    }
                    ds.endMap();
                }
                ds.endAttrEntry();
            }
            ds.endMap().endEntry();
        }
        ds.endMap();
    }

    public void formatInfoResponse(ResultInputFormat result) {
        ds.startMap()
                .entry("formatName", result.getConfig().getName())
                .entry("configFileType", result.getConfig().getConfigFileType())
                .entry("configFile", result.getFileContents())
                .endMap();
    }

    public void cacheInfo(SearchCache blackLabCache, boolean includeDebugInfo) {
        ds.startMap()
                .startEntry("cacheStatus");
        ds.value(blackLabCache.getStatus());
        ds.endEntry()
            .startEntry("cacheContents");
        ds.value(blackLabCache.getContents(includeDebugInfo));
        ds.endEntry()
                .endMap();
    }

    public void formatXsltResponse(ResultInputFormat result) {
        ds.xslt(result.getXslt());
    }

    public DataStream getDataStream() {
        return ds;
    }
}
