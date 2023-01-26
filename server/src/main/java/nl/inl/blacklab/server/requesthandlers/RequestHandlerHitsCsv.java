package nl.inl.blacklab.server.requesthandlers;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.server.datastream.DataFormat;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.lib.WebserviceOperation;
import nl.inl.blacklab.server.lib.WriteCsv;
import nl.inl.blacklab.server.lib.results.ResultHitsCsv;
import nl.inl.blacklab.server.lib.results.WebserviceOperations;

/**
 * Request handler for hit results.
 */
public class RequestHandlerHitsCsv extends RequestHandler {

    public RequestHandlerHitsCsv(UserRequestBls userRequest) {
        super(userRequest, WebserviceOperation.HITS_CSV);
    }

    @Override
    public int handle(DataStream ds) throws BlsException, InvalidQuery {
        ResultHitsCsv result = WebserviceOperations.hitsCsv(params);
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
