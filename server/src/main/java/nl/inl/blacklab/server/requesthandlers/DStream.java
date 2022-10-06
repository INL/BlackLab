package nl.inl.blacklab.server.requesthandlers;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;

import nl.inl.blacklab.search.Concordance;
import nl.inl.blacklab.search.Kwic;
import nl.inl.blacklab.search.Span;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.AnnotationSensitivity;
import nl.inl.blacklab.search.indexmetadata.Annotations;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.indexmetadata.ValueListComplete;
import nl.inl.blacklab.search.results.ContextSize;
import nl.inl.blacklab.search.results.CorpusSize;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.Hit;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.ResultGroups;
import nl.inl.blacklab.search.results.ResultsStats;
import nl.inl.blacklab.search.results.ResultsStatsStatic;
import nl.inl.blacklab.search.results.SampleParameters;
import nl.inl.blacklab.search.results.WindowStats;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.index.Index;
import nl.inl.blacklab.server.lib.ConcordanceContext;
import nl.inl.blacklab.server.lib.SearchCreator;
import nl.inl.blacklab.server.lib.SearchTimings;
import nl.inl.blacklab.server.lib.WebserviceParams;
import nl.inl.blacklab.server.lib.results.ResultAnnotatedField;
import nl.inl.blacklab.server.lib.results.ResultAnnotationInfo;
import nl.inl.blacklab.server.lib.results.ResultDocInfo;
import nl.inl.blacklab.server.lib.results.ResultIndexProgress;
import nl.inl.blacklab.server.lib.results.ResultListOfHits;
import nl.inl.blacklab.server.lib.results.ResultMetadataField;
import nl.inl.blacklab.server.lib.results.ResultSummaryNumDocs;
import nl.inl.blacklab.server.lib.results.ResultSummaryNumHits;
import nl.inl.blacklab.server.lib.results.ResultSummaryCommonFields;
import nl.inl.blacklab.server.lib.results.ResultUserInfo;

/**
 * Utilities for serializing BlackLab responses using DataStream.
 */
public class DStream {

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

        SearchCreator searchParam = summaryFields.getSearchParam();
        Index.IndexStatus indexStatus = summaryFields.getIndexStatus();
        SearchTimings timings = summaryFields.getTimings();
        ResultGroups<?> groups = summaryFields.getGroups();
        WindowStats window = summaryFields.getWindow();

        // Our search parameters
        ds.startEntry("searchParam");
        ds.startMap();
        for (Map.Entry<String, String> e : ((WebserviceParams) searchParam).getParameters().entrySet()) {
            ds.entry(e.getKey(), e.getValue());
        }
        ds.endMap();
        ds.endEntry();

        if (indexStatus != Index.IndexStatus.AVAILABLE) {
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
        SearchCreator params = result.getParams();
        Hits hits = result.getHits();

        ds.startEntry("hits").startList();
        for (Hit hit : hits) {
            ds.startItem("hit");
            {
                String docPid = result.getDocIdToPid().get(hit.doc());
                Map<String, Span> capturedGroups = null;
                if (hits.hasCapturedGroups()) {
                    capturedGroups = hits.capturedGroups().getMap(hit, params.omitEmptyCapture());
                    if (capturedGroups == null)
                        RequestHandler.logger.warn(
                                "MISSING CAPTURE GROUP: " + docPid + ", query: " + params.getPattern());
                }

                hit(ds, params, result.getConcordanceContext(), result.getAnnotationsToWrite(), hit, docPid,
                        capturedGroups);
            }
            ds.endItem();
        }
        ds.endList().endEntry();
    }

    private static void hit(DataStream ds, SearchCreator params, ConcordanceContext concordanceContext,
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

    static void indexProgress(DataStream ds, ResultIndexProgress progress)
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
                .entry("uiType", fd.uiType());
        ds
                .entry("type", fd.type().toString())
                .entry("analyzer", fd.analyzerName())
                .entry("unknownCondition", fd.unknownCondition().toString())
                .entry("unknownValue", fd.unknownValue());
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
}
