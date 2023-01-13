package nl.inl.blacklab.server.lib;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.search.ConcordanceType;

public abstract class PlainWebserviceParamsAbstract implements PlainWebserviceParams {

    private static double parse(String value) {
        if (value != null) {
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException e) {
                // ok, just return default
            }
        }
        return 0.0;
    }

    private static int parse(String value, int defVal) {
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                // ok, just return default
            }
        }
        return defVal;
    }

    private static long parse(String value, long defVal) {
        if (value != null) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                // ok, just return default
            }
        }
        return defVal;
    }

    private static boolean parse(String value, boolean defVal) {
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
        return defVal;
    }

    protected abstract boolean has(String name);

    protected abstract String get(String name);

    protected boolean getBool(String name) {
        return PlainWebserviceParamsAbstract.parse(get(name), false);
    }

    protected int getInt(String name) {
        return PlainWebserviceParamsAbstract.parse(get(name), 0);
    }

    protected long getLong(String name) {
        return PlainWebserviceParamsAbstract.parse(get(name), 0L);
    }

    protected Set<String> getSet(String name) {
        String par = get(name).trim();
        return StringUtils.isEmpty(par) ?
                Collections.emptySet() :
                new HashSet<>(Arrays.asList(par.split("\\s*,\\s*")));
    }

    protected Optional<String> opt(String name) {
        return has(name) ? Optional.of(get(name)) : Optional.empty();
    }

    protected Optional<Double> optDouble(String name) {
        return opt(name).map(s -> PlainWebserviceParamsAbstract.parse(s));
    }

    protected Optional<Integer> optInteger(String name) {
        return opt(name).map(s -> PlainWebserviceParamsAbstract.parse(s, 0));
    }

    protected Optional<Long> optLong(String name) {
        return opt(name).map(s -> PlainWebserviceParamsAbstract.parse(s, 0L));
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
