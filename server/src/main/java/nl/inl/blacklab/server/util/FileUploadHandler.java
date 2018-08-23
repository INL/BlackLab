package nl.inl.blacklab.server.util;

import java.io.File;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadBase;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.FileUtils;

import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;

public class FileUploadHandler {

    private static final long MAX_UPLOAD_SIZE = FileUtils.ONE_GB * 2;

    private static final int MAX_MEM_UPLOAD_SIZE = (int) FileUtils.ONE_MB * 5;

    private static final File TMP_DIR = new File(System.getProperty("java.io.tmpdir"));

    /**
     * Parse and validate the uploaded file(s).
     *
     * Only one file may be uploaded at a time, and it must be uploaded using
     * fieldName.
     *
     * @param request
     * @return the file
     * @throws BlsException on invalid upload parameter/missing file/too large
     *             file/general IO errors
     */
    public static List<FileItem> getFiles(HttpServletRequest request) throws BlsException {
        // Check that we have a file upload request
        boolean isMultipart = ServletFileUpload.isMultipartContent(request);
        if (!isMultipart) {
            throw new BadRequest("NO_FILE", "No file(s) were uploaded");
        }
        DiskFileItemFactory factory = new DiskFileItemFactory();

        // maximum size that will be stored in memory
        factory.setSizeThreshold(MAX_MEM_UPLOAD_SIZE);
        // Location to save data that is larger than maxMemSize.
        factory.setRepository(TMP_DIR);

        // Create a new file upload handler
        ServletFileUpload upload = new ServletFileUpload(factory);
        // maximum file size to be uploaded. Intentionally accept a bit more than we actually support,
        // because when the SizeLimitExceededException is thrown, the request is aborted (from the browser), and user will never receive/see our response
        // See https://stackoverflow.com/questions/18367824/how-to-cancel-http-upload-from-data-events/18370751#18370751
        upload.setSizeMax(MAX_UPLOAD_SIZE * 2);

        try {
            // Parse the request to get file items.
            List<FileItem> items = upload.parseRequest(request);
            if (items.isEmpty())
                throw new BadRequest("NO_FILE", "No file(s) were uploaded");

            for (FileItem f : items) {
                if (f.isFormField())
                    throw new BadRequest("CANNOT_UPLOAD_FILE", "File must not be uploaded as a form field.");
                if (f.getSize() > MAX_UPLOAD_SIZE)
                    throw new BadRequest("CANNOT_UPLOAD_FILE", "Cannot upload file. It is larger than the maximum of "
                            + (MAX_UPLOAD_SIZE / 1024 / 1024) + " MB.");
            }
            return items;
        } catch (FileUploadBase.SizeLimitExceededException e) {
            throw new BadRequest("ERROR_UPLOADING_FILE",
                    "File too large (maximum " + MAX_UPLOAD_SIZE / 1024 / 1024 + " MB)");
        } catch (FileUploadException e) {
            throw new BadRequest("ERROR_UPLOADING_FILE", e.getMessage());
        }
    }
}
