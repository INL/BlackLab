package nl.inl.blacklab.server.requesthandlers;

import java.io.File;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.index.IndexListener;
import nl.inl.blacklab.index.IndexListenerReportConsole;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.IndexNotFound;
import nl.inl.blacklab.server.exceptions.NotAuthorized;
import nl.inl.blacklab.server.index.IndexTask;
import nl.inl.blacklab.server.jobs.User;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadBase;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;

/**
 * Display the contents of the cache.
 */
public class RequestHandlerAddToIndex extends RequestHandler {

	private static final long MAX_UPLOAD_SIZE = 25 * 1024 * 1024;

	private static final int MAX_MEM_UPLOAD_SIZE = 5 * 1024 * 1024;

	private static final File TMP_DIR = new File(System.getProperty("java.io.tmpdir"));

	String indexError = null;

	public RequestHandlerAddToIndex(BlackLabServer servlet,
			HttpServletRequest request, User user, String indexName,
			String urlResource, String urlPathPart) {
		super(servlet, request, user, indexName, urlResource, urlPathPart);
	}

	@Override
	public Response handle() throws BlsException {
		debug(logger, "REQ add data: " + indexName);

		if (!indexName.contains(":"))
			throw new NotAuthorized("Can only add to private indices.");
		if (!searchMan.indexExists(indexName))
			throw new IndexNotFound(indexName);

		String status = searchMan.getIndexStatus(indexName);
		if (!status.equals("available") && !status.equals("empty"))
			return Response.unavailable(indexName, status);

		// Check that we have a file upload request
		boolean isMultipart = ServletFileUpload.isMultipartContent(request);
		if (!isMultipart) {
			return Response.badRequest("NO_FILE", "Upload a file to add to the index.");
		}
		DiskFileItemFactory factory = new DiskFileItemFactory();

		// maximum size that will be stored in memory
		factory.setSizeThreshold(MAX_MEM_UPLOAD_SIZE);
		// Location to save data that is larger than maxMemSize.
		factory.setRepository(TMP_DIR);

		// Create a new file upload handler
		ServletFileUpload upload = new ServletFileUpload(factory);
		// maximum file size to be uploaded.
		upload.setSizeMax(MAX_UPLOAD_SIZE);

		try {
			// Parse the request to get file items.
			List<FileItem> fileItems;
			try {
				fileItems = upload.parseRequest(request);
			} catch (FileUploadBase.SizeLimitExceededException e) {
				return Response.badRequest("ERROR_UPLOADING_FILE", "File too large (maximum " + MAX_UPLOAD_SIZE / 1024 / 1024 + " MB)");
			} catch (FileUploadException e) {
				return Response.badRequest("ERROR_UPLOADING_FILE", e.getMessage());
			}

			// Process the uploaded file items
			Iterator<FileItem> i = fileItems.iterator();

			if (!searchMan.indexExists(indexName))
				return Response.indexNotFound(indexName);
			File indexDir = searchMan.getIndexDir(indexName);
			int filesDone = 0;
			String newStatus = searchMan.setIndexStatus(indexName, "available|empty", "busy");
			searchMan.closeSearcher(indexName);
			if (!newStatus.equals("busy")) {
				return Response.internalError("Could not set index status to busy (status was " + newStatus + ")", debugMode, 28);
			}
			try {
				while (i.hasNext()) {
					FileItem fi = i.next();
					if (!fi.isFormField()) {

						if (!fi.getFieldName().equals("data"))
							return Response.badRequest("CANNOT_UPLOAD_FILE", "Cannot upload file. File should be uploaded using the 'data' field.");

						if (fi.getSize() > MAX_UPLOAD_SIZE)
							return Response.badRequest("CANNOT_UPLOAD_FILE", "Cannot upload file. It is larger than the maximum of " + (MAX_UPLOAD_SIZE / 1024 / 1024) + " MB.");

						if (filesDone != 0)
							return Response.internalError("Tried to upload more than one file.", debugMode, 14);

						// Get the uploaded file parameters
						String fileName = fi.getName();

						File tmpFile = null;
						IndexTask task;
						IndexListener listener = new IndexListenerReportConsole() {
							@Override
							public boolean errorOccurred(String error,
									String unitType, File unit, File subunit) {
								indexError = error + " in " + unit +
										(subunit == null ? "" : " (" + subunit + ")");
								super.errorOccurred(error, unitType, unit, subunit);
								return false; // Don't continue indexing
							}
						};
						try {
							if (fileName.endsWith(".zip")) {
								// We can only index zip from a file, not from a stream.
								tmpFile = File.createTempFile("blsupload", ".tmp.zip");
								fi.write(tmpFile);
								task = new IndexTask(indexDir, tmpFile, fileName, listener);
							} else {
								InputStream data = fi.getInputStream();

								// TODO: do this in the background
								// TODO: lock the index while indexing
								// TODO: re-open Searcher after indexing
								// TODO: keep track of progress
								// TODO: error handling
								task = new IndexTask(indexDir, data, fileName, listener);
							}
							task.run();
							if (task.getIndexError() != null) {
								return Response.internalError(task.getIndexError(), true, 30);
							}
						} finally {
							if (tmpFile != null)
								tmpFile.delete();
						}

						//searchMan.addIndexTask(indexName, new IndexTask(is, fileName));

						filesDone++;
					}
				}
			} finally {
				searchMan.setIndexStatus(indexName, null, "available");
			}
		} catch (BlsException ex) {
			throw ex;
		} catch (Exception ex) {
			return Response.internalError(ex, debugMode, 26);
		}

		if (indexError != null)
			return Response.badRequest("INDEX_ERROR", "An error occurred during indexing. (error text: " + indexError + ")");
		return Response.success("Data added succesfully."); //Response.accepted();
	}
}
