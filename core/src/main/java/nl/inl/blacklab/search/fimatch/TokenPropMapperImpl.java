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
 * Maps property name to property number, term string to term number,
 * and allows you to instantiante a token source for matching from a
 * certain position in a document.
 */
class TokenPropMapperImpl extends TokenPropMapper {

	private Searcher searcher;

	private String complexFieldBaseName;

	private Map<String, Integer> propertyNumbers = new HashMap<>();

	private List<String> propertyNames = new ArrayList<>();

	private List<Terms> terms = new ArrayList<>();

	private List<ForwardIndex> fis = new ArrayList<>();

	TokenPropMapperImpl(Searcher searcher, String searchField) {
		this.searcher = searcher;
		this.complexFieldBaseName = searchField;
	}

	/**
	 * Get the index number corresponding to the given property name.
	 *
	 * @param propertyName property to get the index for
	 * @return index for this property
	 */
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
	public ReaderTokenPropMapper getReaderTokenPropMapper(LeafReader reader) {
		return new ReaderTokenPropMapperImpl(reader);
	}
	
	class ReaderTokenPropMapperImpl extends ReaderTokenPropMapper {
		
		private List<DocIntFieldGetter> fiidGetters;

		ReaderTokenPropMapperImpl(LeafReader reader) {
			super(reader);
			fiidGetters = new ArrayList<>();
			for (int i = 0; i < numberOfProperties(); i++)
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
		public TokenSource tokenSource(int id) {
			return new TokenSourceImpl(this, id, reader);
		}

		public int getDocLength(int docId) {
			return fis.get(0).getDocLength(getFiid(0, docId));
		}

		int[] starts = {0}, ends = {0};
		
		public int[] getChunk(int propIndex, int docId, int start, int end) {
			starts[0] = start;
			ends[0] = end;
			int fiid = fiidGetter(propIndex).getFieldValue(docId); 
			return fis.get(propIndex).retrievePartsInt(fiid, starts, ends).get(0);
		}

		public int getFiid(int propIndex, int docId) {
			return fiidGetter(propIndex).getFieldValue(docId);
		}

		public int numberOfProperties() {
			return TokenPropMapperImpl.this.numberOfProperties();
		}

	}

}
