package nl.inl.blacklab.server.requesthandlers;

import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.lib.ResultDocInfo;
import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.lib.WebserviceOperations;

/**
 * Get information about a document.
 */
public class RequestHandlerDocInfo extends RequestHandler {

    public RequestHandlerDocInfo(BlackLabServer servlet, HttpServletRequest request, User user, String indexName,
            String urlResource, String urlPathPart) {
        super(servlet, request, user, indexName, urlResource, urlPathPart);
    }

    @Override
    public int handle(DataStream ds) throws BlsException {
        int i = urlPathInfo.indexOf('/');
        String docPid = i >= 0 ? urlPathInfo.substring(0, i) : urlPathInfo;
        ResultDocInfo docInfo = WebserviceOperations.getDocInfo(blIndex(), params, docPid);

        // Document info
        debug(logger, "REQ doc info: " + indexName + "-" + docPid);
        ds.startMap().entry("docPid", docInfo.getPid());
        {
            ds.startEntry("docInfo");
            {
                dataStreamDocumentInfo(ds, docInfo);
            }
            ds.endEntry();
            dataStreamMetadataGroupInfo(ds, WebserviceOperations.getMetadataGroupInfo(blIndex()));
            datastreamMetadataFieldInfo(ds, blIndex());
        }
        ds.endMap();
        return HTTP_OK;
    }

    @Override
    protected boolean isDocsOperation() {
        return true;
    }

}
