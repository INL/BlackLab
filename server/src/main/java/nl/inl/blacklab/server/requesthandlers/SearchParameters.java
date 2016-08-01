package nl.inl.blacklab.server.requesthandlers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.servlet.http.HttpServletRequest;

import org.apache.log4j.Logger;
import org.apache.lucene.search.Query;

import nl.inl.blacklab.perdocument.DocGroupProperty;
import nl.inl.blacklab.perdocument.DocProperty;
import nl.inl.blacklab.perdocument.DocPropertyMultiple;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.HitsSample;
import nl.inl.blacklab.search.Searcher;
import nl.inl.blacklab.search.SingleDocIdFilter;
import nl.inl.blacklab.search.TextPattern;
import nl.inl.blacklab.search.grouping.GroupProperty;
import nl.inl.blacklab.server.dataobject.DataObject;
import nl.inl.blacklab.server.dataobject.DataObjectMapElement;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.NotFound;
import nl.inl.blacklab.server.jobs.ContextSettings;
import nl.inl.blacklab.server.jobs.DocGroupSettings;
import nl.inl.blacklab.server.jobs.DocGroupSortSettings;
import nl.inl.blacklab.server.jobs.DocSortSettings;
import nl.inl.blacklab.server.jobs.HitGroupSettings;
import nl.inl.blacklab.server.jobs.HitGroupSortSettings;
import nl.inl.blacklab.server.jobs.HitSortSettings;
import nl.inl.blacklab.server.jobs.JobDescription;
import nl.inl.blacklab.server.jobs.MaxSettings;
import nl.inl.blacklab.server.jobs.SampleSettings;
import nl.inl.blacklab.server.jobs.WindowSettings;
import nl.inl.blacklab.server.jobs.JobDocs.JobDescDocs;
import nl.inl.blacklab.server.jobs.JobDocsGrouped.JobDescDocsGrouped;
import nl.inl.blacklab.server.jobs.JobDocsSorted.JobDescDocsSorted;
import nl.inl.blacklab.server.jobs.JobDocsTotal.JobDescDocsTotal;
import nl.inl.blacklab.server.jobs.JobDocsWindow.JobDescDocsWindow;
import nl.inl.blacklab.server.jobs.JobFacets.JobDescFacets;
import nl.inl.blacklab.server.jobs.JobHits.JobDescHits;
import nl.inl.blacklab.server.jobs.JobHitsGrouped.JobDescHitsGrouped;
import nl.inl.blacklab.server.jobs.JobHitsSorted.JobDescHitsSorted;
import nl.inl.blacklab.server.jobs.JobHitsTotal.JobDescHitsTotal;
import nl.inl.blacklab.server.jobs.JobHitsWindow.JobDescHitsWindow;
import nl.inl.blacklab.server.jobs.JobSampleHits.JobDescSampleHits;
import nl.inl.blacklab.server.search.SearchManager;
import nl.inl.blacklab.server.util.BlsUtils;
import nl.inl.blacklab.server.util.ParseUtil;
import nl.inl.blacklab.server.util.ServletUtil;

/**
 * The parameters passed in the request.
 *
 * We create the necessary JobDescriptions from this.
 */
public class SearchParameters {
	private static final Logger logger = Logger.getLogger(SearchParameters.class);

	// TODO: move to SearchParameters?
	/** Default values for request parameters */
	final static private Map<String, String> defaultParameterValues;

	static {
		defaultParameterValues = new HashMap<>();
		defaultParameterValues.put("filterlang", "luceneql");
		defaultParameterValues.put("pattlang", "corpusql");
		defaultParameterValues.put("sort", "");
		defaultParameterValues.put("group", "");
		defaultParameterValues.put("viewgroup", "");
		defaultParameterValues.put("first", "0");
		defaultParameterValues.put("hitstart", "0");
		defaultParameterValues.put("hitend", "1");
		defaultParameterValues.put("includetokencount", "no");
		defaultParameterValues.put("usecontent", "fi");
		defaultParameterValues.put("wordstart", "-1");
		defaultParameterValues.put("wordend", "-1");
		defaultParameterValues.put("calc", "");
		defaultParameterValues.put("property", "word");
		defaultParameterValues.put("waitfortotal", "no");
		defaultParameterValues.put("number", "20");
		defaultParameterValues.put("wordsaroundhit", "5");
		defaultParameterValues.put("maxretrieve", "1000000");
		defaultParameterValues.put("maxcount", "10000000");
		defaultParameterValues.put("sensitive", "no");
	}

