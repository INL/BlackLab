package nl.inl.blacklab.server.lib;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.server.config.BLSConfigParameters;

public class ParameterDefaults {

    /**
     * Parameters involved in search
     */
    public static final Set<String> NAMES = new HashSet<>(Arrays.asList(
            // What to search for
            "patt", "pattlang", "pattgapdata", // pattern to search for
            "filter", "filterlang", "docpid", // docs to search
            "sample", "samplenum", "sampleseed", // what hits to select
            "hitfiltercrit", "hitfilterval",

            // How to search
            "fimatch", // [debug] set NFA FI matching threshold
            "usecache", // [debug] use cache or bypass it?

            // How to present results
            "sort", // sorting (grouped) hits/docs
            "first", "number", // results window
            "wordsaroundhit", "usecontent", // concordances
            "hitstart", "hitend", // doc snippets
            "wordstart", "wordend",
            "explain", // explain query rewriting?

            // on field info page, show (non-sub) values for annotation?
            // also controls which annotations' values are sent back with hits
            "listvalues",
            // on document info page, list the values for which metadata fields?
            //also controls which metadata fields are sent back with search hits and document search results
            "listmetadatavalues",
            // EXPERIMENTAL, mostly for part of speech, limited to 500 values
            "subprops", // on field info page, show all subannotations and values for annotation
            // EXPERIMENTAL, mostly for part of speech

            // How to process results
            "facets", // include facet information?
            "includetokencount", // count tokens in all matched documents?
            "maxretrieve", "maxcount", // limits to numbers of hits to process

            // Alternative views
            "calc", // collocations, or other context-based calculations
            "group", "viewgroup", // grouping hits/docs
            "annotation", "sensitive", "terms", // for term frequency

            // How to execute request
            "waitfortotal", // wait until total number of results known?
            "term", // term for autocomplete

            // CSV options
            "csvsummary", // include summary of search in the CSV output? [no]
            "csvsepline", // include separator declaration for Excel? [no]

            // Deprecated parameters
            "property", // now called "annotation",

            "includegroupcontents", // include hits with the group response? (false)
            "omitemptycaptures",  // omit capture groups of length 0? (false)

            "debug", // include debug info (cache)

            "indexname",
            "field"
    ));

    public static boolean paramExists(String name) {
        return NAMES.contains(name);
    }

    public static String get(String name) {
        String v = values.get(name);
        return v == null ? "" : v;
    }

    /**
     * Default values for request parameters
     */
    final static private Map<String, String> values;

    static {
        // Default values for the parameters. Note that if no default is set, the default will be the empty string.
        values = new HashMap<>();
        //values.put("annotation", "");   // default empty, because we fall back to the old name, "property".
        //values.put("calc", "");
        values.put("csvsepline", "yes");
        values.put("csvsummary", "yes");
        values.put("debug", "no");
        values.put("explain", "no");
        values.put("filterlang", "luceneql");
        values.put("fimatch", "-1");
        values.put("first", "0");
        //values.put("group", "");
        values.put("hitend", "1");
        values.put("hitstart", "0");
        values.put("includegroupcontents", "no");
        values.put("includetokencount", "no");
        //values.put("listmetadatavalues", "");
        //values.put("listvalues", "");
        values.put("maxcount", "10000000");
        values.put("maxretrieve", "1000000");
        values.put("number", "50");
        values.put("omitemptycaptures", "no");
        values.put("pattlang", "corpusql");
        values.put("property", AnnotatedFieldNameUtil.DEFAULT_MAIN_ANNOT_NAME); // deprecated, use "annotation" now
        values.put("sensitive", "no");
        //values.put("sort", "");
        //values.put("subprops", "");
        values.put("usecache", "yes");
        values.put("usecontent", "fi");
        //values.put("viewgroup", "");
        values.put("waitfortotal", "no");
        values.put("wordend", "-1");
        values.put("wordsaroundhit", "5");
        values.put("wordstart", "-1");
    }

    /**
     * Set up parameter default values from the configuration.
     */
    public static void set(BLSConfigParameters param) {
        // Set up the parameter default values
        values.put("maxretrieve", "" + param.getProcessHits().getDefaultValue());
        values.put("maxcount", "" + param.getCountHits().getDefaultValue());
        values.put("number", "" + param.getPageSize().getDefaultValue());
        values.put("sensitive", param.getDefaultSearchSensitivity() == MatchSensitivity.SENSITIVE ? "yes" : "no");
        values.put("wordsaroundhit", "" + param.getContextSize().getDefaultValue());
    }

}
