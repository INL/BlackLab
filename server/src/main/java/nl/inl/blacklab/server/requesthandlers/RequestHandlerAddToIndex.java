package nl.inl.blacklab.server.requesthandlers;

import java.io.File;
import java.util.Arrays;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItem;

import nl.inl.blacklab.index.IndexListener;
import nl.inl.blacklab.index.IndexListenerReportConsole;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.IndexNotFound;
import nl.inl.blacklab.server.exceptions.InternalServerError;
import nl.inl.blacklab.server.exceptions.NotAuthorized;
import nl.inl.blacklab.server.index.IndexTask;
import nl.inl.blacklab.server.jobs.User;
import nl.inl.blacklab.server.search.IndexManager.IndexStatus;
import nl.inl.blacklab.server.util.FileUploadHandler;
import nl.inl.blacklab.server.util.FileUploadHandler.UploadedFileTask;

/**
 * Display the contents of the cache.
 */
public class RequestHandlerAddToIndex extends RequestHandler {

	String indexError = null;

	public RequestHandlerAddToIndex(BlackLabServer servlet,
			HttpServletRequest request, User user, String indexName,
			String urlResource, String urlPathPart) {
		super(servlet, request, user, indexName, urlResource, urlPathPart);
	}

	@Override
	public int handle(DataStream ds) throws BlsException {
		debug(logger, "REQ add data: " + indexName);

		if (!indexName.contains(":"))
			throw new NotAuthorized("Can only add to private indices.");
		if (!indexMan.indexExists(indexName))
			throw new IndexNotFound(indexName);

		IndexStatus status = indexMan.getIndexStatus(indexName);
		if (status != IndexStatus.AVAILABLE && status != IndexStatus.EMPTY)
			throw new BlsException(HttpServletResponse.SC_CONFLICT, "INDEX_UNAVAILABLE", "The index '" + indexName + "' is not available right now. Status: " + status);

		if (!indexMan.indexExists(indexName))
			throw new IndexNotFound(indexName);
		final File indexDir = indexMan.getIndexDir(indexName);
		IndexStatus newStatus = indexMan.setIndexStatus(indexName, Arrays.asList(IndexStatus.AVAILABLE, IndexStatus.EMPTY), IndexStatus.INDEXING);
		try {
			indexMan.closeSearcher(indexName);
			if (newStatus != IndexStatus.INDEXING) {
				throw new InternalServerError("Could not set index status to 'indexing' (status was " + newStatus + ")", 28);
			}
			FileUploadHandler.handleRequest(ds, request, "data", new UploadedFileTask() {
				@Override
				public void handle(FileItem fi) throws Exception {
					// Get the uploaded file parameters
					String fileName = fi.getName();

					IndexListener listener = new IndexListenerReportConsole() {
						@Override
						public synchronized boolean errorOccurred(String error,
								String unitType, File unit, File subunit) {
							indexError = error + " in " + unit +
									(subunit == null ? "" : " (" + subunit + ")");
							super.errorOccurred(error, unitType, unit, subunit);
							return false; // Don't continue indexing
						}
					};

					// TODO: do this in the background
					// TODO: lock the index while indexing
					// TODO: re-open Searcher after indexing
					// TODO: keep track of progress
					// TODO: error handling
					IndexTask task = new IndexTask(indexDir, fi.getInputStream(), fileName, listener);

					task.run();
					if (task.getIndexError() != null) {
						throw new InternalServerError(task.getIndexError(), 30);
					}


					//searchMan.addIndexTask(indexName, new IndexTask(is, fileName));
				}
			});
		} finally {
			indexMan.setIndexStatus(indexName, null, IndexStatus.AVAILABLE);
		}

		if (indexError != null)
			throw new BadRequest("INDEX_ERROR", "An error occurred during indexing. (error text: " + indexError + ")");
		return Response.success(ds, "Data added succesfully.");
	}

}
