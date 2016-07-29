package nl.inl.blacklab.server.search;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import org.apache.lucene.search.Query;

import nl.inl.blacklab.perdocument.DocProperty;
import nl.inl.blacklab.search.TextPattern;
import nl.inl.blacklab.server.dataobject.DataObject;
import nl.inl.blacklab.server.dataobject.DataObjectList;
import nl.inl.blacklab.server.dataobject.DataObjectMapElement;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.InternalServerError;

/** Description of a job */
public class JobDescriptionImpl extends JobDescriptionBasic {

	private Class<? extends Job> jobClass;

	private TextPattern pattern;

	private Query filterQuery;

	private HitsSortSettings hitsSortSett;

	private HitGroupSettings hitsGroupSett;

	private HitGroupSortSettings hitsGroupSortSett;

	private DocSortSettings docSortSett;

	private DocGroupSettings docGroupSett;

	private DocGroupSortSettings docGroupSortSett;

	private MaxSettings maxSettings;

	private SampleSettings sampleSettings;

	private WindowSettings windowSettings;

	private ContextSettings contextSettings;

	private List<DocProperty> facets;

	public JobDescriptionImpl(String indexName) {
		super(indexName);
	}

	/**
	 * Generate a unique identifier string for this job, for caching, etc.
	 *
	 * NOTE: this is not guaranteed to stay the same between BlackLab versions.
	 *
	 * @return the unique identifier string
	 */
	@Override
	public String uniqueIdentifier() {
		return "DescriptionImpl [jobClass=" + jobClass + ", indexName=" + getIndexName() + ", pattern=" + pattern.toString() + ", filterQuery="
				+ filterQuery + ", hitsSortSett=" + hitsSortSett + ", hitsGroupSett=" + hitsGroupSett + ", hitsGroupSortSett="
				+ hitsGroupSortSett + ", docSortSett=" + docSortSett + ", docGroupSett=" + docGroupSett + ", docGroupSortSortSett="
				+ docGroupSortSett + ", maxSettings=" + maxSettings + ", sampleSettings=" +
				sampleSettings + ", windowSettings=" + windowSettings + ", contextSettings=" + contextSettings
				+ ", facets=" + facets
				+ "]";
	}

	/**
	 * Create the job corresponding to this description.
	 * @param searchMan searchmanager to use
	 * @param user user that created the job
	 * @return newly created job
	 * @throws BlsException on error
	 */
	@Override
	public Job createJob(SearchManager searchMan, User user) throws BlsException {
		try {
			Constructor<?> cons = jobClass.getConstructor(SearchManager.class, User.class, JobDescription.class);
			return (Job)cons.newInstance(searchMan, user, this);
		} catch (NoSuchMethodException|InstantiationException|IllegalAccessException|IllegalArgumentException|InvocationTargetException e) {
			throw new InternalServerError("Error instantiating Job class", 1, e);
		}
	}

	/**
	 * Get the pattern to search for (if any)
	 * @return pattern, or null if none given
	 */
	@Override
	public TextPattern getPattern() {
		return pattern;
	}

	@Override
	public Query getFilterQuery() {
		return filterQuery;
	}

	@Override
	public SampleSettings getSampleSettings() {
		return sampleSettings;
	}

	@Override
	public MaxSettings getMaxSettings() {
		return maxSettings;
	}

	@Override
	public WindowSettings getWindowSettings() {
		return windowSettings;
	}

	@Override
	public ContextSettings getContextSettings() {
		return contextSettings;
	}

	@Override
	public DocGroupSettings docGroupSettings() {
		return docGroupSett;
	}

	@Override
	public DocGroupSortSettings docGroupSortSettings() {
		return docGroupSortSett;
	}

	@Override
	public DocSortSettings docSortSettings() {
		return docSortSett;
	}

	@Override
	public List<DocProperty> getFacets() {
		return facets;
	}

	@Override
	public HitGroupSettings hitGroupSettings() {
		return hitsGroupSett;
	}

	@Override
	public HitGroupSortSettings hitGroupSortSettings() {
		return hitsGroupSortSett;
	}

	@Override
	public HitsSortSettings hitsSortSettings() {
		return hitsSortSett;
	}

	@Override
	public boolean hasSort() {
		return hitsSortSett != null || hitsGroupSortSett != null || docSortSett != null || docGroupSortSett != null;
	}

	@Override
	public DataObject toDataObject() {
		DataObjectMapElement d = new DataObjectMapElement();
		d.put("jobClass", jobClass.getSimpleName());
		d.put("indexName", indexName);
		if (pattern != null)
			d.put("pattern", pattern.toString());
		if (filterQuery != null)
			d.put("filterQuery", filterQuery.toString());
		if (hitsSortSett != null)
			d.put("hitsSortSett", hitsSortSett.toString());
		if (hitsGroupSett != null)
			d.put("hitsGroupSett", hitsGroupSett.toString());
		if (hitsGroupSortSett != null)
			d.put("hitsGroupSortSett", hitsGroupSortSett.toString());
		if (docSortSett != null)
			d.put("docSortSett", docSortSett.toString());
		if (docGroupSett != null)
			d.put("docGroupSett", docGroupSett.toString());
		if (docGroupSortSett != null)
			d.put("docGroupSortSett", docGroupSortSett.toString());
		if (maxSettings != null)
			d.put("maxSettings", maxSettings.toString());
		if (sampleSettings != null)
			d.put("sampleSettings", sampleSettings.toString());
		if (windowSettings != null)
			d.put("windowSettings", windowSettings.toString());
		if (contextSettings != null)
			d.put("contextSettings", contextSettings.toString());
		if (facets != null) {
			DataObjectList l = new DataObjectList("facet");
			for (DocProperty facet: facets) {
				l.add(facet.toString());
			}
			d.put("facets", l);
		}
		return d;
	}

}