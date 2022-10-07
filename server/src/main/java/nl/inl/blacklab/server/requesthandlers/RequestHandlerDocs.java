package nl.inl.blacklab.server.requesthandlers;

import java.util.Collection;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.lucene.document.Document;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.Concordance;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.Kwic;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.results.Concordances;
import nl.inl.blacklab.search.results.DocGroups;
import nl.inl.blacklab.search.results.DocResult;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.Hit;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.Kwics;
import nl.inl.blacklab.search.results.ResultsStats;
import nl.inl.blacklab.searches.SearchCacheEntry;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.jobs.ContextSettings;
import nl.inl.blacklab.server.lib.SearchCreator;
import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.lib.results.ResultDocInfo;
import nl.inl.blacklab.server.lib.results.ResultDocsResponse;
import nl.inl.blacklab.server.lib.results.ResultSummaryCommonFields;
import nl.inl.blacklab.server.lib.results.ResultSummaryNumDocs;
import nl.inl.blacklab.server.lib.results.ResultSummaryNumHits;
import nl.inl.blacklab.server.lib.results.WebserviceOperations;

/**
 * List documents, search for documents matching criteria.
 */
public class RequestHandlerDocs extends RequestHandler {

    public RequestHandlerDocs(BlackLabServer servlet, HttpServletRequest request, User user, String indexName,
            String urlResource, String urlPathPart) {
        super(servlet, request, user, indexName, urlResource, urlPathPart);
    }

    SearchCacheEntry<?> search = null;
    SearchCacheEntry<ResultsStats> originalHitsSearch;
    DocResults totalDocResults;
    DocResults window;
    private DocResults docResults;
    private long totalTime;

    @Override
    public int handle(DataStream ds) throws BlsException, InvalidQuery {
        // Do we want to view a single group after grouping?
        ResultDocsResponse result;
        if (params.getGroupProps().isPresent() && params.getViewGroup().isPresent()) {
            // View a single group in a grouped docs resultset
            result = WebserviceOperations.viewGroupDocsResponse(params, searchMan, indexMan);
        } else {
            // Regular set of docs (no grouping first)
            result = WebserviceOperations.regularDocsResponse(params, indexMan);
        }
        dstreamDocsResponse(ds, result);
        return HTTP_OK;
    }

    private static void dstreamDocsResponse(DataStream ds, ResultDocsResponse result) throws InvalidQuery {
        ResultSummaryCommonFields summaryFields = result.getSummaryFields();
        ResultSummaryNumDocs numResultDocs = result.getNumResultDocs();
        ResultSummaryNumHits numResultHits = result.getNumResultHits();
        boolean includeTokenCount = result.isIncludeTokenCount();
        long totalTokens = result.getTotalTokens();
        BlackLabIndex index = result.getIndex();
        Collection<MetadataField> metadataFieldsToList = result.getMetadataFieldsToList();
        SearchCreator params = result.getParams();

        ds.startMap();
        {

            // The summary
            ds.startEntry("summary").startMap();
            {
                DStream.summaryCommonFields(ds, summaryFields);
                if (numResultDocs != null) {
                    DStream.summaryNumDocs(ds, numResultDocs);
                } else {
                    DStream.summaryNumHits(ds, numResultHits);
                }
                if (includeTokenCount)
                    ds.entry("tokensInMatchingDocuments", totalTokens);

                Map<String, String> docFields = WebserviceOperations.getDocFields(index);
                Map<String, String> metaDisplayNames = WebserviceOperations.getMetaDisplayNames(index);
                DStream.metadataFieldInfo(ds, docFields, metaDisplayNames);
            }
            ds.endMap().endEntry();

            // The hits and document info
            ds.startEntry("docs").startList();
            for (DocResult dr: result.getWindow()) {
                // Find pid
                Document document = params.blIndex().luceneDoc(dr.docId());
                String pid = WebserviceOperations.getDocumentPid(index, dr.identity().value(), document);
                ResultDocInfo docInfo = WebserviceOperations.docInfo(index, null, document, metadataFieldsToList);

                ds.startItem("doc").startMap();
                {
                    // Combine all
                    ds.entry("docPid", pid);
                    long numHits = dr.size();
                    if (numHits > 0)
                        ds.entry("numberOfHits", numHits);

                    // Doc info (metadata, etc.)
                    ds.startEntry("docInfo");
                    {
                        DStream.documentInfo(ds, docInfo);
                    }
                    ds.endEntry();

                    // Snippets
                    Hits hits2 = dr.storedResults().window(0, 5); // TODO: make num. snippets configurable
                    if (hits2.hitsStats().processedAtLeast(1)) {
                        ds.startEntry("snippets").startList();
                        ContextSettings contextSettings = params.contextSettings();
                        Concordances concordances = null;
                        Kwics kwics = null;
                        if (contextSettings.concType() == ConcordanceType.CONTENT_STORE)
                            concordances = hits2.concordances(contextSettings.size(), ConcordanceType.CONTENT_STORE);
                        else
                            kwics = hits2.kwics(index.defaultContextSize());
                        Collection<Annotation> annotationsTolist = result.getAnnotationsTolist();
                        for (Hit hit: hits2) {
                            // TODO: use RequestHandlerDocSnippet.getHitOrFragmentInfo()
                            ds.startItem("snippet").startMap();
                            if (contextSettings.concType() == ConcordanceType.CONTENT_STORE) {
                                // Add concordance from original XML
                                Concordance c = concordances.get(hit);
                                ds.startEntry("left").xmlFragment(c.left()).endEntry()
                                        .startEntry("match").xmlFragment(c.match()).endEntry()
                                        .startEntry("right").xmlFragment(c.right()).endEntry();
                            } else {
                                // Add KWIC info
                                Kwic c = kwics.get(hit);
                                ds.startEntry("left").contextList(c.annotations(), annotationsTolist, c.left())
                                        .endEntry()
                                        .startEntry("match").contextList(c.annotations(), annotationsTolist, c.match())
                                        .endEntry()
                                        .startEntry("right").contextList(c.annotations(), annotationsTolist, c.right())
                                        .endEntry();
                            }
                            ds.endMap().endItem();
                        } // for hits2
                        ds.endList().endEntry();
                    } // if snippets
                    
                }
                ds.endMap().endItem();
            }
            ds.endList().endEntry();
            if (params.hasFacets()) {
                // Now, group the docs according to the requested facets.
                ds.startEntry("facets");
                {
                    Map<DocProperty, DocGroups> counts = params.facets().execute().countsPerFacet();
                    DStream.facets(ds, WebserviceOperations.getFacetInfo(counts));
                }
                ds.endEntry();
            }
        }
        ds.endMap();
    }

    @Override
    protected boolean isDocsOperation() {
        return true;
    }

}
