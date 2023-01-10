package nl.inl.blacklab.server.requesthandlers;

import java.util.Collection;

import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.Concordance;
import nl.inl.blacklab.search.Kwic;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.lib.results.DStream;
import nl.inl.blacklab.server.lib.results.ResultDocResult;
import nl.inl.blacklab.server.lib.results.ResultDocsResponse;
import nl.inl.blacklab.server.lib.results.WebserviceOperations;

/**
 * List documents, search for documents matching criteria.
 */
public class RequestHandlerDocs extends RequestHandler {

    public RequestHandlerDocs(BlackLabServer servlet, HttpServletRequest request, User user, String indexName,
            String urlResource, String urlPathPart) {
        super(servlet, request, user, indexName, urlResource, urlPathPart);
    }

    @Override
    public int handle(DataStream ds) throws BlsException, InvalidQuery {
        // Do we want to view a single group after grouping?
        ResultDocsResponse result;
        if (params.getGroupProps().isPresent() && params.getViewGroup().isPresent()) {
            // View a single group in a grouped docs resultset
            result = WebserviceOperations.viewGroupDocsResponse(params);
        } else {
            // Regular set of docs (no grouping first)
            result = WebserviceOperations.regularDocsResponse(params);
        }
        dstreamDocsResponse(ds, result);
        return HTTP_OK;
    }

    private static void dstreamDocsResponse(DataStream ds, ResultDocsResponse result) throws InvalidQuery {
        ds.startMap();
        {
            // The summary
            ds.startEntry("summary").startMap();
            {
                DStream.summaryCommonFields(ds, result.getSummaryFields());
                if (result.getNumResultDocs() != null) {
                    DStream.summaryNumDocs(ds, result.getNumResultDocs());
                } else {
                    DStream.summaryNumHits(ds, result.getNumResultHits());
                }
                if (result.isIncludeTokenCount())
                    ds.entry("tokensInMatchingDocuments", result.getTotalTokens());

                DStream.metadataFieldInfo(ds, result.getDocFields(), result.getMetaDisplayNames());
            }
            ds.endMap().endEntry();

            // The hits and document info
            ds.startEntry("docs").startList();
            for (ResultDocResult docResult: result.getDocResults()) {
                dstreamDocResult(ds, docResult);
            }
            ds.endList().endEntry();
            if (result.getFacetInfo() != null) {
                // Now, group the docs according to the requested facets.
                ds.startEntry("facets");
                {
                    DStream.facets(ds, result.getFacetInfo());
                }
                ds.endEntry();
            }
        }
        ds.endMap();
    }

    private static void dstreamDocResult(DataStream ds, ResultDocResult result) {
        ds.startItem("doc").startMap();
        {
            // Combine all
            ds.entry("docPid", result.getPid());
            if (result.numberOfHits() > 0)
                ds.entry("numberOfHits", result.numberOfHits());

            // Doc info (metadata, etc.)
            ds.startEntry("docInfo");
            {
                DStream.documentInfo(ds, result.getDocInfo());
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

    @Override
    protected boolean isDocsOperation() {
        return true;
    }

}
