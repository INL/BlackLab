package nl.inl.blacklab.server.requesthandlers;

import java.io.File;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItem;

import nl.inl.blacklab.index.config.ConfigInputFormat;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.InternalServerError;
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

        try {
			FileItem fi = FileUploadHandler.getFile(request, "data");
			// Get the uploaded file parameters
			String fileName = fi.getName();
			if (!fileName.matches("[\\w_\\-]+(\\.blf)?\\.(ya?ml|json)"))
			    throw new BadRequest("ILLEGAL_INDEX_NAME", "Format configuration name may only contain letters, digits, underscore and dash, and must end with .yaml or .json (or .blf.yaml/.blf.json)");
			String formatIdentifier = ConfigInputFormat.stripExtensions(fileName);
			boolean isJson = fileName.endsWith(".json");
            File userFormatDir = indexMan.getUserFormatDir(user.getUserId());
			File formatFile = new File(userFormatDir, formatIdentifier + ".blf." + (isJson ? "json" : "yaml"));
			fi.write(formatFile);

			searchMan.getIndexManager().getUserFormatManager().registerFormat(user, formatFile);
        } catch (IllegalArgumentException e) {
            return Response.error(ds, "CONFIG_ERROR", "Error in format configuration: " + e.getMessage(), HttpServletResponse.SC_BAD_REQUEST);
        } catch (Exception e) {
			throw new InternalServerError(e.getMessage(), 40);
		}
		return Response.success(ds, "Format added.");
	}

}
