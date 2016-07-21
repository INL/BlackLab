package nl.inl.blacklab.server.search;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import nl.inl.blacklab.server.dataobject.DataObject;
import nl.inl.blacklab.server.dataobject.DataObjectMapElement;

import org.apache.log4j.Logger;

/**
 * Uniquely describes a search operation.
 *
 * Used for caching and nonblocking operation.
 *
 * Derives from TreeMap because it keeps entries in sorted order, which can  be convenient.
 */
public class SearchParameters extends TreeMap<String, String> {
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
		StringBuilder b = new StringBuilder();
		for (Map.Entry<String, String> e: entrySet()) {
			if (b.length() > 0)
				b.append(", ");
			b.append(e.getKey() + "=" + e.getValue());
		}
		return "{ " + b.toString() + " }";
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

	public SearchParameters copyWithJobClass(String newJobClass) {
		SearchParameters par = new SearchParameters(searchManager);
		par.putAll(this);
		par.put("jobclass", newJobClass);
		return par;
	}

	public SearchParameters copyWithOnly(String... keys) {
		SearchParameters copy = new SearchParameters(searchManager);
		for (String key: keys) {
			if (containsKey(key))
				copy.put(key, get(key));
		}
		return copy;
	}

	public SearchParameters copyWithout(String... remove) {
		SearchParameters copy = new SearchParameters(searchManager);
		copy.putAll(this);
		for (String key: remove) {
			copy.remove(key);
		}
		return copy;
	}

	public DataObject toDataObject() {
		DataObjectMapElement d = new DataObjectMapElement();
		for (Map.Entry<String, String> e: entrySet()) {
			d.put(e.getKey(), e.getValue());
		}
		return d;
	}

}
