package nl.inl.blacklab.server.lib;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.search.ConcordanceType;

/**
 * Abstract implementation of PlainWebserviceParams that uses request parameters.
 * This is used for both BLS and Solr.
 */
public abstract class PlainWebserviceParamsAbstract implements PlainWebserviceParams {

    private static double parseDouble(String value) {
        if (value != null) {
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException e) {
                // ok, just return default
            }
        }
        return 0.0;
    }

    private static int parseInt(String value) {
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                // ok, just return default
            }
        }
        return 0;
    }

    private static long parseLong(String value) {
        if (value != null) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                // ok, just return default
            }
        }
        return 0;
    }

    private static boolean parseBoolean(String value) {
        if (value != null) {
            switch (value) {
            case "true":
            case "1":
            case "yes":
            case "on":
                return true;
            case "false":
            case "0":
            case "no":
            case "off":
                return false;
            }
        }
        return false;
    }

    /**
     * Was a value for this parameter explicitly passed?
     *
     * This disregards any default values configured for the parameter,
     * and only checks if this request included a value for it.
     *
     * @param name parameter name
     * @return true if this request included an explicit value for the parameter
     */
    protected abstract boolean has(String name);

    /**
     * Get the parameter value.
     *
     * If this request didn't include an explicit value, use the configured default value.
     *
     * @param name parameter name
     * @return value
     */
    protected abstract String get(String name);

    /**
     * Get parameter value as a boolean.
     *
     * If not explicitly set, uses the configured default value, or false if none configured.
     *
     * @param name parameter name
     * @return value
     */
    protected boolean getBool(String name) {
        String value = get(name);
        return parseBoolean(value);
    }

    /**
     * Get parameter value as an integer.
     *
     * If not explicitly set, uses the configured default value, or 0 if none configured.
     *
     * @param name parameter name
     * @return value
     */
    protected int getInt(String name) {
        return PlainWebserviceParamsAbstract.parseInt(get(name));
    }

    /**
     * Get parameter value as a long.
     *
     * If not explicitly set, uses the configured default value, or 0 if none configured.
     *
     * @param name parameter name
     * @return value
     */
    protected long getLong(String name) {
        return PlainWebserviceParamsAbstract.parseLong(get(name));
    }

    /**
     * Get parameter value as a set of strings.
     *
     * If not explicitly set, uses the configured default value, or an empty set if none configured.
     *
     * @param name parameter name
     * @return value
     */
    protected Set<String> getSet(String name) {
        String par = get(name).trim();
        return StringUtils.isEmpty(par) ?
                Collections.emptySet() :
                new HashSet<>(Arrays.asList(par.split("\\s*,\\s*")));
    }

    /**
     * Get parameter value if it was explicitly passed with the request.
     *
     * If not explicitly set, will return an empty Optional.
     *
     * @param name parameter name
     * @return value if set
     */
    protected Optional<String> opt(String name) {
        return has(name) ? Optional.of(get(name)) : Optional.empty();
    }

    /**
     * Get parameter value if it was explicitly passed with the request.
     *
     * If not explicitly set, will return an empty Optional.
     *
     * @param name parameter name
     * @return value if set
     */
    protected Optional<Double> optDouble(String name) {
        return opt(name).map(PlainWebserviceParamsAbstract::parseDouble);
    }

    /**
     * Get parameter value if it was explicitly passed with the request.
     *
     * If not explicitly set, will return an empty Optional.
     *
     * @param name parameter name
     * @return value if set
     */
    protected Optional<Integer> optInteger(String name) {
        return opt(name).map(PlainWebserviceParamsAbstract::parseInt);
    }

    /**
     * Get parameter value if it was explicitly passed with the request.
     *
     * If not explicitly set, will return an empty Optional.
     *
     * @param name parameter name
     * @return value if set
     */
    protected Optional<Long> optLong(String name) {
        return opt(name).map(PlainWebserviceParamsAbstract::parseLong);
    }

    @Override
    public String getPattern() { return get("patt"); }

    @Override
    public String getPattLanguage() { return get("pattlang"); }

    @Override
    public String getPattGapData() { return get("pattgapdata"); }

    @Override
    public String getDocPid() { return get("docpid"); }

    @Override
    public String getDocumentFilterQuery() { return get("filter"); }

    @Override
    public String getDocumentFilterLanguage() { return get("filterlang"); }

    @Override
    public String getHitFilterCriterium() { return get("hitfiltercrit"); }

    @Override
    public String getHitFilterValue() {
        return get("hitfilterval");
    }

    @Override
    public Optional<Double> getSampleFraction() { return optDouble("sample"); }

    @Override
    public Optional<Integer> getSampleNumber() { return optInteger("samplenum"); }

    @Override
    public Optional<Long> getSampleSeed() { return optLong("sampleseed"); }

    @Override
    public boolean getUseCache() { return getBool("usecache"); }

    @Override
    public int getForwardIndexMatchFactor() { return getInt("fimatch"); }

    @Override
    public long getMaxRetrieve() { return getLong("maxretrieve"); }

    @Override
    public long getMaxCount() { return getLong("maxcount"); }

    @Override
    public long getFirstResultToShow() { return getLong("first"); }

    @Override
    public Optional<Long> optNumberOfResultsToShow() { return optLong("number"); }

    @Override
    public long getNumberOfResultsToShow() {
        // NOTE: this is NOT the same as optNumberOfResultsToShow.orElse(0L) because
        //       getString() sets a default value if "number" param is not set
        //       (yes, this is smelly)
        return getLong("number");
    }

    @Override
    public int getWordsAroundHit() { return getInt("wordsaroundhit"); }

    @Override
    public ConcordanceType getConcordanceType() {
        return get("usecontent").equals("orig") ? ConcordanceType.CONTENT_STORE : ConcordanceType.FORWARD_INDEX;
    }

    @Override
    public boolean getIncludeGroupContents() { return getBool("includegroupcontents"); }

    @Override
    public boolean getOmitEmptyCaptures() { return getBool("omitemptycaptures"); }

    @Override
    public Optional<String> getFacetProps() { return opt("facets"); }

    public Optional<String> getGroupProps() {
        return opt("group");
    }

    @Override
    public Optional<String> getSortProps() {
        return opt("sort");
    }

    @Override
    public Optional<String> getViewGroup() {
        return opt("viewgroup");
    }

    /**
     * Which annotations to list actual or available values for in hit results/hit exports/indexmetadata requests.
     * IDs are not validated and may not actually exist!
     *
     * @return which annotations to list
     */
    @Override
    public Set<String> getListValuesFor() { return getSet("listvalues"); }

    /**
     * Which metadata fields to list actual or available values for in search results/result exports/indexmetadata requests.
     * IDs are not validated and may not actually exist!
     *
     * @return which metadata fields to list
     */
    @Override
    public Set<String> getListMetadataValuesFor() { return getSet("listmetadatavalues"); }

    @Override
    public Collection<String> getListSubpropsFor() { return getSet("subprops"); }

    @Override
    public boolean getWaitForTotal() { return getBool("waitfortotal"); }

    @Override
    public boolean getIncludeTokenCount() {
        return getBool("includetokencount");
    }

    @Override
    public boolean getCsvIncludeSummary() {
        return getBool("csvsummary");
    }

    @Override
    public boolean getCsvDeclareSeparator() {
        return getBool("csvsepline");
    }

    @Override
    public boolean getExplain() {
        return getBool("explain");
    }

    @Override
    public boolean getSensitive() { return getBool("sensitive"); }

    @Override
    public int getWordStart() { return getInt("wordstart"); }

    @Override
    public int getWordEnd() {
        return getInt("wordend");
    }

    @Override
    public Optional<Integer> getHitStart() { return optInteger("hitstart"); }

    @Override
    public int getHitEnd() { return getInt("hitend"); }

    @Override
    public String getAutocompleteTerm() { return get("term"); }

    @Override
    public boolean isCalculateCollocations() { return get("calc").equals("colloc"); }

    @Override
    public String getAnnotationName() {
        String annotName = get("annotation");
        if (annotName.length() == 0)
            annotName = get("property"); // old parameter name, deprecated
        return annotName;
    }

    @Override
    public Set<String> getTerms() { return getSet("terms"); }

    @Override
    public boolean isIncludeDebugInfo() { return getBool("debug"); }
}
