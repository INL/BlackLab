package nl.inl.blacklab.server.requesthandlers;

import java.io.File;

import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.index.DocumentFormats;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.InternalServerError;
import nl.inl.blacklab.server.exceptions.NotAuthorized;
import nl.inl.blacklab.server.exceptions.NotFound;
import nl.inl.blacklab.server.index.IndexManager;
import nl.inl.blacklab.server.jobs.User;

/**
 * Delete an input format configuration.
 */
public class RequestHandlerDeleteFormat extends RequestHandler {

	public RequestHandlerDeleteFormat(BlackLabServer servlet,
			HttpServletRequest request, User user, String indexName,
			String urlResource, String urlPathPart) {
		super(servlet, request, user, indexName, urlResource, urlPathPart);
	}

	@Override
	public int handle(DataStream ds) throws BlsException {
		debug(logger, "REQ add format: " + indexName);

		// Get the uploaded file parameters
        String documentFormat = urlResource; //request.getParameter("format");
        String[] parts = documentFormat.split(":", -1);
        if (parts.length != 2)
            throw new BadRequest("ILLEGAL_INDEX_NAME", "User format configuration name must contain one colon");
        if (!user.getUserId().equals(parts[0]))
            throw new NotAuthorized("Can only delete your own formats.");
        if (!DocumentFormats.exists(documentFormat))
            throw new NotFound("FORMAT_NOT_FOUND", "Specified format was not found.");
        documentFormat = parts[1];
		if (!documentFormat.matches("[\\w_\\-]+"))
		    throw new BadRequest("ILLEGAL_INDEX_NAME", "Format configuration name may only contain letters, digits, underscore and dash");
        File userFormatDir = indexMan.getUserFormatDir(user.getUserId());
        File yamlFile = new File(userFormatDir, documentFormat + ".blf.yaml");
        File jsonFile = new File(userFormatDir, documentFormat + ".blf.json");
        boolean success = false;
        if (yamlFile.exists())
        	success |= yamlFile.delete();
        if (jsonFile.exists())
        	success |= jsonFile.delete();

    	if (!success) // If both files are missing, DocumentFormats.exists should have returned false?
    		throw new InternalServerError("Could not delete format. Unknown reason.", 35);

		DocumentFormats.unregister(IndexManager.userFormatName(user.getUserId(), documentFormat));
		return Response.success(ds, "Format deleted.");
	}

}
