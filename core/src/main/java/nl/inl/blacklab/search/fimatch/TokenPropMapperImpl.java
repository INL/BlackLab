package nl.inl.blacklab.search.fimatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nl.inl.blacklab.forwardindex.ForwardIndex;
import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.search.Searcher;

/**
 * Maps property name to property number, term string to term number,
 * and allows you to instantiante a token source for matching from a
 * certain position in a document.
 */
class TokenPropMapperImpl extends TokenPropMapper {

	private Searcher searcher;

	private String complexFieldBaseName;

	private Map<String, Integer> propertyNumbers = new HashMap<>();

	private List<Terms> terms = new ArrayList<>();

	private List<ForwardIndex> fis = new ArrayList<>();

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
	public TokenSource tokenSource(int fiid, int startingPosition, int direction) {
		return new TokenSourceImpl(fis, fiid, startingPosition, direction);
	}

}
