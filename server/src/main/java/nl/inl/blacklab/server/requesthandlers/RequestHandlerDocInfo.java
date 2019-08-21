package nl.inl.blacklab.server.requesthandlers;

import javax.servlet.http.HttpServletRequest;

import org.apache.lucene.document.Document;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.InternalServerError;
import nl.inl.blacklab.server.exceptions.NotFound;
import nl.inl.blacklab.server.jobs.User;
import nl.inl.blacklab.server.util.BlsUtils;

/**
 * Get information about the structure of an index.
 */
public class RequestHandlerDocInfo extends RequestHandler {

    public RequestHandlerDocInfo(BlackLabServer servlet, HttpServletRequest request, User user, String indexName,
            String urlResource, String urlPathPart) {
        super(servlet, request, user, indexName, urlResource, urlPathPart);
    }

    @Override
    public int handle(DataStream ds) throws BlsException {

        int i = urlPathInfo.indexOf('/');
        String docId = i >= 0 ? urlPathInfo.substring(0, i) : urlPathInfo;
        if (docId.length() == 0)
            throw new BadRequest("NO_DOC_ID", "Specify document pid.");

        BlackLabIndex blIndex = blIndex();
        int luceneDocId = BlsUtils.getDocIdFromPid(blIndex, docId);
        if (luceneDocId < 0)
            throw new NotFound("DOC_NOT_FOUND", "Document with pid '" + docId + "' not found.");
        Document document = blIndex.doc(luceneDocId).luceneDoc();
        if (document == null)
            throw new InternalServerError("Couldn't fetch document with pid '" + docId + "'.", "INTERR_FETCHING_DOCUMENT_INFO");

        // Document info
        debug(logger, "REQ doc info: " + indexName + "-" + docId);

        ds.startMap()
                .entry("docPid", docId);

        ds.startEntry("docInfo");
        dataStreamDocumentInfo(ds, blIndex, document, getMetadataToWrite());
        ds.endEntry();

        ds.startEntry("docFields");
        RequestHandler.dataStreamDocFields(ds, blIndex.metadata());
        ds.endEntry();
        
        ds.startEntry("metadataFieldDisplayNames");
        RequestHandler.dataStreamMetadataFieldDisplayNames(ds, blIndex.metadata());
        ds.endEntry();

        ds.endMap();
        return HTTP_OK;
    }

    @Override
    protected boolean isDocsOperation() {
        return true;
    }

}
