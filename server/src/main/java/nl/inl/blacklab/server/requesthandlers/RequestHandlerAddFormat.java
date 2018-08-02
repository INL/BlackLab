package nl.inl.blacklab.server.requesthandlers;

import java.io.IOException;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.fileupload.FileItem;

import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.index.DocIndexerFactoryUserFormats;
import nl.inl.blacklab.server.jobs.User;
import nl.inl.blacklab.server.util.FileUploadHandler;

/**
 * Add or update an input format configuration.
 */
public class RequestHandlerAddFormat extends RequestHandler {

    public RequestHandlerAddFormat(BlackLabServer servlet,
            HttpServletRequest request, User user, String indexName,
            String urlResource, String urlPathPart) {
        super(servlet, request, user, indexName, urlResource, urlPathPart);
    }

    @Override
    public int handle(final DataStream ds) throws BlsException {
        debug(logger, "REQ add format: " + indexName);

        List<FileItem> files = FileUploadHandler.getFiles(request);
        if (files.size() != 1)
            throw new BadRequest("CANNOT_CREATE_INDEX",
                    "Adding a format requires the request to contain a single file in the 'data' field.");
        FileItem file = files.get(0);
        if (!file.getFieldName().equals("data"))
            throw new BadRequest("CANNOT_CREATE_INDEX",
                    "Adding a format requires the request to contain a single file in the 'data' field.");

        String fileName = file.getName();
        try {
            DocIndexerFactoryUserFormats formatMan = searchMan.getIndexManager().getUserFormatManager();
            if (formatMan == null)
                throw new BadRequest("CANNOT_CREATE_INDEX ",
                        "Could not create/overwrite format. The server is not configured with support for user content.");

            formatMan.createUserFormat(user, fileName, file.getInputStream());
            return Response.success(ds, "Format added.");
        } catch (IOException e) {
            throw new BadRequest("", e.getMessage());
        }
    }
}
