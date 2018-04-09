package nl.inl.blacklab.server.requesthandlers;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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

		Index index = indexMan.getIndex(indexName);
		IndexStructure indexStructure = index.getIndexStructure();

		if (!index.isUserIndex() || !index.getUserId().equals(user.getUserId()))
			throw new NotAuthorized("You can only add new data to your own private indices.");

		if (indexStructure.getTokenCount() > MAX_TOKEN_COUNT) {
			throw new NotAuthorized("Sorry, this index is already larger than the maximum of " + MAX_TOKEN_COUNT + " tokens. Cannot add any more data to it.");
		}

		List<FileItem> dataFiles = new ArrayList<>();
		Map<String, File> metadataFiles = new HashMap<>();
		try {
			for (FileItem f : FileUploadHandler.getFiles(request)) {
				switch (f.getFieldName()) {
				case "data":
				case "data[]":
					dataFiles.add(f);
					break;
				case "metadata":
				case "metadata[]":
					String fileNameOnly = FilenameUtils.getName(f.getName());
					File temp = Files.createTempFile("", fileNameOnly).toFile();
					temp.deleteOnExit();

					try (OutputStream tempOut = new FileOutputStream(temp)) {
						IOUtils.copy(f.getInputStream(), tempOut);
					}
					metadataFiles.put(fileNameOnly.toLowerCase(), temp);
					break;
				}
			}
		} catch (IOException e) {
			throw new InternalServerError("Error occured during indexing: " + e.getMessage(), 41);
		}

		Indexer indexer = index.getIndexer();
		indexer.setListener(new IndexListenerReportConsole() {
			@Override
			public boolean errorOccurred(Throwable e, String path, File f) {
				super.errorOccurred(e, path, f);
				indexError = e.getMessage() + " in " + path;
				return false; // Don't continue indexing
			}
		});

		indexer.setLinkedFileResolver(fileName ->
			metadataFiles.get(FilenameUtils.getName(fileName).toLowerCase())
		);

		for (FileItem file : dataFiles) {
			try (InputStream is = file.getInputStream()) {
				indexer.index(file.getName(), is/*, "*.xml"*/);
				if (indexError == null) {
					if (indexer.getListener().getFilesProcessed() == 0)
						indexError = "No files were found when indexing.";
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
		}

		if (indexError != null)
			throw new BadRequest("INDEX_ERROR", "An error occurred during indexing. (error text: " + indexError + ")");
		return Response.success(ds, "Data added succesfully.");
	}

}
