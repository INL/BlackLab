package nl.inl.blacklab.server.requesthandlers;

import nl.inl.blacklab.index.Indexer;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.NotAuthorized;
import nl.inl.blacklab.server.index.Index;
import nl.inl.blacklab.server.jobs.User;
import org.apache.lucene.search.Query;

import javax.servlet.http.HttpServletRequest;

public class RequestHandlerDeleteDocs extends RequestHandler {

    RequestHandlerDeleteDocs(BlackLabServer servlet, HttpServletRequest request, User user, String indexName, String urlResource, String urlPathInfo) {
        super(servlet, request, user, indexName, urlResource, urlPathInfo);
    }

    @Override
    public int handle(DataStream ds) throws BlsException {
        Index index = indexMan.getIndex(indexName);
        if (!index.isUserIndex() || !index.getUserId().equals(user.getUserId())) {
            throw new NotAuthorized("You can only delete data from your own private indices.");
        }

        Query query = searchParam.getFilterQuery();
        if (query == null) {
            throw new BadRequest("NO_FILTER_GIVEN",
                    "Document filter required. Please specify 'filter' parameter.");
        }

        int deletedCount;
        Indexer indexer = index.getIndexer();
        try {
            deletedCount = indexer.indexWriter().delete(query);
        } finally {
            indexer.close();
        }

        return Response.success(ds, String.format("Deleted %d documents.", deletedCount));
    }
}
