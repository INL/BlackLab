package nl.inl.blacklab.search.fimatch;

import org.apache.lucene.index.LeafReader;

import nl.inl.blacklab.search.Searcher;

/**
 * Maps property name to property number, term string to term number,
 * and allows you to instantiante a token source for matching from a
 * certain position in a document.
 */
public abstract class TokenPropMapper {

	public static TokenPropMapper fromSearcher(Searcher searcher, String searchField) {
		return new TokenPropMapperImpl(searcher, searchField);
	}

	public TokenPropMapper() {
		super();
	}

	/**
	 * Get the index number corresponding to the given property name.
	 *
	 * @param propertyName property to get the index for
	 * @return index for this property
	 */
	public abstract int getPropertyNumber(String propertyName);

	/**
	 * Get the term number for a given term string.
	 *
	 * @param propertyNumber which property to get term number for
	 * @param propertyValue which term string to get term number for
	 * @return term number for this term in this property
	 *
	 */
	public abstract int getTermNumber(int propertyNumber, String propertyValue);

	/**
	 * Get the number of properties
	 * @return number of properties
	 */
	public abstract int numberOfProperties();

	/**
	 * Get a PropMapper object specific to this index reader.
	 * 
	 * @param reader index reader
	 * @return reader-specific PropMapper object
	 */
	public abstract ReaderTokenPropMapper getReaderTokenPropMapper(LeafReader reader);
	
	public abstract class ReaderTokenPropMapper {
		
		protected LeafReader reader;
		
		ReaderTokenPropMapper(LeafReader reader) {
			this.reader = reader;
		}
		
		/**
		 * Get a token source, which we can use to get tokens from a document 
		 * for different properties.
		 *  
		 * @param docId Lucene document id
		 * @param reader the index reader
		 * @return the token source
		 */
		public abstract TokenSource tokenSource(int id);

		public abstract int getDocLength(int docId);

		public abstract int[] getChunk(int propIndex, int docId, int start, int end);

		public abstract int getFiid(int propIndex, int docId);

		public int numberOfProperties() {
			return TokenPropMapper.this.numberOfProperties();
		}

	}


}
