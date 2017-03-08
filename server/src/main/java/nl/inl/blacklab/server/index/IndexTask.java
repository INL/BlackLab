package nl.inl.blacklab.server.index;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.index.DocIndexer;
import nl.inl.blacklab.index.DocumentFormats;
import nl.inl.blacklab.index.IndexListener;
import nl.inl.blacklab.index.IndexListenerDecorator;
import nl.inl.blacklab.index.Indexer;
import nl.inl.blacklab.search.indexstructure.IndexStructure;
import nl.inl.blacklab.server.exceptions.NotAuthorized;

public class IndexTask {

	private static final Logger logger = LogManager.getLogger(IndexTask.class);

	private static final long MAX_TOKEN_COUNT = 500000;

	/** The data we're indexing. We're responsible for closing the stream when
	 *  we're done with it. */
	private InputStream data;

	private String name;

	private File indexDir;

	private File dataFile;

	private IndexListener decoratedListener;

	String indexError = null;

	/**
	 * Construct a new SearchThread
	 * @param indexDir directory of index to add to
	 * @param data (XML) input data
	 * @param name (file) name for the input data
	 * @param listener the index listener to use
	 */
	public IndexTask(File indexDir, InputStream data, String name, IndexListener listener) {
		this.indexDir = indexDir;
		this.data = data;
		this.name = name;
		setListener(listener);
	}

	public IndexTask(File indexDir, File dataFile, String name, IndexListener listener) {
		this.indexDir = indexDir;
		this.dataFile = dataFile;
		this.name = name;
		setListener(listener);
	}

	boolean anyDocsFound = false;

	private void setListener(IndexListener listener) {
		this.decoratedListener = new IndexListenerDecorator(listener) {

			@Override
			public boolean errorOccurred(String error, String unitType,
					File unit, File subunit) {
				indexError = error;
				return super.errorOccurred(error, unitType, unit, subunit);
			}

			@Override
			public synchronized void documentStarted(String name) {
				super.documentStarted(name);
				anyDocsFound = true;
			}
		};
	}

	public void run() throws Exception {
		Indexer indexer = null;
		try {
			indexer = new Indexer(indexDir, false, null);

			// We created the Indexer with a null DocIndexer class.
			// Now we figure out what the indices' own document format is,
			// resolve it to a DocIndexer class and update the Indexer with it.
			IndexStructure indexStructure = indexer.getSearcher().getIndexStructure();
			if (indexStructure.getTokenCount() > MAX_TOKEN_COUNT) {
				throw new NotAuthorized("Sorry, this index is already larger than the maximum of " + MAX_TOKEN_COUNT + ". Cannot add any more data to it.");
			}
			String docFormat = indexStructure.getDocumentFormat();
			Class<? extends DocIndexer> docIndexerClass;
			docIndexerClass = DocumentFormats.getIndexerClass(docFormat);
			indexer.setDocIndexer(docIndexerClass);

			indexer.setListener(decoratedListener);
			anyDocsFound = false;
			indexer.setContinueAfterInputError(false);
			indexer.setRethrowInputError(false);
			try {
				if (data == null && dataFile != null) {
					// Used for zip files, possibly other types in the future.
					indexer.index(dataFile, "*.xml");
				} else if (name.endsWith(".tar.gz") || name.endsWith(".tgz")) {
					// Tar gzipped data; read directly from stream.
					indexer.indexTarGzip(name, data, "*.xml", true);
				} else if (name.endsWith(".gz")) {
					// Tar gzipped data; read directly from stream.
					indexer.indexGzip(name, data);
				} else {
					// Straight XML data. Read as UTF-8.
					try (Reader reader = new BufferedReader(new InputStreamReader(data, Indexer.DEFAULT_INPUT_ENCODING))) {
						logger.debug("Starting indexing");
						indexer.index(name, reader);
						logger.debug("Done indexing");
						if (!anyDocsFound) {
							indexError = "The file contained no documents in the selected format. Do the corpus and file formats match?";
						}
					}
				}
			} catch (Exception e) {
				logger.warn("An error occurred while indexing, rolling back changes: " + e.getMessage());
				indexer.rollback();
				indexer = null;
				throw e;
			} finally {
				if (indexError != null) {
					logger.warn("An error occurred while indexing, rolling back changes: " + indexError);
					if (indexer != null)
						indexer.rollback();
					indexer = null;
				} else {
					if (indexer != null)
						indexer.close();
					indexer = null;
				}
			}
		} finally {
			if (data != null)
				data.close();
			data = null;
		}
	}

	public String getIndexError() {
		return indexError;
	}
}