	private static String getParameterDefaultValue(String paramName) {
		return defaultParameterValues.get(paramName);
	}

	public static void setDefault(String name, String value) {
		defaultParameterValues.put(name, value);
	}

	public static SearchParameters get(SearchManager searchMan, boolean isDocs, String indexName, HttpServletRequest request) {
		SearchParameters param = new SearchParameters(searchMan, isDocs);
		param.put("indexname", indexName);
		for (String name: SearchParameters.NAMES) {
			String value = ServletUtil.getParameter(request, name, "").trim();
			if (value.length() == 0)
				continue;
			param.put(name, value);
		}
		return param;
	}

	/** Parameters involved in search */
	private static final List<String> NAMES = Arrays.asList(
		// What to search for
		"patt", "pattlang",                  // pattern to search for
		"filter", "filterlang",              // docs to search
		"sample", "samplenum", "sampleseed", // what hits to select

		// How to present results
		"sort",                         // sorting (grouped) hits/docs
		"first", "number",              // results window
		"wordsaroundhit", "usecontent", // concordances
		"hitstart", "hitend",           // doc snippets
		  "wordstart", "wordend",

		// How to process results
		"facets",                       // include facet information?
		"includetokencount",            // count tokens in all matched documents?
		"maxretrieve", "maxcount",      // limits to numbers of hits to process

		// Alternative views
		"calc",                         // collocations, or other context-based calculations
		"group", "viewgroup",           // grouping hits/docs
		"property", "sensitive",        // for term frequency

		// How to execute request
		"waitfortotal"                  // wait until total number of results known?
	);

	/** The search manager, for querying default value for missing parameters */
	private SearchManager searchManager;

	private Map<String, String> map = new TreeMap<>();

	/** The pattern, if parsed already */
	private TextPattern pattern;

	/** The filter query, if parsed already */
	private Query filterQuery;

	private boolean isDocsOperation;

	private SearchParameters(SearchManager searchManager, boolean isDocsOperation) {
		this.searchManager = searchManager;
		this.isDocsOperation = isDocsOperation;
	}

	public String put(String key, String value) {
		return map.put(key, value);
	}

	public String getString(Object key) {
		String value = map.get(key);
		if (value == null || value.length() == 0) {
			value = getParameterDefaultValue(key.toString());
		}
		return value;
	}

	public int getInteger(String name) {
		String value = getString(name);
		try {
			return ParseUtil.strToInt(value);
		} catch (IllegalArgumentException e) {
			logger.debug("Illegal integer value for parameter '" + name + "': " + value);
			return 0;
		}
	}

	public long getLong(String name) {
		String value = getString(name);
		try {
			return ParseUtil.strToLong(value);
		} catch (IllegalArgumentException e) {
			logger.debug("Illegal integer value for parameter '" + name + "': " + value);
			return 0L;
		}
	}

	public float getFloat(String name) {
		String value = getString(name);
		try {
			return ParseUtil.strToFloat(value);
		} catch (IllegalArgumentException e) {
			logger.debug("Illegal integer value for parameter '" + name + "': " + value);
			return 0L;
		}
	}

	public boolean getBoolean(String name) {
		String value = getString(name);
		try {
			return ParseUtil.strToBool(value);
		} catch (IllegalArgumentException e) {
			logger.debug("Illegal boolean value for parameter '" + name + "': " + value);
			return false;
		}
	}

	public DataObject toDataObject() {
		DataObjectMapElement d = new DataObjectMapElement();
		for (Map.Entry<String, String> e: map.entrySet()) {
			d.put(e.getKey(), e.getValue());
		}
		return d;
	}

	private String getIndexName() {
		return getString("indexname");
	}

