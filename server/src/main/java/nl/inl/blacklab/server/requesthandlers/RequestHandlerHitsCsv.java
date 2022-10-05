package nl.inl.blacklab.server.requesthandlers;

import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataFormat;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.lib.WriteCsv;
import nl.inl.blacklab.server.lib.requests.ResultHitsCsv;
import nl.inl.blacklab.server.lib.requests.WebserviceOperations;

/**
 * Request handler for hit results.
 */
public class RequestHandlerHitsCsv extends RequestHandler {

    public RequestHandlerHitsCsv(BlackLabServer servlet, HttpServletRequest request, User user, String indexName,
            String urlResource, String urlPathPart) {
        super(servlet, request, user, indexName, urlResource, urlPathPart);
    }

    @Override
    public int handle(DataStream ds) throws BlsException, InvalidQuery {
        ResultHitsCsv result = WebserviceOperations.hitsCsv(params, searchMan);
        String csv;
        if (result.getGroups() != null && !result.isViewGroup()) {
            csv = WriteCsv.hitsGroupsResponse(result);
        } else {
            csv = WriteCsv.hitsResponse(result);
        }
        ds.plain(csv);
        return HTTP_OK;
    }

    @Override
    public DataFormat getOverrideType() {
        return DataFormat.CSV;
    }

}
