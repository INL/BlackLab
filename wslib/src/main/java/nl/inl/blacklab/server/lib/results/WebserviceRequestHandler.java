package nl.inl.blacklab.server.lib.results;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.servlet.http.HttpServletResponse;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.IndexMetadata;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.searches.SearchCache;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.lib.Response;
import nl.inl.blacklab.server.lib.ResultIndexMetadata;
import nl.inl.blacklab.server.lib.WebserviceParams;
import nl.inl.blacklab.server.lib.WriteCsv;

/**
 * Handle all the different webservice requests, given the requested operation,
 * parameters and output stream.
 * <p>
 * This is used for both the BLS and Solr webservices.
 */
public class WebserviceRequestHandler {

    /**
     * Show information about a field in a corpus.
     *
     * @param params parameters
     * @param ds output stream
     */
    public static void opFieldInfo(WebserviceParams params, DStream ds) {
        BlackLabIndex index = params.blIndex();
        IndexMetadata indexMetadata = index.metadata();
        String fieldName = params.getFieldName();
        if (indexMetadata.annotatedFields().exists(fieldName)) {
            // Annotated field
            AnnotatedField fieldDesc = indexMetadata.annotatedField(fieldName);
            ResultAnnotatedField resultAnnotatedField = WebserviceOperations.annotatedField(params, fieldDesc, true);
            ds.annotatedField(resultAnnotatedField);
        } else {
            // Metadata field
            MetadataField fieldDesc = indexMetadata.metadataField(fieldName);
            ResultMetadataField metadataField = WebserviceOperations.metadataField(fieldDesc, params.getCorpusName());
            ds.metadataField(metadataField);
        }
    }

    /**
     * Show information about a corpus.
     *
     * @param params parameters
     * @param ds output stream
     */
    public static void opCorpusInfo(WebserviceParams params, DStream ds) {
        ResultIndexMetadata corpusInfo = WebserviceOperations.indexMetadata(params);
        ds.indexMetadataResponse(corpusInfo);
    }

    /**
     * Show (indexing) status of a corpus.
     *
     * @param params parameters
     * @param ds output stream
     */
    public static void opCorpusStatus(WebserviceParams params, DStream ds) {
        ResultIndexStatus corpusStatus = WebserviceOperations.resultIndexStatus(params);
        ds.indexStatusResponse(corpusStatus);
    }

    /**
     * Show server information.
     *
     * @param params parameters
     * @param ds output stream
     */
    public static void opServerInfo(WebserviceParams params, boolean debugMode, DStream ds) {
        ResultServerInfo serverInfo = WebserviceOperations.serverInfo(params, debugMode);
        ds.serverInfo(serverInfo, params.apiCompatibility());
    }

    /**
     * Find or group hits.
     *
     * @param params parameters
     * @param ds output stream
     */
    public static void opHits(WebserviceParams params, DStream ds) throws InvalidQuery {
        if (params.isCalculateCollocations()) {
            // Collocations request
            TermFrequencyList tfl = WebserviceOperations.calculateCollocations(params);
            ds.collocationsResponse(tfl);
        } else {
            // Hits request
            if (shouldReturnListOfGroups(params)) {
                // We're returning a list of groups
                ResultHitsGrouped hitsGrouped = WebserviceOperations.hitsGrouped(params);
                ds.hitsGroupedResponse(hitsGrouped);
            } else {
                // We're returning a list of results (ungrouped, or viewing single group)
                ResultHits result = WebserviceOperations.getResultHits(params);
                ds.hitsResponse(result, params.apiCompatibility() == ApiVersion.V3);
            }
        }
    }

    /**
     * Find or group documents.
     *
     * @param params parameters
     * @param ds output stream
     */
    public static void opDocs(WebserviceParams params, DStream ds) throws InvalidQuery {
        if (shouldReturnListOfGroups(params)) {
            // We're returning a list of groups
            ResultDocsGrouped docsGrouped = WebserviceOperations.docsGrouped(params);
            ds.docsGroupedResponse(docsGrouped);
        } else {
            // We're returning a list of results (ungrouped, or viewing single group)
            ResultDocsResponse result;
            if (params.getGroupProps().isPresent() && params.getViewGroup().isPresent()) {
                // View a single group in a grouped docs resultset
                result = WebserviceOperations.viewGroupDocsResponse(params);
            } else {
                // Regular set of docs (no grouping first)
                result = WebserviceOperations.regularDocsResponse(params);
            }
            ds.docsResponse(result, params.apiCompatibility() == ApiVersion.V3);
        }
    }

    /**
     * Is this a request for a list of groups?
     * <p>
     * If not, it's either a regular request for (hits or docs) results,
     * or a request for viewing the results in a single group.
     *
     * @param params parameters
     * @return true if we should return a list of groups
     */
    private static boolean shouldReturnListOfGroups(WebserviceParams params) {
        Optional<String> viewgroup = params.getViewGroup();
        boolean returnListOfGroups = false;
        if (params.getGroupProps().isPresent()) {
            // This is a grouping operation
            if (viewgroup.isEmpty()) {
                // We want the list of groups, not the contents of a single group
                returnListOfGroups = true;
            }
        } else if (viewgroup.isPresent()) {
            // "viewgroup" parameter without "group" parameter; error.
            throw new BadRequest("ERROR_IN_GROUP_VALUE",
                    "Parameter 'viewgroup' specified, but required 'group' parameter is missing.");
        }
        return returnListOfGroups;
    }

