package nl.inl.blacklab.server.lib.results;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.IndexMetadata;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.lib.ResultIndexMetadata;
import nl.inl.blacklab.server.lib.WebserviceParams;

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
    public static void opFieldInfo(WebserviceParams params, DataStream ds) {
        BlackLabIndex index = params.blIndex();
        IndexMetadata indexMetadata = index.metadata();
        String fieldName = params.getFieldName();
        if (indexMetadata.annotatedFields().exists(fieldName)) {
            // Annotated field
            AnnotatedField fieldDesc = indexMetadata.annotatedField(fieldName);
            ResultAnnotatedField resultAnnotatedField = WebserviceOperations.annotatedField(params, fieldDesc, true);
            DStream.annotatedField(ds, resultAnnotatedField);
        } else {
            // Metadata field
            MetadataField fieldDesc = indexMetadata.metadataField(fieldName);
            ResultMetadataField metadataField = WebserviceOperations.metadataField(fieldDesc, params.getCorpusName());
            DStream.metadataField(ds, metadataField);
        }
    }

    /**
     * Show information about a corpus.
     *
     * @param params parameters
     * @param ds output stream
     */
    public static void opCorpusInfo(WebserviceParams params, DataStream ds) {
        ResultIndexMetadata corpusInfo = WebserviceOperations.indexMetadata(params);
        DStream.indexMetadataResponse(ds, corpusInfo);
    }

    /**
     * Show (indexing) status of a corpus.
     *
     * @param params parameters
     * @param ds output stream
     */
    public static void opCorpusStatus(WebserviceParams params, DataStream ds) {
        ResultIndexStatus corpusStatus = WebserviceOperations.resultIndexStatus(params);
        DStream.indexStatusResponse(ds, corpusStatus);
    }
    /**
     * Show server information.
     *
     * @param params parameters
     * @param ds output stream
     */

    public static void opServerInfo(WebserviceParams params, boolean debugMode, DataStream ds) {
        ResultServerInfo serverInfo = WebserviceOperations.serverInfo(params, debugMode);
        DStream.serverInfo(ds, serverInfo);
    }

    /**
     * Find or group hits.
     *
     * @param params parameters
     * @param ds output stream
     */
    public static void opHits(WebserviceParams params, DataStream ds) throws InvalidQuery {
        if (params.isCalculateCollocations()) {
            // Collocations request
            TermFrequencyList tfl = WebserviceOperations.calculateCollocations(params);
            DStream.collocationsResponse(ds, tfl);
        } else {
            // Hits request
            if (shouldReturnListOfGroups(params)) {
                // We're returning a list of groups
                ResultHitsGrouped hitsGrouped = WebserviceOperations.hitsGrouped(params);
                DStream.hitsGroupedResponse(ds, hitsGrouped);
            } else {
                // We're returning a list of results (ungrouped, or viewing single group)
                ResultHits result = WebserviceOperations.getResultHits(params);
                DStream.hitsResponse(ds, result);
            }
        }
    }

    /**
     * Find or group documents.
     *
     * @param params parameters
     * @param ds output stream
     */
    public static void opDocs(WebserviceParams params, DataStream ds) throws InvalidQuery {
        if (shouldReturnListOfGroups(params)) {
            // We're returning a list of groups
            ResultDocsGrouped docsGrouped = WebserviceOperations.docsGrouped(params);
            DStream.docsGroupedResponse(ds, docsGrouped);
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
            DStream.docsResponse(ds, result);
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
    public static void opDocContents(WebserviceParams params, DataStream ds) throws InvalidQuery {
        ResultDocContents result = WebserviceOperations.docContents(params);
        DStream.docContentsResponse(result, ds);
    }

    /**
     * Return metadata for a document.
     *
     * @param params parameters
     * @param ds output stream
     */
    public static void opDocInfo(WebserviceParams params, DataStream ds) {
        Collection<MetadataField> metadataToWrite = WebserviceOperations.getMetadataToWrite(params);
        BlackLabIndex index = params.blIndex();
        ResultDocInfo docInfo = WebserviceOperations.docInfo(index, params.getDocPid(), null, metadataToWrite);

        Map<String, List<String>> metadataFieldGroups = WebserviceOperations.getMetadataFieldGroupsWithRest(index);
        Map<String, String> docFields = WebserviceOperations.getDocFields(index);
        Map<String, String> metaDisplayNames = WebserviceOperations.getMetaDisplayNames(index);

        // Document info
        DStream.docInfoResponse(ds, docInfo, metadataFieldGroups, docFields, metaDisplayNames);
    }

    /**
     * Return a snippet from a document.
     *
     * @param params parameters
     * @param ds output stream
     */
    public static void opDocSnippet(WebserviceParams params, DataStream ds) {
        ResultDocSnippet result = WebserviceOperations.docSnippet(params);
        DStream.hitOrFragmentInfo(ds, result);
    }

    /**
     * Calculate term frequencies.
     *
     * @param params parameters
     * @param ds output stream
     */
    public static void opTermFreq(WebserviceParams params, DataStream ds) {
        TermFrequencyList tfl = WebserviceOperations.getTermFrequencies(params);
        DStream.termFreqResponse(ds, tfl);
    }


    /**
     * Return autocomplete results for metadata or annotated field.
     *
     * @param params parameters
     * @param ds output stream
     */
    public static void opAutocomplete(WebserviceParams params, DataStream ds) {
        ResultAutocomplete result = WebserviceOperations.autocomplete(params);
        DStream.autoComplete(ds, result);
    }

    public static void opInputFormatInfo(WebserviceParams params, DataStream ds) {
        ResultInputFormat result = WebserviceOperations.inputFormat(params.getInputFormat().get());
        DStream.formatInfoResponse(ds, result);
    }

    public static void opListInputFormats(WebserviceParams params, DataStream ds) {
        ResultListInputFormats result = WebserviceOperations.listInputFormats(params);
        DStream.listFormatsResponse(ds, result);
    }
}
