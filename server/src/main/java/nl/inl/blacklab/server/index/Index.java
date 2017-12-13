package nl.inl.blacklab.server.index;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Comparator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.index.DocIndexerFactory;
import nl.inl.blacklab.index.Indexer;
import nl.inl.blacklab.search.Searcher;
import nl.inl.blacklab.search.indexstructure.IndexStructure;
import nl.inl.blacklab.server.exceptions.IllegalIndexName;
import nl.inl.blacklab.server.exceptions.InternalServerError;
import nl.inl.blacklab.server.exceptions.ServiceUnavailable;
import nl.inl.blacklab.server.search.SearchCache;

/**
 * A wrapper of sorts around {@link Searcher}, which is the main blacklab-core interface to an index.
 *
 * This is the main class used to interface with a corpus/index in Blacklab-Server.
 * Note the difference between an Index, which is a searchable collection of documents, and the _act_ of Indexing, adding new data to an Index.
 * Blacklab-Server manages indices centrally using the {@link IndexManager}. These handles are managed through this Index class.
 * An instance of Index should not be kept for long periods of time.
 */
public class Index {
	public enum IndexStatus {
		EMPTY,       // index has just been created. can be added to but not searched.
		AVAILABLE,   // index is available for searching and adding to
		INDEXING,    // index is busy, files are being added to it
		@Deprecated
		OPENING;     // index is being opened, will never be returned as getStatus blocks while the index is being opened

		@Override
		public String toString() {
			return name().toLowerCase();
		}
	}

	/**
	 * Sort all public indices first, then sort alphabetically within all public and private indices.
	 */
	public static final Comparator<Index> COMPARATOR = new Comparator<Index>() {
		@Override
		public int compare(Index o1, Index o2) {
			// Sort public before private
			boolean o1priv = o1.isUserIndex();
			boolean o2priv = o2.isUserIndex();
			if (o1priv != o2priv)
				return o1priv ? 1 : -1;

			// Sort rest case-insensitively
			return o1.getId().toLowerCase().compareTo(o2.getId().toLowerCase());
		}
	};

	private static final Logger logger = LogManager.getLogger(Index.class);

	private final String id;
	private final File dir;
	private SearchCache cache;

	/**
	 * Only one of these can be set at a time.
	 * The searcher is closed and cleared when an indexer is requested. Running searches are cancelled when this happens.
	 * The Indexer is cleared the first time a search is started after it the Indexer has finished indexing (meaning close() has been called on it).
	 * In addition, while an index is still running, no new Indexers can be created.
	 */
	private Searcher searcher;
	private Indexer indexer;

	/**
	 *
	 * @param indexId name of this index, including any username if this is a user index
	 * @param dir directory of this index
	 * @param cache
	 * @throws IllegalIndexName
	 * @throws FileNotFoundException
	 */
	public Index(String indexId, File dir, SearchCache cache) throws IllegalIndexName, FileNotFoundException {
		if (!isValidIndexName(indexId))
			throw new IllegalIndexName(indexId);
		if (dir == null || !dir.exists() || !dir.isDirectory())
			throw new FileNotFoundException("Cannot find index directory " + dir + ".");
		if (!dir.canRead() || !Searcher.isIndex(dir))
			throw new FileNotFoundException("Index directory " + dir + " is not an index or cannot be read.");

		this.id = indexId;
		this.dir = dir;
		this.cache = cache;

		// Opened on-demand
		this.searcher = null;
		this.indexer = null;
	}


	public String getId() {
		return id;
	}

	public File getDir() {
		return dir;
	}

	/**
	 * Get the current Searcher backing this Index.
	 * This is not available while this index is indexing new data.
	 *
	 * @return the currently opened Searcher
	 * @throws InternalServerError when there was an error opening this index
	 * @throws ServiceUnavailable when the index is in use.
	 */
	// TODO searcher should not have references to it held for longer times outside of this class
	// (this is a large job)
	public synchronized Searcher getSearcher() throws InternalServerError, ServiceUnavailable {
		openForSearching();
		return searcher;
	}

	/*
	 * TODO this whole function is a little iffy...
	 * What's to stop someone from doing Index.getSearcher().getIndexStructure(), which is wrong when the Index is currently busy indexing
	 */
	public synchronized IndexStructure getIndexStructure() throws InternalServerError {
		try {
			openForSearching();
		}
		catch (ServiceUnavailable e) {
			// swallow, we're apparently still busy indexing something,
			// this isn't a problem, we'll just use the indexer's searcher to get the structure
		} catch (InternalServerError e) {
			// Rethrow here is on purpose
			// this means there is something wrong
			throw e;
		}


		if (this.searcher != null)
			return this.searcher.getIndexStructure();
		else if (this.indexer != null)
			return this.indexer.getSearcher().getIndexStructure();

		// This should literally never happen, after openForSearching either searcher or indexer must be set
		throw new RuntimeException("Index in invalid state, openForSearching didn't throw unrecoverable error yet there is no Searcher and no Indexer");
	}


