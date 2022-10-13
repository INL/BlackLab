package nl.inl.blacklab.server.requesthandlers;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.lib.results.ResultDocInfo;
import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.lib.results.WebserviceOperations;

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
        Collection<MetadataField> metadataToWrite = WebserviceOperations.getMetadataToWrite(params);
        ResultDocInfo docInfo = WebserviceOperations.docInfo(blIndex(), docPid, null, metadataToWrite);

        Map<String, List<String>> metadataFieldGroups = WebserviceOperations.getMetadataFieldGroupsWithRest(blIndex());
        Map<String, String> docFields = WebserviceOperations.getDocFields(blIndex());
        Map<String, String> metaDisplayNames = WebserviceOperations.getMetaDisplayNames(blIndex());

        // Document info
        debug(logger, "REQ doc info: " + indexName + "-" + docPid);
        dstreamDocInfoResponse(ds, docInfo, metadataFieldGroups, docFields, metaDisplayNames);
        return HTTP_OK;
    }

    private static void dstreamDocInfoResponse(DataStream ds, ResultDocInfo docInfo, Map<String, List<String>> metadataFieldGroups,
            Map<String, String> docFields, Map<String, String> metaDisplayNames) {
        ds.startMap().entry("docPid", docInfo.getPid());
        {
            ds.startEntry("docInfo");
            {
                DStream.documentInfo(ds, docInfo);
            }
            ds.endEntry();

            // (this probably shouldn't be here)
            DStream.metadataGroupInfo(ds, metadataFieldGroups);
            DStream.metadataFieldInfo(ds, docFields, metaDisplayNames);
        }
        ds.endMap();
    }

    @Override
    protected boolean isDocsOperation() {
        return true;
    }

}
