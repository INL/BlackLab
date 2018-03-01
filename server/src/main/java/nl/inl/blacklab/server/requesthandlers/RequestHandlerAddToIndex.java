package nl.inl.blacklab.server.requesthandlers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.fileupload.FileItem;

import nl.inl.blacklab.index.IndexListenerReportConsole;
import nl.inl.blacklab.index.Indexer;
import nl.inl.blacklab.search.indexstructure.IndexStructure;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.InternalServerError;
import nl.inl.blacklab.server.exceptions.NotAuthorized;
import nl.inl.blacklab.server.index.Index;
import nl.inl.blacklab.server.jobs.User;
import nl.inl.blacklab.server.util.FileUploadHandler;

/**
 * Display the contents of the cache.
 */
public class RequestHandlerAddToIndex extends RequestHandler {
	// TODO make configurable?
	public static final int MAX_TOKEN_COUNT = 1_000_000;

	String indexError = null;

	public RequestHandlerAddToIndex(BlackLabServer servlet,
			HttpServletRequest request, User user, String indexName,
			String urlResource, String urlPathPart) {
		super(servlet, request, user, indexName, urlResource, urlPathPart);
	}

	@Override
	public int handle(DataStream ds) throws BlsException {
		debug(logger, "REQ add data: " + indexName);

		FileItem file = FileUploadHandler.getFile(request, "data");
		Index index = indexMan.getIndex(indexName);
		IndexStructure indexStructure = index.getIndexStructure();

		if (!index.isUserIndex() || !index.getUserId().equals(user.getUserId()))
			throw new NotAuthorized("You can only add new data to your own private indices.");

		if (indexStructure.getTokenCount() > MAX_TOKEN_COUNT) {
			throw new NotAuthorized("Sorry, this index is already larger than the maximum of " + MAX_TOKEN_COUNT + " tokens. Cannot add any more data to it.");
		}

		Indexer indexer = index.getIndexer();
		indexer.setListener(new IndexListenerReportConsole() {
			@Override
			public synchronized boolean errorOccurred(String error, String unitType, File unit, File subunit) {
				indexError = error + " in " + unit + (subunit == null ? "" : " (" + subunit + ")");
				super.errorOccurred(error, unitType, unit, subunit);
				return false; // Don't continue indexing
			}
		});

		try (InputStream is = file.getInputStream()) {
			indexer.index(file.getName(), is, "*.xml");
			if (indexError == null) {
				if (indexer.getListener().getFilesProcessed() == 0)
					indexError = "No files were found when indexing, only .xml files, or archives containing .xml files are supported at the moment.";
				else if (indexer.getListener().getDocsDone() == 0)
					indexError = "No documents were found when indexing, are the files in the correct format?";
				else if (indexer.getListener().getTokensProcessed() == 0)
					indexError = "No tokens were found when indexing, are the files in the correct format?";
			}
		} catch(IOException e) {
			throw new InternalServerError("Error occured during indexing: " + e.getMessage(), 41);
		} finally {
			// It's important we roll back on errors, or an incorrect indexstructure might be written.
			// See Indexer#hasRollback
			if (indexError != null)
				indexer.rollback();

			indexer.close();
		}

		if (indexError != null)
			throw new BadRequest("INDEX_ERROR", "An error occurred during indexing. (error text: " + indexError + ")");
		return Response.success(ds, "Data added succesfully.");
	}

}
