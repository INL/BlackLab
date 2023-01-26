package nl.inl.blacklab.server.requesthandlers;

import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.lib.Response;
import nl.inl.blacklab.server.lib.WebserviceOperation;

/**
 * Delete a user index.
 */
public class RequestHandlerDeleteIndex extends RequestHandler {
    public RequestHandlerDeleteIndex(UserRequestBls userRequest, String indexName,
            String urlResource, String urlPathPart) {
        super(userRequest, indexName, urlResource, urlPathPart, WebserviceOperation.DELETE_CORPUS);
    }

    @Override
    public int handle(DataStream ds) throws BlsException {
        if (indexName != null && indexName.length() > 0) {
            // Delete index
            try {
                debug(logger, "REQ delete index: " + indexName);
                indexMan.getIndex(indexName).blIndex(); // Make sure we're not deleting an index while it's in use.
                indexMan.deleteUserIndex(indexName);
                return Response.status(ds, "SUCCESS", "Index deleted succesfully.", HTTP_OK);
            } catch (BlsException e) {
                throw e;
            } catch (Exception e) {
                return Response.internalError(ds, e, debugMode, "INTERR_DELETING_INDEX_REQH");
            }
        }

        return Response.badRequest(ds, "CANNOT_CREATE_INDEX",
                "Could not delete index '" + indexName + "'. Specify a valid name.");
    }

}
