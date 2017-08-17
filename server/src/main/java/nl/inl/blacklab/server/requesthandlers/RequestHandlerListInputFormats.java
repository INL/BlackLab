package nl.inl.blacklab.server.requesthandlers;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

import nl.inl.blacklab.index.DocumentFormats;
import nl.inl.blacklab.index.DocumentFormats.FormatDesc;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.NotFound;
import nl.inl.blacklab.server.jobs.User;
import nl.inl.blacklab.server.search.IndexManager;

/**
 * Get information about supported input formats.
 */
public class RequestHandlerListInputFormats extends RequestHandler {

	public RequestHandlerListInputFormats(BlackLabServer servlet, HttpServletRequest request, User user, String indexName, String urlResource, String urlPathPart) {
		super(servlet, request, user, indexName, urlResource, urlPathPart);
	}

	@Override
	public boolean isCacheAllowed() {
		return false; // You can create/delete formats, don't cache the list
	}

	@Override
	public int handle(DataStream ds) throws BlsException {

	    if (urlResource != null && urlResource.length() > 0) {
	        if (!DocumentFormats.exists(urlResource))
                throw new NotFound("NOT_FOUND", "The format '" + urlResource + "' does not exist.");

	        File formatFile = DocumentFormats.getConfig(urlResource).getReadFromFile();
	        try (BufferedReader reader = DocumentFormats.getFormatFile(urlResource)) {
	        	if (reader == null)
	        		throw new NotFound("NOT_FOUND", "The format '" + urlResource + "' is not configuration-based, and therefore cannot be displayed.");

	        	ds	.startMap()
        		.entry("formatName", urlResource)
        		.entry("configFileType", FilenameUtils.getExtension(formatFile.getName()))
        		.entry("configFile", IOUtils.toString(reader))
        		.endMap();
        		return HTTP_OK;
	        } catch (IOException e1) {
				throw new RuntimeException(e1);
			}
	    }

		ds.startMap();
		ds.startEntry("user").startMap();
		ds.entry("loggedIn", user.isLoggedIn());
		if (user.isLoggedIn())
			ds.entry("id", user.getUserId());
		boolean canCreateIndex = user.isLoggedIn() ? indexMan.canCreateIndex(user.getUserId()) : false;
        ds.entry("canCreateIndex", canCreateIndex);
		ds.endMap().endEntry();

		// List supported input formats
	    ds.startEntry("supportedInputFormats").startMap();
        for (FormatDesc format: DocumentFormats.getSupportedFormats()) {
            String name = format.getName();
            if (IndexManager.userOwnsFormat(user.getUserId(), name)) {
                ds.startAttrEntry("format", "name", name).startMap()
                    .entry("displayName", format.getDisplayName())
                    .entry("description", format.getDescription())
                    .entry("configurationBased", format.isConfigurationBased())
	            .endMap().endAttrEntry();
            }
        }
	    ds.endMap().endEntry();
		ds.endMap();

		return HTTP_OK;
	}


}
