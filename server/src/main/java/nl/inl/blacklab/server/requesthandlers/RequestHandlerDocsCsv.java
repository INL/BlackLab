package nl.inl.blacklab.server.requesthandlers;

import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataFormat;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.lib.WriteCsv;
import nl.inl.blacklab.server.lib.requests.ResultDocsCsv;

/**
 * Handle /docs requests that produce CSV.
 */
public class RequestHandlerDocsCsv extends RequestHandler {

    public RequestHandlerDocsCsv(BlackLabServer servlet, HttpServletRequest request, User user, String indexName,
            String urlResource, String urlPathPart) {
        super(servlet, request, user, indexName, urlResource, urlPathPart);
    }

    @Override
    public int handle(DataStream ds) throws BlsException, InvalidQuery {
        ResultDocsCsv result = ResultDocsCsv.get(params, searchMan);
        String csv;
        if (result.groups == null || result.isViewGroup)
            csv = WriteCsv.docs(params, result.docs, result.groups, result.subcorpusResults);
        else
            csv = WriteCsv.docGroups(params, result.docs, result.groups, result.subcorpusResults);
        ds.plain(csv);

        return HTTP_OK;
    }

    @Override
    public DataFormat getOverrideType() {
        return DataFormat.CSV;
    }

    @Override
    protected boolean isDocsOperation() {
        return true;
    }
}
