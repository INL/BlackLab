package nl.inl.blacklab.search.fimatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.index.LeafReader;

import nl.inl.blacklab.forwardindex.ForwardIndex;
import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.search.Searcher;
import nl.inl.blacklab.search.lucene.DocIntFieldGetter;

/**
 * Allows the forward index matching subsystem to access the forward indices,
 * including an easy and fast way to read any property at any position from a document.
 */
class ForwardIndexAccessorImpl extends ForwardIndexAccessor {

	/** Our Searcher object */
	private Searcher searcher;

	/** Field name, e.g. "contents" */
	String complexFieldBaseName;

	/** The property index for each property name */
	private Map<String, Integer> propertyNumbers = new HashMap<>();

	/** The property names for each property */
	List<String> propertyNames = new ArrayList<>();

	/** The forward index for each property */
	List<ForwardIndex> fis = new ArrayList<>();

	/** The terms object for each property */
	private List<Terms> terms = new ArrayList<>();

	ForwardIndexAccessorImpl(Searcher searcher, String searchField) {
		this.searcher = searcher;
		this.complexFieldBaseName = searchField;
	}

	/**
	 * Get the index number corresponding to the given property name.
	 *
	 * @param propertyName property to get the index for
	 * @return index for this property
	 */
	@Override
	public int getPropertyNumber(String propertyName) {
		Integer n = propertyNumbers.get(propertyName);
		if (n == null) {
			// Assign number and store reference to forward index
			n = propertyNumbers.size();
			propertyNumbers.put(propertyName, n);
			propertyNames.add(propertyName);
			ForwardIndex fi = searcher.getForwardIndex(ComplexFieldUtil.propertyField(complexFieldBaseName, propertyName));
			fis.add(fi);
			terms.add(fi.getTerms());
		}
		return n;
	}

	@Override
	public int getTermNumber(int propertyNumber, String propertyValue) {
		return terms.get(propertyNumber).indexOf(propertyValue);
	}

	public int getTermAtPosition(int fiid, int propertyNumber, int pos) {
		return fis.get(propertyNumber).getToken(fiid, pos);
	}

	@Override
	public int numberOfProperties() {
		return fis.size();
	}

	@Override
	public ForwardIndexAccessorLeafReader getForwardIndexAccessorLeafReader(LeafReader reader) {
		return new ForwardIndexAccessorLeafReaderImpl(reader);
	}
	
	class ForwardIndexAccessorLeafReaderImpl extends ForwardIndexAccessorLeafReader {
		
		private List<DocIntFieldGetter> fiidGetters;

		ForwardIndexAccessorLeafReaderImpl(LeafReader reader) {
			super(reader);
			fiidGetters = new ArrayList<>();
			for (int i = 0; i < getNumberOfProperties(); i++)
				fiidGetters.add(null);
		}
		
		DocIntFieldGetter fiidGetter(int propIndex) {
			DocIntFieldGetter g = fiidGetters.get(propIndex);
			if (g == null) {
				String propertyName = propertyNames.get(propIndex);
				String propFieldName = ComplexFieldUtil.propertyField(complexFieldBaseName, propertyName);
				String fiidFieldName = ComplexFieldUtil.forwardIndexIdField(propFieldName);
				g = new DocIntFieldGetter(reader, fiidFieldName);
				fiidGetters.set(propIndex, g);
			}
			return g;
		}
		
		/**
		 * Get a token source, which we can use to get tokens from a document 
		 * for different properties.
		 *  
		 * @param docId Lucene document id
		 * @param reader the index reader
		 * @return the token source
		 */
		@Override
		public ForwardIndexDocument getForwardIndexDoc(int id) {
			return new ForwardIndexDocumentImpl(this, id, reader);
		}

		@Override
		public int getDocLength(int docId) {
			return fis.get(0).getDocLength(getFiid(0, docId));
		}

		int[] starts = {0}, ends = {0};
		
		@Override
		public int[] getChunk(int propIndex, int docId, int start, int end) {
			starts[0] = start;
			ends[0] = end;
			int fiid = fiidGetter(propIndex).getFieldValue(docId); 
			return fis.get(propIndex).retrievePartsInt(fiid, starts, ends).get(0);
		}

		@Override
		public int getFiid(int propIndex, int docId) {
			return fiidGetter(propIndex).getFieldValue(docId);
		}

		@Override
		public int getNumberOfProperties() {
			return ForwardIndexAccessorImpl.this.numberOfProperties();
		}

	}

}
