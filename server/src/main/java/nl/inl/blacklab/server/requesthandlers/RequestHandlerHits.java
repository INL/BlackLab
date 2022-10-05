package nl.inl.blacklab.server.requesthandlers;

import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.QueryExplanation;
import nl.inl.blacklab.search.TermFrequency;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.lib.SearchCreator;
import nl.inl.blacklab.server.lib.SearchTimings;
import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.lib.results.ResultHits;
import nl.inl.blacklab.server.lib.results.ResultListOfHits;
import nl.inl.blacklab.server.lib.results.ResultSummaryNumHits;
import nl.inl.blacklab.server.lib.results.ResultSummaryCommonFields;
import nl.inl.blacklab.server.lib.results.WebserviceOperations;

/**
 * Request handler for hit results.
 */
public class RequestHandlerHits extends RequestHandler {

    public RequestHandlerHits(BlackLabServer servlet, HttpServletRequest request, User user, String indexName,
            String urlResource, String urlPathPart) {
        super(servlet, request, user, indexName, urlResource, urlPathPart);
    }

    @Override
    public int handle(DataStream ds) throws BlsException, InvalidQuery {
        if (params.isCalculateCollocations()) {
            // Collocations request
            TermFrequencyList tfl = WebserviceOperations.calculateCollocations(params);
            dstreamCollocationsResponse(ds, tfl);
        } else {
            // Hits request
            ResultHits resultHits = WebserviceOperations.getResultHits(params, indexMan);
            dstreamHitsResponse(ds, resultHits);
        }
        return HTTP_OK;
    }

    private static void dstreamCollocationsResponse(DataStream ds, TermFrequencyList tfl) {
        ds.startMap().startEntry("tokenFrequencies").startMap();
        for (TermFrequency tf : tfl) {
            ds.attrEntry("token", "text", tf.term, tf.frequency);
        }
        ds.endMap().endEntry().endMap();
    }

    private static void dstreamHitsResponse(DataStream ds, ResultHits resultHits)
            throws InvalidQuery {
        SearchCreator params = resultHits.getParams();
        Hits hits = resultHits.getHits();
        BlackLabIndex index = hits.index();
        Hits window = resultHits.getWindow();
        // Search time should be time user (originally) had to wait for the response to this request.
        // Count time is the time it took (or is taking) to iterate through all the results to count the total.
        SearchTimings timings = resultHits.getSearchTimings();
        ResultSummaryCommonFields summaryFields = WebserviceOperations.summaryCommonFields(params,
                resultHits.getIndexStatus(), timings, null, window.windowStats());
        ResultListOfHits listOfHits = WebserviceOperations.listOfHits(params, window, resultHits.getConcordanceContext(),
                resultHits.getDocIdToPid());

        ds.startMap();

        // The summary
        ds.startEntry("summary").startMap();
        {
            DStream.summaryCommonFields(ds, summaryFields);
            ResultSummaryNumHits result = WebserviceOperations.numResultsSummaryHits(
                    resultHits.getHitsStats(), resultHits.getDocsStats(),
                    params.getWaitForTotal(), timings.getCountTime() < 0, null);
            DStream.summaryNumHits(ds, result);
            if (params.getIncludeTokenCount())
                ds.entry("tokensInMatchingDocuments", resultHits.getTotalTokens());

            // Write docField (pidField, titleField, etc.) and metadata display names
            // (these arguably shouldn't be included with every hits response; can be read once from the index
            //  metadata response)
            DStream.metadataFieldInfo(ds, resultHits.getDocFields(), resultHits.getMetaDisplayNames());

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
        DStream.listOfHits(ds, listOfHits);
        DStream.documentInfos(ds, resultHits.getDocInfos());

        // Facets (if requested)
        if (resultHits.hasFacets()) {
            // Now, group the docs according to the requested facets.
            ds.startEntry("facets");
            {
                DStream.facets(ds, resultHits.getFacetInfo());
            }
            ds.endEntry();
        }

        ds.endMap();
    }

}
