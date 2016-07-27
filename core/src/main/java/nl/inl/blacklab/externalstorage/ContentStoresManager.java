package nl.inl.blacklab.externalstorage;

import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.document.Document;

import nl.inl.blacklab.search.ContentAccessor;

public class ContentStoresManager {
	/**
	 * ContentAccessors tell us how to get a field's content:
	 * <ol>
	 * <li>if there is no contentaccessor: get it from the Lucene index (stored field)</li>
	 * <li>from an external source (file, database) if it's not (because the content is very large
	 * and/or we want faster random access to the content than a stored field can provide)</li>
	 * </ol>
	 *
	 * Indexed by complex field name.
	 */
	private Map<String, ContentAccessor> contentAccessors = new HashMap<>();

	public void close() {
		// Close the content accessor(s)
		// (the ContentStore, and possibly other content accessors
		// (although that feature is not used right now))
		for (ContentAccessor ca: contentAccessors.values()) {
			ca.close();
		}

	}

	public void put(String field, ContentStore store) {
		contentAccessors.put(field, new ContentAccessor(field, store));
	}

	public ContentStore get(String fieldName) {
		ContentAccessor ca = contentAccessors.get(fieldName);
		if (ca == null)
			return null;
		return ca.getContentStore();
	}

	public void deleteDocument(Document d) {
		for (ContentAccessor ca: contentAccessors.values()) {
			ca.delete(d);
		}
	}

	public boolean exists(String fieldName) {
		return contentAccessors.containsKey(fieldName);
	}

	public String[] getSubstrings(String fieldName, Document d, int[] start, int[] end) {
		ContentAccessor contentAccessor = contentAccessors.get(fieldName);
		if (contentAccessor == null)
			return null;
		return contentAccessor.getSubstringsFromDocument(d, start, end);
	}

	public String[] getSubstrings(String fieldName, int contentId, int[] start, int[] end) {
		ContentAccessor contentAccessor = contentAccessors.get(fieldName);
		if (contentAccessor == null)
			return null;
		return contentAccessor.getSubstringsFromDocument(contentId, start, end);
	}


}
