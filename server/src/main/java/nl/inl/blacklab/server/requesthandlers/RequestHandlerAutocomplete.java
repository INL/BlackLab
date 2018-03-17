package nl.inl.blacklab.server.requesthandlers;


import java.util.List;
import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.search.Searcher;
import nl.inl.blacklab.search.indexstructure.IndexStructure;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.jobs.User;
import nl.inl.util.LuceneUtil;
import org.apache.lucene.index.IndexReader;

/**
 * Autocompletion for metadata fields.
 */
public class RequestHandlerAutocomplete extends RequestHandler {

    private static final int MAX_VALUES = 30;

    public RequestHandlerAutocomplete(BlackLabServer servlet, HttpServletRequest request, User user, String indexName, String urlResource, String urlPathPart) {
        super(servlet, request, user, indexName, urlResource, urlPathPart);
    }

    @Override
    public boolean isCacheAllowed() {
        return false; // Because reindexing might change something
    }

    @Override
    public int handle(DataStream ds) throws BlsException {

        int i = urlPathInfo.indexOf('/');
        String fieldName = i >= 0 ? urlPathInfo.substring(0, i) : urlPathInfo;
        if (fieldName.length() == 0) {
            throw new BadRequest("UNKNOWN_OPERATION", "Bad URL. Specify a field name to autocomplete.");
        }
        String term = searchParam.getString("term");
        if (term == null || term.isEmpty()) {
            throw new BadRequest("UNKNOWN_OPERATION", "Bad URL. Specify a term for "+fieldName+" to autocomplete.");
        }

        Searcher searcher = getSearcher();
        IndexStructure struct = searcher.getIndexStructure();

        if (struct.getComplexFields().contains(fieldName)) {
            throw new BadRequest("COMPLEX_FIELD_NOT_ALLOWED", "autocomplete not supported for complexfield: " + fieldName);
        } else {
            autoComplete(ds, fieldName, term, searcher.getIndexReader());
        }

        return HTTP_OK;
    }

    public static void autoComplete(DataStream ds, String fieldName, String term, IndexReader reader) {
        ds.startList();
        LuceneUtil.findTermsByPrefix(reader, fieldName, term, false, MAX_VALUES).forEach((v) -> {
            ds.item(v, v);
        });
        ds.endList();
    }

}
