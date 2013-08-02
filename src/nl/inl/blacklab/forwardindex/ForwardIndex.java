package nl.inl.blacklab.forwardindex;

import java.io.File;
import java.text.Collator;
import java.util.List;

import nl.inl.util.VersionFile;

import org.apache.lucene.index.IndexReader;

public abstract class ForwardIndex {

	/*
	 * File format version history:
	 * 1. Initial version.
	 * 2. Added sort index to terms file.
	 * 3. New terms and docs file format; added reverse sort index and case-insensitive index to terms file.
	 */

	/**
	 * Indicate how to translate Lucene document ids to forward index ids
	 * (by looking them up in the index).
	 *
	 * Caches the forward index id field.
	 *
	 * @param reader the index
	 * @param lucenePropFieldName the forward index if field
	 */
	public abstract void setIdTranslateInfo(IndexReader reader, String lucenePropFieldName);

	public abstract int luceneDocIdToFiid(int docId);

	/**
	 * Perform a series of reads on the forward index to get (most of) it into disk cache.
	 */
	public abstract void warmUp();

	/**
	 * Close the forward index.
	 * Writes the table of contents to disk if modified.
	 */
	public abstract void close();

	/**
	 * Store the given content and assign an id to it.
	 *
	 * Note that if more than one token occurs at any position, we only store the first in the
	 * forward index.
	 *
	 * @param content
	 *            the content to store
	 * @param posIncr the associated position increments, or null if position increment is always 1.
	 * @return the id assigned to the content
	 */
	public abstract int addDocument(List<String> content, List<Integer> posIncr);

	/**
	 * Store the given content and assign an id to it
	 *
	 * @param content
	 *            the content to store
	 * @return the id assigned to the content
	 */
	public abstract int addDocument(List<String> content);

	/**
	 * Retrieve one or more substrings from the specified content.
	 *
	 * This is more efficient than retrieving the whole content, or retrieving parts in separate
	 * calls, because the file is only opened once and random access is used to read only the
	 * required parts.
	 *
	 * NOTE: if offset and length are both -1, retrieves the whole content. This is used by the
	 * retrieve(id) method.
	 *
	 * NOTE2: Mapped file IO on Windows has some issues that sometimes cause an OutOfMemoryError on
	 * the FileChannel.map() call (which makes no sense, because memory mapping only uses address
	 * space, it doesn't try to read the whole file). Possibly this could be solved by using 64-bit
	 * Java, but we haven't tried. For now we just disable memory mapping on Windows.
	 *
	 * @param fiid
	 *            forward index document id
	 * @param start
	 *            the starting points of the substrings (in words)
	 * @param end
	 *            the end points of the substrings (in words)
	 * @return the parts
	 * @deprecated use retrievePartsInt and getTerms().get(id)
	 */
	@Deprecated
	public abstract List<String[]> retrieveParts(int fiid, int[] start, int[] end);

	/**
	 * Retrieve one or more parts from the specified content, in the form of token sort order ids.
	 *
	 * This is more efficient than retrieving the whole content, or retrieving parts in separate
	 * calls, because the file is only opened once and random access is used to read only the
	 * required parts.
	 *
	 * @param fiid
	 *            forward index document id
	 * @param start
	 *            the starting points of the parts to retrieve (in words)
	 * @param end
	 *            the end points of the parts to retrieve (in words)
	 * @param sensitive
	 *            whether to get the case-sensitive sort order or not
	 * @return the parts
	 * @deprecated
	 */
	@Deprecated
	public abstract List<int[]> retrievePartsSortOrder(int fiid, int[] start, int[] end, boolean sensitive);

	/**
	 * Retrieve one or more parts from the specified content, in the form of token ids.
	 *
	 * This is more efficient than retrieving the whole content, or retrieving parts in separate
	 * calls, because the file is only opened once and random access is used to read only the
	 * required parts.
	 *
	 * NOTE: if offset and length are both -1, retrieves the whole content. This is used by the
	 * retrieve(id) method.
	 *
	 * NOTE2: Mapped file IO on Windows has some issues that sometimes cause an OutOfMemoryError on
	 * the FileChannel.map() call (which makes no sense, because memory mapping only uses address
	 * space, it doesn't try to read the whole file). Possibly this could be solved by using 64-bit
	 * Java, but we haven't tried. For now we just disable memory mapping on Windows.
	 *
	 * @param fiid
	 *            forward index document id
	 * @param start
	 *            the starting points of the parts to retrieve (in words)
	 * @param end
	 *            the end points of the parts to retrieve (in words)
	 * @return the parts
	 */
	public abstract List<int[]> retrievePartsInt(int fiid, int[] start, int[] end);

	public abstract Terms getTerms();

	public abstract int getNumDocs();

	public abstract int getDocLength(int fiid);

	/**
	 * Current forward index format version
	 */
	private static final String CURRENT_VERSION = "3";

	public static ForwardIndex open(File dir) {
		return open(dir, false, null, false);
	}

	public static ForwardIndex open(File dir, boolean indexMode) {
		return open(dir, indexMode, null, false);
	}

	public static ForwardIndex open(File dir, boolean indexMode, Collator collator,
			boolean create) {

		if (!dir.exists()) {
			if (!create)
				throw new RuntimeException("ForwardIndex doesn't exist: " + dir);
			dir.mkdir();
		}

		// Version check
		boolean isVersion2 = false;
		if (!indexMode || !create) {
			// We're opening an existing forward index. Check version.
			if (!VersionFile.isTypeVersion(dir, "fi", CURRENT_VERSION)) {
				if (VersionFile.isTypeVersion(dir, "fi", "2")) {
					isVersion2 = true;
				} else {
					throw new RuntimeException("Not a forward index or wrong version: "
							+ VersionFile.report(dir) + " (fi " + CURRENT_VERSION + " expected)");
				}
			}
		} else {
			// We're creating a forward index. Write version.
			VersionFile.write(dir, "fi", CURRENT_VERSION);
		}

		if (isVersion2)
			return new ForwardIndexImplV2(dir, indexMode, collator, create);
		return new ForwardIndexImplV3(dir, indexMode, collator, create);
	}

	/**
	 * @param b true iff we want to keep FI in memory
	 * @deprecated keeping forward index in memory is 'never' a good idea (better to use OS caching)
	 */
	@Deprecated
	public static void setKeepInMemory(boolean b) {
		//
	}

	/**
	 * Sets the options for keeping forward indices in memory.
	 *
	 * Default and recommended setting is false, as it takes advantage of OS caching.
	 *
	 * @param keepInMemory if true, tries to keep the forward index in memory
	 * @param keepFree how much of the maximum heap to keep free (only used if keepInMemory == true)
	 * @deprecated keeping forward index in memory is 'never' a good idea (better to use OS caching)
	 */
	@Deprecated
	public static void setKeepInMemory(boolean keepInMemory, long keepFree) {
		//
	}

}
