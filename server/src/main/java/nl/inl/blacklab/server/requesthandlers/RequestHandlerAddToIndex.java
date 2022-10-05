package nl.inl.blacklab.server.requesthandlers;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.InternalServerError;
import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.lib.requests.WebserviceOperations;

/**
 * Add document(s) to a user index.
 */
public class RequestHandlerAddToIndex extends RequestHandler {

    public RequestHandlerAddToIndex(BlackLabServer servlet,
            HttpServletRequest request, User user, String indexName,
            String urlResource, String urlPathPart) {
        super(servlet, request, user, indexName, urlResource, urlPathPart);
    }

    @Override
    public int handle(DataStream ds) throws BlsException {
        debug(logger, "REQ add data: " + indexName);

        // Read uploaded files before checking for errors, or the client won't see our response :(
        // See https://stackoverflow.com/questions/18367824/how-to-cancel-http-upload-from-data-events/18370751#18370751
        List<FileItem> dataFiles = new ArrayList<>();
        Map<String, File> linkedFiles = new HashMap<>();
        try {
            for (FileItem f : FileUploadHandler.getFiles(request)) {
                switch (f.getFieldName()) {
                case "data":
                case "data[]":
                    dataFiles.add(f);
                    break;
                case "linkeddata":
                case "linkeddata[]":
                    String fileNameOnly = FilenameUtils.getName(f.getName());
                    File temp = Files.createTempFile("", fileNameOnly).toFile();
                    temp.deleteOnExit();

                    try (OutputStream tempOut = new FileOutputStream(temp)) {
                        IOUtils.copy(f.getInputStream(), tempOut);
                    }
                    linkedFiles.put(fileNameOnly.toLowerCase(), temp);
                    break;
                }
            }
        } catch (IOException e) {
            throw new InternalServerError("Error occurred during indexing: " + e.getMessage(),
                    "INTERR_WHILE_INDEXING1");
        }

        String indexError = WebserviceOperations.addToIndex(indexName, indexMan, user, dataFiles, linkedFiles);
        if (indexError != null)
            throw new BadRequest("INDEX_ERROR", "An error occurred during indexing. (error text: " + indexError + ")");
        return Response.success(ds, "Data added succesfully.");
    }

}
