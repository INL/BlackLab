package nl.inl.blacklab.server.search;

import org.apache.lucene.search.Query;

import nl.inl.blacklab.search.Hits;
import nl.inl.blacklab.search.TextPattern;
import nl.inl.blacklab.search.grouping.HitGroups;
import nl.inl.blacklab.search.grouping.HitProperty;
import nl.inl.blacklab.server.dataobject.DataObjectMapElement;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.util.ThreadPriority.Level;

/**
 * Represents a hits search and sort operation.
 */
public class JobHitsGrouped extends Job {

	private HitGroups groups;

	private Hits hits;

	public JobHitsGrouped(SearchManager searchMan, User user, Description par) throws BlsException {
		super(searchMan, user, par);
	}

	@Override
	public void performSearch() throws BlsException {
		// First, execute blocking hits search.
		Description parNoGroup = DescriptionImpl.jobHits(JobHits.class, jobDesc.getIndexName(), jobDesc.getPattern(),
				jobDesc.getFilterQuery(), null, jobDesc.getMaxSettings(), jobDesc.getSampleSettings(), jobDesc.getWindowSettings(),
				jobDesc.getContextSettings());
		JobWithHits hitsSearch = searchMan.searchHits(user, parNoGroup);
		try {
			waitForJobToFinish(hitsSearch);

			// Now, group the hits.
			hits = hitsSearch.getHits();
			setPriorityInternal();
		} finally {
			hitsSearch.decrRef();
			hitsSearch = null;
		}
		HitGroupSettings groupSett = jobDesc.hitGroupSettings();
		HitProperty groupProp = null;
		groupProp = HitProperty.deserialize(hits, groupSett.groupBy());
		if (groupProp == null)
			throw new BadRequest("UNKNOWN_GROUP_PROPERTY", "Unknown group property '" + groupSett.groupBy() + "'.");
		HitGroups theGroups = hits.groupedBy(groupProp);

		HitGroupSortSettings sortSett = jobDesc.hitGroupSortSettings();
		theGroups.sortGroups(sortSett.sortBy(), sortSett.reverse());

		groups = theGroups; // we're done, caller can use the groups now
	}

	public HitGroups getGroups() {
		return groups;
	}

	public Hits getHits() {
		return hits;
	}

	@Override
	protected void setPriorityInternal() {
		setHitsPriority(hits);
	}

	@Override
	public Level getPriorityOfResultsObject() {
		return hits == null ? Level.RUNNING : hits.getPriorityLevel();
	}

	@Override
	public DataObjectMapElement toDataObject(boolean debugInfo) throws BlsException {
		DataObjectMapElement d = super.toDataObject(debugInfo);
		d.put("hitsRetrieved", hits == null ? -1 : hits.countSoFarHitsRetrieved());
		d.put("numberOfGroups", groups == null ? -1 : groups.numberOfGroups());
		return d;
	}

	@Override
	protected void cleanup() {
		groups = null;
		hits = null;
		super.cleanup();
	}

	public static Description description(String indexName, TextPattern pattern, Query filterQuery, HitGroupSettings hitGroupSettings,
			HitGroupSortSettings hitGroupSortSettings, MaxSettings maxSettings, SampleSettings sampleSettings) {
		return DescriptionImpl.hitsGrouped(JobHitsGrouped.class, indexName, pattern, filterQuery, hitGroupSettings, hitGroupSortSettings, maxSettings, sampleSettings);
	}

}
