package org.ivdnt.blacklab.solr;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.search.DocList;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.server.lib.PlainWebserviceParams;
import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.search.SearchManager;

public class WebserviceParamsSolr implements PlainWebserviceParams {

    private static final String BL_PAR_NAME = "bl";

    private static final String BL_PAR_NAME_PREFIX = BL_PAR_NAME + ".";

    private final SolrParams solrParams;

    private final DocList docList;

    private final BlackLabIndex index;

    private final SearchManager searchManager;

    public WebserviceParamsSolr(ResponseBuilder rb, BlackLabIndex index, SearchManager searchManager) {
        solrParams = rb.req.getParams();
        docList = rb.getResults() != null ? rb.getResults().docList : null;
        this.index = index;
        this.searchManager = searchManager;
    }

    // FIXME: default values (see BlackLabServerParams)

    public boolean isRunBlackLab() {
        return solrParams.getBool(BL_PAR_NAME, false);
    }

    public String bl(String name) {
        return solrParams.get(BL_PAR_NAME_PREFIX + name);
    }

    public String bl(String name, String def) {
        return solrParams.get(BL_PAR_NAME_PREFIX + name, def);
    }


    public boolean bl(String name, boolean def) {
        return solrParams.getBool(BL_PAR_NAME_PREFIX + name, def);
    }

    public int bl(String name, int def) {
        return solrParams.getInt(BL_PAR_NAME_PREFIX + name, def);
    }

    public long bl(String name, long def) {
        return solrParams.getLong(BL_PAR_NAME_PREFIX + name, def);
    }
    public Optional<String> blOpt(String name) {
        return Optional.ofNullable(solrParams.get(name));
    }

    public Optional<Double> blOptDouble(String name) {
        return Optional.ofNullable(solrParams.getDouble(name));
    }

    public Optional<Integer> blOptInteger(String name) {
        return Optional.ofNullable(solrParams.getInt(name));
    }

    public Optional<Long> blOptLong(String name) {
        return Optional.ofNullable(solrParams.getLong(name));
    }

    public Set<String> blSet(String name, String def) {
        String par = bl(name, def).trim();
        return StringUtils.isEmpty(par) ?
                Collections.emptySet() :
                new HashSet<>(Arrays.asList(par.split("\\s*,\\s*")));
    }

    @Override
    public Map<String, String> getParameters() {
        return Collections.emptyMap(); // @@@ FIXME
    }

    @Override
    public String getIndexName() {
        return index.name();
    }

    @Override
    public String getPattern() {
        return bl("patt");
    }

    @Override
    public String getPattLanguage() {
        return bl("pattlang", "corpusql");
    }

    @Override
    public String getPattGapData() {
        return bl("pattgapdata");
    }

    @Override
    public SearchManager getSearchManager() {
        return searchManager;
    }

    @Override
    public User getUser() {
        return User.anonymous("12345"); // @@@ FIXME
    }

    @Override
    public String getDocPid() {
        return bl("docpid");
    }

    @Override
    public String getDocumentFilterQuery() {
        return bl("filter");
    }

    @Override
    public String getDocumentFilterLanguage() {
        return bl("filterlang");
    }

    @Override
    public String getHitFilterCriterium() {
        return bl("hitfiltercrit");
    }

    @Override
    public String getHitFilterValue() {
        return bl("hitfilterval");
    }

    @Override
    public Optional<Double> getSampleFraction() {
        return blOptDouble("sample");
    }

    @Override
    public Optional<Integer> getSampleNumber() {
        return blOptInteger("samplenum");
    }

    @Override
    public Optional<Long> getSampleSeed() {
        return blOptLong("sampleseed");
    }

    @Override
    public boolean getUseCache() {
        return bl("usecache", true);
    }

    @Override
    public int getForwardIndexMatchFactor() {
        return bl("fimatch", 0);
    }

    @Override
    public long getMaxRetrieve() {
        return bl("maxretrieve", 1_000_000L);
    }

    @Override
    public long getMaxCount() {
        return bl("maxcount", 1_000_000L);
    }

    @Override
    public long getFirstResultToShow() {
        return bl("first", 0L);
    }

    @Override
    public Optional<Long> optNumberOfResultsToShow() {
        return blOptLong("number");
    }

    @Override
    public long getNumberOfResultsToShow() {
        return optNumberOfResultsToShow().orElse(0L);
    }

    @Override
    public int getWordsAroundHit() {
        return bl("wordsaroundhit", 0);
    }

    @Override
    public ConcordanceType getConcordanceType() {
        return bl("usecontent", "fi").equals("orig") ?
                ConcordanceType.CONTENT_STORE :
                ConcordanceType.FORWARD_INDEX;
    }

    @Override
    public boolean getIncludeGroupContents() {
        return bl("includegroupcontents", false);
    }

    @Override
    public boolean getOmitEmptyCaptures() {
        return bl("omitemptycaptures", false);
    }

    @Override
    public Optional<String> getFacetProps() {
        return blOpt("facets");
    }

    @Override
    public Optional<String> getGroupProps() {
        return blOpt("group");
    }

    @Override
    public Optional<String> getSortProps() {
        return blOpt("sort");
    }

    @Override
    public Optional<String> getViewGroup() {
        return blOpt("viewgroup");
    }

    @Override
    public Collection<String> getListValuesFor() {
        return blSet("listvalues", "");
    }

    @Override
    public Collection<String> getListMetadataValuesFor() {
        return blSet("listmetadatavalues", "");
    }

    @Override
    public Collection<String> getListSubpropsFor() {
        return blSet("subprops", "");
    }

    @Override
    public boolean getWaitForTotal() {
        return bl("waitfortotal", false);
    }

    @Override
    public boolean getIncludeTokenCount() {
        return bl("includetokencount", false);
    }

    @Override
    public boolean getCsvIncludeSummary() {
        return bl("csvsummary", true);
    }

    @Override
    public boolean getCsvDeclareSeparator() {
        return bl("csvsepline", true);
    }

    @Override
    public boolean getExplain() {
        return bl("explain", false);
    }

    @Override
    public boolean getSensitive() {
        return bl("sensitive", false);
    }

    @Override
    public int getWordStart() {
        return bl("wordstart", 0);
    }

    @Override
    public int getWordEnd() {
        return bl("wordend", 0);
    }

    @Override
    public Optional<Integer> getHitStart() {
        return blOptInteger("hitstart");
    }

    @Override
    public int getHitEnd() {
        return bl("hitend", 0);
    }

    @Override
    public String getAutocompleteTerm() {
        return bl("term", "");
    }

    @Override
    public boolean isCalculateCollocations() {
        return bl("calc", "").equals("colloc");
    }

    @Override
    public String getAnnotationName() {
        return bl("annotation");
    }

    @Override
    public Set<String> getTerms() {
        return blSet("terms", "");
    }

    @Override
    public boolean isIncludeDebugInfo() {
        return bl("debug", false);
    }
}