	public synchronized IndexStatus getStatus() {
		if (this.indexer != null && this.indexer.getListener().getIndexTime() == 0)
			return IndexStatus.INDEXING;
		else if (this.searcher != null && this.searcher.isEmpty())
			return IndexStatus.EMPTY;
		else
			return IndexStatus.AVAILABLE; // we're available even when searcher == null since we open on-demand.
	}

	//----------------------

	/**
	 * Attempt to open this index in search mode.
	 * If this Index currently has an open Indexer, checks whether the Indexer has finished (i.e. Indexer.close() has been called), and cleans it up if so.
	 *
	 * @throws InternalServerError if the index could not be opened due to currently ongoing indexing
	 * @throws ServiceUnavailable
	 */
	private synchronized void openForSearching() throws InternalServerError, ServiceUnavailable {
		tryCleanupCurrentIndexer();

		if (this.searcher != null)
			return;

		try {
			logger.debug("Opening index '" + id + "', dir = " + dir);
			searcher = Searcher.open(this.dir);
		} catch (Exception e) {
			this.searcher = null;

			throw new InternalServerError("Could not open index '" + id + "'", 27, e);
		}
	}

	/**
	 * Get an Indexer that can be used to add new data to this Index.
	 * Only one indexer may be obtained at a time, meaning until the previous indexer can be/has been cleaned up, ServiceUnavailable will be thrown.
	 * It is up to the user to close the returned Indexer.
	 *
	 * Note that this will lock this index for searching until the Indexer has been closed again.
	 * @param getCurrentIndexer get the current indexer instead of trying to allocate a new indexer, note that this might return a closed indexer
	 *
	 * @return the indexer
	 * @throws InternalServerError when the index cannot be opened for some reason
	 * @throws ServiceUnavailable when there is already an Indexer on this Index that's still processing
	 */
	// TODO getCurrentIndexer is a bit meh, but required for when you need to know the current state of the indexer
	public synchronized Indexer getIndexer(boolean getCurrentIndexer) throws InternalServerError, ServiceUnavailable {
		if (getCurrentIndexer) {
			try {
				// Clear our current indexer if it's finished the indexing job
				tryCleanupCurrentIndexer();
			} catch (ServiceUnavailable e) {
				// swallow, the current indexer has not finished yet
			}

			return indexer;
		}

		tryCleanupCurrentIndexer();
		close(); // Close any Searcher that is still in search mode
		try {
			this.indexer = new Indexer(this.dir, false, (DocIndexerFactory)null, (File)null);
			indexer.setUseThreads(true);
		} catch (Exception e) {
			throw new InternalServerError("Could not open index '" + id + "'", 27, e);
		}

		return indexer;
	}

	/**
	 * Close this index if it's currently open. Force closes any current Indexer.
	 * Has no effect if the index was already closed.
	 */
	public synchronized void close() {
		if (this.searcher != null) {
			this.searcher.close();
			this.searcher = null;
		}

		// if we're currently indexing, force close the indexer
		if (this.indexer != null && this.indexer.getListener().getIndexTime() == 0) {
			this.indexer.close();
		}

		this.indexer = null;

		cache.clearCacheForIndex(this.id);
	}

	/**
	 * Clean up the current Indexer (if any), provided close() has been called on the Indexer.
	 * NOTE: we do not close the indexer ourselves on purpose (except when this.close() is called), instead we using an IndexListener to know when an external user of the Indexer closes it.
	 *
	 * @throws ServiceUnavailable when the current indexer is still indexing
	 */
	private synchronized void tryCleanupCurrentIndexer() throws ServiceUnavailable {
		if (this.indexer == null)
			return;

		if (this.indexer.getListener().getIndexTime() == 0) // close() has not yet been called on the Indexer
			throw new ServiceUnavailable("Index '"+id+"' is currently indexing a file, please try again later.");

		// close() was already called on the indexer externally
		this.indexer = null;
	}

	// TODO pidfield is now gotten from blacklab itself, check how it's determined

