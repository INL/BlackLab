package nl.inl.blacklab.server.search;

import java.util.List;

import org.apache.lucene.search.Query;

import nl.inl.blacklab.perdocument.DocProperty;
import nl.inl.blacklab.search.TextPattern;
import nl.inl.blacklab.server.dataobject.DataObject;
import nl.inl.blacklab.server.exceptions.BlsException;

/** Description of a job */
public abstract class JobDescription {

	JobDescription inputDesc;

	public JobDescription(JobDescription inputDesc) {
		this.inputDesc = inputDesc;
	}

	public JobDescription getInputDesc() {
		return inputDesc;
	}

	/**
	 * Get the index name
	 * @return name of the index this job if for
	 */
	public String getIndexName() {
		return inputDesc.getIndexName();
	}

	/**
	 * Generate a unique identifier string for this job, for caching, etc.
	 * @return the unique identifier string
	 */
	public abstract String uniqueIdentifier();

	/**
	 * Create the job corresponding to this description.
	 * @param searchMan searchmanager to use
	 * @param user user that created the job
	 * @return newly created job
	 * @throws BlsException on error
	 */
	public abstract Job createJob(SearchManager searchMan, User user) throws BlsException;

	@Override
	public String toString() {
		return uniqueIdentifier();
	}

	/**
	 * Get the pattern to search for (if any)
	 * @return pattern, or null if none given
	 * @throws BlsException on error
	 */
	public TextPattern getPattern() throws BlsException {
		return null;
	}

	public Query getFilterQuery() throws BlsException {
		return null;
	}

	public SampleSettings getSampleSettings() {
		return null;
	}

	public MaxSettings getMaxSettings() {
		return null;
	}

	public WindowSettings getWindowSettings() {
		return null;
	}

	public ContextSettings getContextSettings() {
		return null;
	}

	public List<DocProperty> getFacets() {
		return null;
	}

	public DocGroupSettings getDocGroupSettings() throws BlsException {
		return null;
	}

	public DocGroupSortSettings getDocGroupSortSettings() throws BlsException {
		return null;
	}

	public DocSortSettings getDocSortSettings() {
		return null;
	}

	public HitGroupSettings getHitGroupSettings() {
		return null;
	}

	public HitGroupSortSettings getHitGroupSortSettings() {
		return null;
	}

	public HitSortSettings getHitSortSettings() {
		return null;
	}

	public boolean hasSort() {
		return false;
	}

	public abstract DataObject toDataObject();

}