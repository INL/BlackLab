package nl.inl.blacklab.server.requesthandlers;

import java.util.Collection;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.IndexMetadata;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.lib.requests.ResultAnnotationInfo;
import nl.inl.blacklab.server.lib.requests.WebserviceOperations;

/**
 * Get information about a field in the index.
 */
public class RequestHandlerFieldInfo extends RequestHandler {

    public RequestHandlerFieldInfo(BlackLabServer servlet, HttpServletRequest request, User user, String indexName,
            String urlResource, String urlPathPart) {
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
            throw new BadRequest("UNKNOWN_OPERATION",
                    "Bad URL. Either specify a field name to show information about, or remove the 'fields' part to get general index information.");
        }

        BlackLabIndex blIndex = blIndex();
        IndexMetadata indexMetadata = blIndex.metadata();
        if (indexMetadata.annotatedFields().exists(fieldName)) {
            // Annotated field
            Collection<String> showValuesFor = params.getListValuesFor();
            AnnotatedField fieldDesc = indexMetadata.annotatedField(fieldName);
            if (!fieldDesc.isDummyFieldToStoreLinkedDocuments()) {
                Map<String, ResultAnnotationInfo> annotInfos = WebserviceOperations.getAnnotInfos(params,
                        fieldDesc.annotations());
                DStream.annotatedField(ds, indexName, fieldDesc, annotInfos);
            } else {
                // skip this, not really an annotated field, just exists to store linked (metadata) document.
            }
        } else {
            // Metadata field
            MetadataField fieldDesc = indexMetadata.metadataField(fieldName);
            Map<String, Integer> fieldValues = WebserviceOperations.getFieldValuesInOrder(fieldDesc);
            DStream.metadataField(ds, indexName, fieldDesc, true, fieldValues);
        }

        // Remove any empty settings
        //response.removeEmptyMapValues();

        return HTTP_OK;
    }

}
