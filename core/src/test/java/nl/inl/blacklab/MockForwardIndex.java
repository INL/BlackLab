package nl.inl.blacklab;

import java.util.List;
import java.util.Set;

import org.apache.lucene.index.IndexReader;

import nl.inl.blacklab.forwardindex.ForwardIndex;
import nl.inl.blacklab.forwardindex.Terms;

public class MockForwardIndex extends ForwardIndex {

	private Terms terms;

	public MockForwardIndex(Terms terms) {
		this.terms = terms;
	}

	@Override
	public void setIdTranslateInfo(IndexReader reader, String lucenePropFieldName) {
		//

	}

	@Override
	public int luceneDocIdToFiid(int docId) {
		//
		return 0;
	}

	@Override
	public void close() {
		//

	}

	@Override
	public int addDocument(List<String> content, List<Integer> posIncr) {
		//
		return 0;
	}

	@Override
	public void deleteDocument(int fiid) {
		//

	}

	@Override
	public List<int[]> retrievePartsInt(int fiid, int[] start, int[] end) {
		//
		return null;
	}

	@Override
	public Terms getTerms() {
		//
		return terms;
	}

	@Override
	public int getNumDocs() {
		//
		return 0;
	}

	@Override
	public long getFreeSpace() {
		//
		return 0;
	}

	@Override
	public int getFreeBlocks() {
		//
		return 0;
	}

	@Override
	public long getTotalSize() {
		//
		return 0;
	}

	@Override
	public int getDocLength(int fiid) {
		//
		return 0;
	}

	@Override
	protected void setLargeTermsFileSupport(boolean b) {
		//

	}

	@Override
	public Set<Integer> idSet() {
		return null;
	}

}
