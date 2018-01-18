package nl.inl.blacklab.server.util;

import java.io.File;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadBase;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;

import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.InternalServerError;

public class FileUploadHandler {

	private static final long MAX_UPLOAD_SIZE = 25 * 1024 * 1024;

	private static final int MAX_MEM_UPLOAD_SIZE = 5 * 1024 * 1024;

	private static final File TMP_DIR = new File(System.getProperty("java.io.tmpdir"));

	/**
	 * Parse and validate the uploaded file(s).
	 *
	 * Only one file may be uploaded at a time, and it must be uploaded using fieldName.
	 *
	 * @param request
	 * @param fieldName
	 * @return the file
	 * @throws BlsException on invalid upload parameter/missing file/more than one file/too large file/general IO errors
	 */
	public static FileItem getFile(HttpServletRequest request, String fieldName) throws BlsException {
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
			List<FileItem> items = upload.parseRequest(request);
			if (items.size() != 1)
				throw new InternalServerError("Can only upload 1 file at a time.", 14);

			FileItem item = items.get(0);
			if (item.isFormField())
				throw new BadRequest("CANNOT_UPLOAD_FILE", "File must be uploaded as a form field.");

			if (!item.getFieldName().equals(fieldName))
				throw new BadRequest("CANNOT_UPLOAD_FILE", "Cannot upload file. File should be uploaded using the '" + fieldName + "' field.");

			// TODO uploaded file returns -1 here? Needs some more investigation
			if (item.getSize() > MAX_UPLOAD_SIZE)
				throw new BadRequest("CANNOT_UPLOAD_FILE", "Cannot upload file. It is larger than the maximum of " + (MAX_UPLOAD_SIZE / 1024 / 1024) + " MB.");

			return item;
		} catch (FileUploadBase.SizeLimitExceededException e) {
			throw new BadRequest("ERROR_UPLOADING_FILE", "File too large (maximum " + MAX_UPLOAD_SIZE / 1024 / 1024 + " MB)");
		} catch (FileUploadException e) {
			throw new BadRequest("ERROR_UPLOADING_FILE", e.getMessage());
		}
	}
}