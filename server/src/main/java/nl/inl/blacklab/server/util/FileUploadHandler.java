package nl.inl.blacklab.server.util;

import java.io.File;
import java.util.Iterator;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadBase;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;

import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.InternalServerError;

public class FileUploadHandler {

	public static abstract class UploadedFileTask {
		public abstract void handle(FileItem fi) throws Exception;
	}

	private static final long MAX_UPLOAD_SIZE = 25 * 1024 * 1024;

	private static final int MAX_MEM_UPLOAD_SIZE = 5 * 1024 * 1024;

	private static final File TMP_DIR = new File(System.getProperty("java.io.tmpdir"));

	public static void handleRequest(DataStream ds, HttpServletRequest request, String fieldName, UploadedFileTask task) throws BlsException {
		// Check that we have a file upload request
		boolean isMultipart = ServletFileUpload.isMultipartContent(request);
		if (!isMultipart) {
			throw new BadRequest("NO_FILE", "Upload a file to add to the index.");
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
				throw new BadRequest("ERROR_UPLOADING_FILE", "File too large (maximum " + MAX_UPLOAD_SIZE / 1024 / 1024 + " MB)");
			} catch (FileUploadException e) {
				throw new BadRequest("ERROR_UPLOADING_FILE", e.getMessage());
			}

			// Process the uploaded file items
			Iterator<FileItem> i = fileItems.iterator();

			int filesDone = 0;
			while (i.hasNext()) {
				FileItem fi = i.next();
				if (!fi.isFormField()) {

					if (filesDone != 0)
						throw new InternalServerError("Tried to upload more than one file.", 14);

					if (!fi.getFieldName().equals(fieldName)) {
						throw new BadRequest("CANNOT_UPLOAD_FILE", "Cannot upload file. File should be uploaded using the '" + fieldName + "' field.");
					}

					if (fi.getSize() > MAX_UPLOAD_SIZE) {
						throw new BadRequest("CANNOT_UPLOAD_FILE", "Cannot upload file. It is larger than the maximum of " + (MAX_UPLOAD_SIZE / 1024 / 1024) + " MB.");
					}

					task.handle(fi);

					filesDone++;
				}
			}
		} catch (BlsException ex) {
			throw ex;
		} catch (Exception ex) {
			throw new InternalServerError(ex.getMessage(), 26);
		}
	}
}