	// If indexPid is null it should use the lucene docId, where is this configured again
	// seems setting the indexPid using Searcher._setPid was deprecated, so it should come from the import format?
	// Figure out the pid from the index metadata and/or BLS config.
//		String indexPid = searcher.getIndexStructure().pidField();
//		if (indexPid == null || indexPid.isEmpty())
//			indexPid = "";
//		String configPid = par.getPidField();
//		if (indexPid.length() > 0 && !configPid.equals(indexPid)) {
//
//
//		} else {
//			// No pid configured in index, only in blacklab-server.json. We want
//			// to get rid
//			// of this (prints an error on startup), but it should still work
//			// for now. Inject
//			// the setting into the searcher.
//			if (configPid.length() > 0)
//				searcher.getIndexStructure()._setPidField(configPid);
//		}
//		if (indexPid.length() == 0 && configPid.length() == 0) {
//			logger.warn("No pid given for index '" + indexName
//					+ "'; using Lucene doc ids.");
//		}


	//---------------------

	/**
	 * Check the index name part (not the user id part, if any) of the specified index name.
	 * Both indexName and indexIds may be used with this function.
	 *
	 * @param indexId the index id, possibly including user id prefix
	 * @return whether or not the index name part is valid
	 */
	public static boolean isValidIndexName(String indexId) {
		try {
			return getIndexName(indexId) != null;
		} catch (IllegalIndexName e) {
			return false;
		}
	}

	/**
	 * Check if this indexId is owned by a user
	 *
	 * @param indexId
	 * @return true if this index is owned by a user
	 */
	public static boolean isUserIndex(String indexId) {
		try {
		 	return getUserId(indexId) != null;
		} catch (IllegalIndexName e) {
			return false;
		}
	}

	/**
	 * Get the full id of this index, if this is a user index this will also contain the username.
	 * This function is a convenience that will not throw any exceptions, as the id of this index has already been validated.
	 *
	 * @return the full id of this index.
	 */
	public boolean isUserIndex() {
		return id.indexOf(":") != -1;
	}

	/**
	 * Get the user that owns this index. Returns null if this is not a user index.
	 *
	 * @param indexId
	 * @return the username or null if this is not a user index.
	 * @throws IllegalIndexName
	 */
	public static String getUserId(String indexId) throws IllegalIndexName {
		if (indexId == null)
			return null;

		getIndexName(indexId); // throws if our indexId is invalid

		int pos = indexId.indexOf(":");
		return pos != -1 ? indexId.substring(0, pos) : null;
	}

	/**
	 * Get the name of the user that owns this index (if any).
	 * This function is a convenience that will not throw any exceptions, as the id of this index has already been validated.
	 *
	 * @return the username
	 */
	public String getUserId() {
		int pos = id.indexOf(":");
		return pos != -1 ? id.substring(0, pos) : null;
	}

	/**
	 * Get the name portion of the indexId.
	 *
	 * @param indexId
	 * @return the name of this index, or null when indexId is null
	 * @throws IllegalIndexName if indexId is not null, but no valid name could be extracted
	 */
	public static String getIndexName(String indexId) throws IllegalIndexName {
		if (indexId == null)
			return null;

		int pos = indexId.indexOf(":");
		String userName = (pos >= 1) ? indexId.substring(0, pos) : null; // only when there is at least 1 character in the username
		String indexName = userName == null ? indexId : indexId.substring(pos + 1); // when there is no username, use the whole id, avoid edge case with ":indexname" stripping the ":"

		// The reason we check this here is because otherwise isValidIndexName() with values containing multiple separators would return true
		// isValidIndexName("a:simple:example") --> getIndexName(...) --> isValidIndexName(simple:example) --> getIndexName(...) --> isValidIndexName(example) --> true
		if (!indexName.matches("[a-zA-Z][a-zA-Z0-9_\\-]*"))
			throw new IllegalIndexName("'" + indexName + "' is not allowed as index name.");

		return indexName;
	}

	/**
	 * Get the name portion of the indexId.
	 * This function is a convenience that will not throw any exceptions, as the id of this index has already been validated.
	 *
	 * @return the name of this index, never null.
	 */
	public String getIndexName() {
		return isUserIndex() ? id.substring(id.indexOf(":" + 1)) : null;
	}


	/**
	 * Given the base name for an index and a userId, get the corresponding unique indexId for the index with that name for that user.
	 *
	 * @param indexName base name of the index
	 * @param userId may not contain ":"
	 * @return the id that unique identifies the index with indexName owned by the given user
	 * @throws IllegalIndexName whent he result would be an illegal name
	 */
	public static String getIndexId(String indexName, String userId) throws IllegalIndexName {
		String indexId = userId + ":" + indexName;

		if (!isValidIndexName(indexId))
			throw new IllegalIndexName(indexId + " is not allowed as index name.");

		return indexId;
	}

	//------------------------

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Index && ((Index) obj).getId().equals(this.getId())) {
			if (!((Index)obj).getDir().equals(this.getDir()))
				throw new RuntimeException("Index has same id but different directory");

			return true;
		}

		return false;
	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}

	@Override
	public String toString() {
		return id;
	}
}