    /**
     * Return the original contents of a document.
     *
     * @param params parameters
     * @param ds output stream
     */
    public static void opDocContents(WebserviceParams params, DStream ds) throws InvalidQuery {
        ResultDocContents result = WebserviceOperations.docContents(params);
        ds.docContentsResponse(result);
    }

    /**
     * Return metadata for a document.
     *
     * @param params parameters
     * @param ds output stream
     */
    public static void opDocInfo(WebserviceParams params, DStream ds) {
        Collection<MetadataField> metadataToWrite = WebserviceOperations.getMetadataToWrite(params);
        BlackLabIndex index = params.blIndex();
        ResultDocInfo docInfo = WebserviceOperations.docInfo(index, params.getDocPid(), null, metadataToWrite);

        Map<String, List<String>> metadataFieldGroups = WebserviceOperations.getMetadataFieldGroupsWithRest(index);
        Map<String, String> docFields = WebserviceOperations.getDocFields(index);
        Map<String, String> metaDisplayNames = WebserviceOperations.getMetaDisplayNames(index);

        // Document info
        ds.docInfoResponse(docInfo, metadataFieldGroups, docFields, metaDisplayNames,
                params.apiCompatibility() == ApiVersion.V3);
    }

    /**
     * Return a snippet from a document.
     *
     * @param params parameters
     * @param ds output stream
     */
    public static void opDocSnippet(WebserviceParams params, DStream ds) {
        ResultDocSnippet result = WebserviceOperations.docSnippet(params);
        ds.hitOrFragmentInfo(result);
    }

    /**
     * Calculate term frequencies.
     *
     * @param params parameters
     * @param ds output stream
     */
    public static void opTermFreq(WebserviceParams params, DStream ds) {
        TermFrequencyList tfl = WebserviceOperations.getTermFrequencies(params);
        ds.termFreqResponse(tfl);
    }


    /**
     * Return autocomplete results for metadata or annotated field.
     *
     * @param params parameters
     * @param ds output stream
     */
    public static void opAutocomplete(WebserviceParams params, DStream ds) {
        ResultAutocomplete result = WebserviceOperations.autocomplete(params);
        ds.autoComplete(result);
    }

    public static void opInputFormatInfo(WebserviceParams params, DStream ds) {
        ResultInputFormat result = WebserviceOperations.inputFormat(params.getInputFormat().get());
        ds.formatInfoResponse(result);
    }

    public static void opListInputFormats(WebserviceParams params, DStream ds) {
        ResultListInputFormats result = WebserviceOperations.listInputFormats(params);
        ds.listFormatsResponse(result);
    }

    public static void opCacheInfo(WebserviceParams params, DStream ds) {
        boolean includeDebugInfo = params.isIncludeDebugInfo();
        SearchCache blackLabCache = params.getSearchManager().getBlackLabCache();
        ds.cacheInfo(blackLabCache, includeDebugInfo);
    }

    public static int opClearCache(WebserviceParams params, DStream ds, boolean debugMode) {
        if (!debugMode) {
            return Response.forbidden(ds);
        } else {
            params.getSearchManager().getBlackLabCache().clear(false);
            return Response.status(ds, "SUCCESS", "Cache cleared succesfully.", HttpServletResponse.SC_OK);
        }
    }

    public static void opDocsCsv(WebserviceParams params, DStream ds) throws InvalidQuery {
        ResultDocsCsv result = WebserviceOperations.docsCsv(params);
        String csv;
        if (result.getGroups() == null || result.isViewGroup()) {
            // No grouping applied, or viewing a single group
            csv = WriteCsv.docs(params, result.getDocs(), result.getGroups(),
                    result.getSubcorpusResults());
        } else {
            // Grouped results
            csv = WriteCsv.docGroups(params, result.getDocs(), result.getGroups(),
                    result.getSubcorpusResults());
        }
        ds.getDataStream().csv(csv);
    }

    public static void opHitsCsv(WebserviceParams params, DStream ds) throws InvalidQuery {
        ResultHitsCsv result = WebserviceOperations.hitsCsv(params);
        String csv;
        if (result.getGroups() != null && !result.isViewGroup()) {
            csv = WriteCsv.hitsGroupsResponse(result);
        } else {
            csv = WriteCsv.hitsResponse(result);
        }
        ds.getDataStream().csv(csv);
    }

    public static void opInputFormatXslt(WebserviceParams params, DStream ds) {
        ResultInputFormat result = WebserviceOperations.inputFormat(params.getInputFormat().get());
        ds.formatXsltResponse(result);
    }
}
