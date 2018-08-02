package nl.inl.blacklab.server.requesthandlers;

import javax.servlet.http.HttpServletRequest;

import org.apache.lucene.search.BooleanQuery.TooManyClauses;

import nl.inl.blacklab.queryParser.corpusql.CorpusQueryLanguageParser;
import nl.inl.blacklab.queryParser.corpusql.ParseException;
import nl.inl.blacklab.search.QueryExplanation;
import nl.inl.blacklab.search.Searcher;
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
        Searcher searcher = getSearcher();
        String patt = searchParam.getString("patt");
        try {
            QueryExplanation explanation = searcher.explain(CorpusQueryLanguageParser.parse(patt));

            // Assemble response
            ds.startMap()
                    .entry("textPattern", patt)
                    .entry("originalQuery", explanation.getOriginalQuery())
                    .entry("rewrittenQuery", explanation.getRewrittenQuery());
            ds.endMap();
        } catch (TooManyClauses e) {
            return Response.badRequest(ds, "QUERY_TOO_BROAD",
                    "Query too broad, too many matching terms. Please be more specific.");
        } catch (ParseException e) {
            return Response.badRequest(ds, "PATT_SYNTAX_ERROR",
                    "Syntax error in gapped CorpusQL pattern: " + e.getMessage());
        }

        return HTTP_OK;
    }

}
