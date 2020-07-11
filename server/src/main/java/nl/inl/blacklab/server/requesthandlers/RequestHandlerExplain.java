package nl.inl.blacklab.server.requesthandlers;

import javax.servlet.http.HttpServletRequest;

import org.apache.lucene.search.BooleanQuery.TooManyClauses;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.queryParser.corpusql.CorpusQueryLanguageParser;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.QueryExplanation;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.jobs.User;

/**
 * Get information about the structure of an index.
 */
public class RequestHandlerExplain extends RequestHandler {

    public RequestHandlerExplain(BlackLabServer servlet, HttpServletRequest request, User user, String indexName,
            String urlResource, String urlPathPart) {
        super(servlet, request, user, indexName, urlResource, urlPathPart);
    }

    @Override
    public boolean isCacheAllowed() {
        return false; // because status might change (or you might reindex)
    }

    @Override
    public int handle(DataStream ds) throws BlsException {
        BlackLabIndex blIndex = blIndex();
        String patt = searchParam.getString("patt");
        try {
            TextPattern tp = CorpusQueryLanguageParser.parse(patt);
            BLSpanQuery q = tp.toQuery(QueryInfo.create(blIndex));
            QueryExplanation explanation = blIndex.explain(q);

            // Assemble response
            ds.startMap()
                    .entry("textPattern", patt)
                    .entry("originalQuery", explanation.originalQuery())
                    .entry("rewrittenQuery", explanation.rewrittenQuery());
            ds.endMap();
        } catch (TooManyClauses e) {
            return Response.badRequest(ds, "QUERY_TOO_BROAD",
                    "Query too broad, too many matching terms. Please be more specific.");
        } catch (InvalidQuery e) {
            return Response.badRequest(ds, "PATT_SYNTAX_ERROR",
                    "Syntax error in gapped CorpusQL pattern: " + e.getMessage());
        }

        return HTTP_OK;
    }

}
