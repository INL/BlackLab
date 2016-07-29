package nl.inl.blacklab.server.search;

import java.util.List;

import org.apache.lucene.search.Query;

import nl.inl.blacklab.perdocument.DocProperty;
import nl.inl.blacklab.search.TextPattern;
import nl.inl.blacklab.server.exceptions.BlsException;

public abstract class JobDescriptionAbstract extends JobDescription {

	@Override
	public String uniqueIdentifier() {
		try {
			return this.getClass().getSimpleName() + " [indexName=" + getIndexName() + ", pattern=" + getPattern() + ", filterQuery="
					+ getFilterQuery() + ", hitsSortSett=" + hitsSortSettings() + ", hitsGroupSett=" + hitGroupSettings() + ", hitsGroupSortSett="
					+ hitGroupSortSettings() + ", docSortSett=" + docSortSettings() + ", docGroupSett=" + docGroupSettings() + ", docGroupSortSortSett="
					+ docGroupSortSettings() + ", maxSettings=" + getMaxSettings() + ", sampleSettings=" +
					getSampleSettings() + ", windowSettings=" + getWindowSettings() + ", contextSettings=" + getContextSettings()
					+ ", facets=" + getFacets()
					+ "]";
		} catch (BlsException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public String toString() {
		return uniqueIdentifier();
	}

	@Override
	public TextPattern getPattern() throws BlsException {
		return null;
	}

	@Override
	public Query getFilterQuery() throws BlsException {
		return null;
	}

	@Override
	public SampleSettings getSampleSettings() {
		return null;
	}

	@Override
	public MaxSettings getMaxSettings() {
		return null;
	}

	@Override
	public WindowSettings getWindowSettings() {
		return null;
	}

	@Override
	public ContextSettings getContextSettings() {
		return null;
	}

	@Override
	public DocGroupSettings docGroupSettings() throws BlsException {
		return null;
	}

	@Override
	public DocGroupSortSettings docGroupSortSettings() throws BlsException {
		return null;
	}

	@Override
	public DocSortSettings docSortSettings() {
		return null;
	}

	@Override
	public List<DocProperty> getFacets() {
		return null;
	}

	@Override
	public HitGroupSettings hitGroupSettings() {
		return null;
	}

	@Override
	public HitGroupSortSettings hitGroupSortSettings() {
		return null;
	}

	@Override
	public HitsSortSettings hitsSortSettings() {
		return null;
	}

	@Override
	public boolean hasSort() {
		return false;
	}

}