	private Searcher getSearcher() {
		try {
			return searchManager.getSearcher(getIndexName());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public boolean hasPattern() throws BlsException {
		return getPattern() != null;
	}

	private TextPattern getPattern() throws BlsException {
		if (pattern == null) {
			String patt = getString("patt");
			if (patt != null && patt.length() > 0)
				pattern = BlsUtils.parsePatt(getSearcher(), patt, getString("pattlang"));
		}
		return pattern;
	}

	public boolean hasFilter() throws BlsException {
		return getFilterQuery() != null;
	}

	Query getFilterQuery() throws BlsException {
		if (filterQuery == null) {
			String docId = getString("docpid");
			if (docId != null) {
				// Only hits in 1 doc (for highlighting)
				int luceneDocId = BlsUtils.getLuceneDocIdFromPid(getSearcher(), docId);
				if (luceneDocId < 0)
					throw new NotFound("DOC_NOT_FOUND", "Document with pid '" + docId + "' not found.");
				logger.debug("Filtering on single doc-id");
				filterQuery = new SingleDocIdFilter(luceneDocId);
			} else if (containsKey("filter")) {
				filterQuery = BlsUtils.parseFilter(getSearcher(), getString("filter"), getString("filterlang"));
			}
		}
		return filterQuery;
	}

	private SampleSettings getSampleSettings() {
		if (! (containsKey("sample") || containsKey("samplenum")) )
			return null;
		float samplePercentage = containsKey("sample") ? getFloat("sample") : -1f;
		int sampleNum = containsKey("samplenum") ? getInteger("samplenum") : -1;
		long sampleSeed = containsKey("sampleseed") ? getLong("sampleseed") : HitsSample.RANDOM_SEED;
		return new SampleSettings(samplePercentage, sampleNum, sampleSeed);
	}

	private MaxSettings getMaxSettings() {
		int maxRetrieve = getInteger("maxretrieve");
		if (searchManager.getMaxHitsToRetrieveAllowed() >= 0 && maxRetrieve > searchManager.getMaxHitsToRetrieveAllowed()) {
			maxRetrieve = searchManager.getMaxHitsToRetrieveAllowed();
		}
		int maxCount = getInteger("maxcount");
		if (searchManager.getMaxHitsToCountAllowed() >= 0 && maxCount > searchManager.getMaxHitsToCountAllowed()) {
			maxCount = searchManager.getMaxHitsToCountAllowed();
		}
		return new MaxSettings(maxRetrieve, maxCount);
	}

	private WindowSettings getWindowSettings() {
		int first = getInteger("first");
		int size = getInteger("number");
		return new WindowSettings(first, size);
	}

	public ContextSettings getContextSettings() {
		int contextSize = getInteger("wordsaroundhit");
		int maxContextSize = searchManager.getMaxContextSize();
		if (contextSize > maxContextSize) {
			//debug(logger, "Clamping context size to " + maxContextSize + " (" + contextSize + " requested)");
			contextSize = maxContextSize;
		}
		ConcordanceType concType = getString("usecontent").equals("orig") ? ConcordanceType.CONTENT_STORE : ConcordanceType.FORWARD_INDEX;
		return new ContextSettings(contextSize, concType);
	}

	private List<DocProperty> getFacets() {
		String facets = getString("facets");
		if (facets == null) {
			// If no facets were specified, we shouldn't even be here.
			throw new RuntimeException("facets == null");
		}
		DocProperty propMultipleFacets = DocProperty.deserialize(facets);
		List<DocProperty> props = new ArrayList<>();
		if (propMultipleFacets instanceof DocPropertyMultiple) {
			// Multiple facets requested
			for (DocProperty prop: (DocPropertyMultiple)propMultipleFacets) {
				props.add(prop);
			}
		} else {
			// Just a single facet requested
			props.add(propMultipleFacets);
		}
		return props;
	}

	private DocGroupSettings docGroupSettings() throws BlsException {
		if (!isDocsOperation)
			return null; // we're doing per-hits stuff, so sort doesn't apply to docs
		String groupBy = getString("group");
		DocProperty groupProp = null;
		if (groupBy == null || groupBy.length() == 0)
			return null;
		groupProp = DocProperty.deserialize(groupBy);
		if (groupProp == null)
			throw new BadRequest("UNKNOWN_GROUP_PROPERTY", "Unknown group property '" + groupBy + "'.");
		return new DocGroupSettings(groupProp);
	}

	// TODO: use this properly!
	private DocGroupSortSettings docGroupSortSettings() {
		if (!isDocsOperation)
			return null; // we're doing per-hits stuff, so sort doesn't apply to docs
		if (!containsKey("group"))
			return null; // not grouping, so no group sort
		String sortBy = getString("sort");
		if (sortBy == null || sortBy.length() == 0)
			return null;
		boolean reverse = false;
		if (sortBy.length() > 0 && sortBy.charAt(0) == '-') {
			reverse = true;
			sortBy = sortBy.substring(1);
		}
		DocGroupProperty sortProp = DocGroupProperty.deserialize(sortBy);
		return new DocGroupSortSettings(sortProp, reverse);
	}

	private DocSortSettings docSortSettings() {
		if (!isDocsOperation)
			return null; // we're doing per-hits stuff, so sort doesn't apply to docs
		String sortBy = getString("sort");
		if (sortBy == null || sortBy.length() == 0)
			return null;
		boolean reverse = false;
		if (sortBy.length() > 0 && sortBy.charAt(0) == '-') {
			reverse = true;
			sortBy = sortBy.substring(1);
		}
		DocProperty sortProp = DocProperty.deserialize(sortBy);
		return new DocSortSettings(sortProp, reverse);
	}

	// TODO: use this properly!
	private HitGroupSortSettings hitGroupSortSettings()  {
		if (isDocsOperation)
			return null; // we're doing per-hits stuff, so sort doesn't apply to docs
		if (!containsKey("group"))
			return null; // not grouping, so no group sort
		String sortBy = getString("sort");
		if (sortBy == null || sortBy.length() == 0)
			return null;
		boolean reverse = false;
		if (sortBy.length() > 0 && sortBy.charAt(0) == '-') {
			reverse = true;
			sortBy = sortBy.substring(1);
		}
		GroupProperty sortProp = GroupProperty.deserialize(sortBy);
		return new HitGroupSortSettings(sortProp, reverse);
	}

	private HitGroupSettings hitGroupSettings() {
		if (isDocsOperation)
			return null; // we're doing per-hits stuff, so sort doesn't apply to docs
		String groupBy = getString("group");
		if (groupBy == null || groupBy.length() == 0)
			return null;
		return new HitGroupSettings(groupBy);
	}

	private HitSortSettings hitsSortSettings() {
		if (isDocsOperation)
			return null; // we're doing per-docs stuff, so sort doesn't apply to hits
		String sortBy = getString("sort");
		if (sortBy == null || sortBy.length() == 0)
			return null;
		boolean reverse = false;
		if (sortBy.length() > 0 && sortBy.charAt(0) == '-') {
			reverse = true;
			sortBy = sortBy.substring(1);
		}
		return new HitSortSettings(sortBy, reverse);
	}

	public boolean containsKey(String key) {
		return map.containsKey(key);
	}

	public JobDescription hitsWindow() throws BlsException {
		if (getWindowSettings() == null)
			return hitsSample();
		return new JobDescHitsWindow(hitsSample(), getWindowSettings(), getContextSettings());
	}

	public JobDescription hitsSample() throws BlsException {
		if (getSampleSettings() == null)
			return hitsSorted();
		return new JobDescSampleHits(hitsSorted(), getSampleSettings());
	}

	public JobDescription hitsSorted() throws BlsException {
		if (hitsSortSettings() == null)
			return hits();
		return new JobDescHitsSorted(hits(), hitsSortSettings());
	}

	public JobDescription hitsTotal() throws BlsException {
		return new JobDescHitsTotal(hits());
	}

	public JobDescription hits() throws BlsException {
		return new JobDescHits(getIndexName(), getPattern(), getFilterQuery(), getMaxSettings());
	}

	public JobDescription docsWindow() throws BlsException {
		if (getWindowSettings() == null)
			return docsSorted();
		return new JobDescDocsWindow(docsSorted(), getWindowSettings(), getContextSettings());
	}

	public JobDescription docsSorted() throws BlsException {
		if (docSortSettings() == null)
			return docs();
		return new JobDescDocsSorted(docs(), docSortSettings());
	}

	public JobDescription docsTotal() throws BlsException {
		return new JobDescDocsTotal(docs());
	}

	public JobDescription docs() throws BlsException {
		if (getPattern() != null)
			return new JobDescDocs(hitsSample(), getFilterQuery());
		return new JobDescDocs(null, getFilterQuery());
	}

	public JobDescription hitsGrouped() throws BlsException {
		return new JobDescHitsGrouped(hitsSample(), hitGroupSettings());
	}

	public JobDescription docsGrouped() throws BlsException {
		return new JobDescDocsGrouped(docs(), docGroupSettings());
	}

	public JobDescription facets() throws BlsException {
		return new JobDescFacets(docs(), getFacets());
	}

}
