package nl.inl.blacklab.server.search;

import java.util.List;

import org.apache.lucene.search.Query;

import nl.inl.blacklab.perdocument.DocProperty;
import nl.inl.blacklab.search.TextPattern;
import nl.inl.blacklab.server.dataobject.DataObject;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.search.JobDocs.JobDescDocs;
import nl.inl.blacklab.server.search.JobDocsGrouped.JobDescDocsGrouped;
import nl.inl.blacklab.server.search.JobDocsSorted.JobDescDocsSorted;
import nl.inl.blacklab.server.search.JobDocsTotal.JobDescDocsTotal;
import nl.inl.blacklab.server.search.JobDocsWindow.JobDescDocsWindow;
import nl.inl.blacklab.server.search.JobFacets.JobDescFacets;
import nl.inl.blacklab.server.search.JobHits.JobDescHits;
import nl.inl.blacklab.server.search.JobHitsGrouped.JobDescHitsGrouped;
import nl.inl.blacklab.server.search.JobHitsSorted.JobDescHitsSorted;
import nl.inl.blacklab.server.search.JobHitsTotal.JobDescHitsTotal;
import nl.inl.blacklab.server.search.JobHitsWindow.JobDescHitsWindow;
import nl.inl.blacklab.server.search.JobSampleHits.JobDescSampleHits;

/** Description of a job */
public abstract class JobDescription {
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

	/**
	 * Get the index name
	 * @return name of the index this job if for
	 */
	public abstract String getIndexName();

	/**
	 * Get the pattern to search for (if any)
	 * @return pattern, or null if none given
	 * @throws BlsException on error
	 */
	public abstract TextPattern getPattern() throws BlsException;

	public abstract Query getFilterQuery() throws BlsException;

	public abstract SampleSettings getSampleSettings();

	public abstract MaxSettings getMaxSettings();

	public abstract WindowSettings getWindowSettings();

	public abstract ContextSettings getContextSettings();

	public abstract DocGroupSettings docGroupSettings() throws BlsException;

	public abstract DocGroupSortSettings docGroupSortSettings() throws BlsException;

	public abstract DocSortSettings docSortSettings();

	public abstract List<DocProperty> getFacets();

	public abstract HitGroupSettings hitGroupSettings();

	public abstract HitGroupSortSettings hitGroupSortSettings();

	public abstract HitsSortSettings hitsSortSettings();

	public abstract boolean hasSort();

	public abstract DataObject toDataObject();

	public JobDescription hitsWindow() throws BlsException {
		if (getWindowSettings() == null)
			return hitsSample();
		return new JobDescHitsWindow(getIndexName(), hitsSample(), getWindowSettings(), getContextSettings());
	}

	public JobDescription hitsSample() throws BlsException {
		if (getSampleSettings() == null)
			return hitsSorted();
		return new JobDescSampleHits(getIndexName(), hitsSorted(), getSampleSettings());
	}

	public JobDescription hitsSorted() throws BlsException {
		if (!hasSort())
			return hits();
		return new JobDescHitsSorted(getIndexName(), hits(), hitsSortSettings());
	}

	public JobDescription hitsTotal() throws BlsException {
		return new JobDescHitsTotal(getIndexName(), hits());
	}

	public JobDescription hits() throws BlsException {
		return new JobDescHits(getIndexName(), getPattern(), getFilterQuery(), getMaxSettings());
	}

	public JobDescription docsWindow() throws BlsException {
		if (getWindowSettings() == null)
			return docsSorted();
		return new JobDescDocsWindow(getIndexName(), docsSorted(), getWindowSettings(), getContextSettings());
	}

	public JobDescription docsSorted() throws BlsException {
		if (!hasSort())
			return docs();
		return new JobDescDocsSorted(getIndexName(), docs(), docSortSettings());
	}

	public JobDescription docsTotal() throws BlsException {
		return new JobDescDocsTotal(getIndexName(), docs());
	}

	public JobDescription docs() throws BlsException {
		if (getPattern() != null)
			return new JobDescDocs(getIndexName(), hitsSample(), getFilterQuery());
		return new JobDescDocs(getIndexName(), null, getFilterQuery());
	}

	public JobDescription hitsGrouped() throws BlsException {
		return new JobDescHitsGrouped(getIndexName(), hitsSample(), hitGroupSettings());
		//return JobDescriptionImpl.hitsGrouped(JobHitsGrouped.class, getIndexName(), getPattern(), getFilterQuery(), hitGroupSettings(), hitGroupSortSettings(), getMaxSettings(), getSampleSettings());
	}

	public JobDescription docsGrouped() throws BlsException {
		return new JobDescDocsGrouped(getIndexName(), docs(), docGroupSettings());
//		return JobDescriptionImpl.docsGrouped(JobDocsGrouped.class, getIndexName(), getPattern(), getFilterQuery(), docGroupSettings(),
//		docGroupSortSettings(), getMaxSettings());
	}

	public JobDescription facets() throws BlsException {
		return new JobDescFacets(getIndexName(), docs(), getFacets());
		//return JobDescriptionImpl.facets(JobFacets.class, getIndexName(), getPattern(), getFilterQuery(), getFacets(), getMaxSettings());
	}
}