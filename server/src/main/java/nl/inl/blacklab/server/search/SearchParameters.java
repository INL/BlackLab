package nl.inl.blacklab.server.search;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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
import nl.inl.blacklab.server.exceptions.InternalServerError;
import nl.inl.blacklab.server.exceptions.NotFound;

/**
 * Uniquely describes a search operation.
 *
 * Used for caching and nonblocking operation.
 *
 * Derives from TreeMap because it keeps entries in sorted order, which can  be convenient.
 */
public class SearchParameters extends TreeMap<String, String> implements Job.Description {
	private static final Logger logger = Logger.getLogger(SearchParameters.class);

	/** The search manager, for querying default value for missing parameters */
	private SearchManager searchManager;

	/** Parameters involved in search */
	final static public List<String> NAMES = Arrays.asList(
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

	public SearchParameters(SearchManager searchManager) {
		this.searchManager = searchManager;
	}

	@Override
	public String toString() {
		return "{ " + uniqueIdentifier() + " }";
	}

	public String getString(Object key) {
		String value = super.get(key);
		if (value == null || value.length() == 0) {
			value = searchManager.getParameterDefaultValue(key.toString());
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

	@Override
	public DataObject toDataObject() {
		DataObjectMapElement d = new DataObjectMapElement();
		for (Map.Entry<String, String> e: entrySet()) {
			d.put(e.getKey(), e.getValue());
		}
		return d;
	}

	@Override
	public String uniqueIdentifier() {
		StringBuilder b = new StringBuilder();
		for (Map.Entry<String, String> e: entrySet()) {
			if (b.length() > 0)
				b.append(", ");
			b.append(e.getKey() + "=" + e.getValue());
		}
		return b.toString();
	}

	@Override
	public Job createJob(SearchManager searchMan, User user) throws BlsException {
		String strJobClass = getString("jobclass");
		if (!strJobClass.startsWith("Job"))
			throw new InternalServerError("Illegal Job class name", 1);
		Class<?> jobClass;
		try {
			jobClass = Class.forName("nl.inl.blacklab.server.search." + strJobClass);
			Constructor<?> cons = jobClass.getConstructor(SearchManager.class, User.class, Job.Description.class);
			return (Job)cons.newInstance(searchMan, user, this);
		} catch (ClassNotFoundException|NoSuchMethodException|InstantiationException|IllegalAccessException|IllegalArgumentException|InvocationTargetException e) {
			throw new InternalServerError("Error instantiating Job class", 1, e);
		}
	}

	@Override
	public String getIndexName() {
		return getString("indexname");
	}

	protected Searcher getSearcher() {
		try {
			return searchManager.getSearcher(getIndexName());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public TextPattern getPattern() throws BlsException {
		if (containsKey("patt"))
			return searchManager.parsePatt(getSearcher(), getString("patt"), getString("pattlang"));
		return null;
	}

	@Override
	public Query getFilterQuery() throws BlsException {
		String docId = getString("docpid");
		if (docId != null) {
			// Only hits in 1 doc (for highlighting)
			int luceneDocId = SearchManager.getLuceneDocIdFromPid(getSearcher(), docId);
			if (luceneDocId < 0)
				throw new NotFound("DOC_NOT_FOUND", "Document with pid '" + docId + "' not found.");
			logger.debug("Filtering on single doc-id");
			return new SingleDocIdFilter(luceneDocId);
		}

		if (containsKey("filter"))
			return SearchManager.parseFilter(getSearcher(), getString("filter"), getString("filterlang"));
		return null;
	}

	@Override
	public SampleSettings getSampleSettings() {
		float samplePercentage = containsKey("sample") ? getFloat("sample") : -1f;
		int sampleNum = containsKey("samplenum") ? getInteger("samplenum") : -1;
		long sampleSeed = containsKey("sampleseed") ? getLong("sampleseed") : HitsSample.RANDOM_SEED;
		return new SampleSettings(samplePercentage, sampleNum, sampleSeed);
	}

	@Override
	public MaxSettings getMaxSettings() {
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

	@Override
	public WindowSettings getWindowSettings() {
		int first = getInteger("first");
		int size = getInteger("number");
		return new WindowSettings(first, size);
	}

	@Override
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

	@Override
	public List<DocProperty> getFacets() {
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

	@Override
	public DocGroupSettings docGroupSettings() throws BlsException {
		String groupBy = getString("group");
		DocProperty groupProp = null;
		if (groupBy == null)
			groupBy = "";
		groupProp = DocProperty.deserialize(groupBy);
		if (groupProp == null)
			throw new BadRequest("UNKNOWN_GROUP_PROPERTY", "Unknown group property '" + groupBy + "'.");
		return new DocGroupSettings(groupProp);
	}

	@Override
	public DocGroupSortSettings docGroupSortSettings() throws BlsException {
		String sortBy = getString("sort");
		if (sortBy == null)
			sortBy = "";
		boolean reverse = false;
		if (sortBy.length() > 0 && sortBy.charAt(0) == '-') {
			reverse = true;
			sortBy = sortBy.substring(1);
		}
		DocGroupProperty sortProp = DocGroupProperty.deserialize(sortBy);
		return new DocGroupSortSettings(sortProp, reverse);
	}

	@Override
	public DocSortSettings docSortSettings() {
		String sortBy = getString("sort");
		if (sortBy == null)
			sortBy = "";
		boolean reverse = false;
		if (sortBy.length() > 0 && sortBy.charAt(0) == '-') {
			reverse = true;
			sortBy = sortBy.substring(1);
		}
		DocProperty sortProp = DocProperty.deserialize(sortBy);
		return new DocSortSettings(sortProp, reverse);
	}

	@Override
	public HitGroupSortSettings hitGroupSortSettings()  {
		String sortBy = getString("sort");
		if (sortBy == null)
			sortBy = "";
		boolean reverse = false;
		if (sortBy.length() > 0 && sortBy.charAt(0) == '-') {
			reverse = true;
			sortBy = sortBy.substring(1);
		}
		GroupProperty sortProp = GroupProperty.deserialize(sortBy);
		return new HitGroupSortSettings(sortProp, reverse);
	}

	@Override
	public HitGroupSettings hitGroupSettings() {
		String groupBy = getString("group");
		if (groupBy == null)
			groupBy = "";
		return new HitGroupSettings(groupBy);
	}

	@Override
	public HitsSortSettings hitsSortSettings() {
		String sortBy = getString("sort");
		if (sortBy == null)
			sortBy = "";
		boolean reverse = false;
		if (sortBy.length() > 0 && sortBy.charAt(0) == '-') {
			reverse = true;
			sortBy = sortBy.substring(1);
		}
		return new HitsSortSettings(sortBy, reverse);
	}

	@Override
	public boolean hasSort() {
		return containsKey("sort") && getString("sort").length() > 0;
	}

}
