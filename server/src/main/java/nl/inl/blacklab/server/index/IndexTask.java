package nl.inl.blacklab.server.index;

import java.io.File;
import java.io.InputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.index.DocIndexerFactory;
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

	private String fileName;

	private File indexDir;

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
		this.fileName = name;
		setListener(listener);
	}

	boolean anyDocsFound = false;

	private void setListener(IndexListener listener) {
		this.decoratedListener = new IndexListenerDecorator(listener) {

			@Override
			public synchronized boolean errorOccurred(String error, String unitType,
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
		    // Open the index, automatically detecting the document format it was created with.
			indexer = new Indexer(indexDir, false, (DocIndexerFactory)null, (File)null);

			IndexStructure indexStructure = indexer.getSearcher().getIndexStructure();
			if (indexStructure.getTokenCount() > MAX_TOKEN_COUNT) {
				throw new NotAuthorized("Sorry, this index is already larger than the maximum of " + MAX_TOKEN_COUNT + ". Cannot add any more data to it.");
			}

			indexer.setListener(decoratedListener);
			anyDocsFound = false;
			indexer.setContinueAfterInputError(false);
			indexer.setRethrowInputError(false);
			try {
				indexer.index(fileName, data);

				if (!anyDocsFound) {
					indexError = "The file contained no documents in the selected format. Do the corpus and file formats match?";
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
