package nl.inl.blacklab.server.lib;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.server.config.BLSConfigParameters;
import nl.inl.blacklab.server.index.IndexManager;
import nl.inl.blacklab.server.search.SearchManager;

/** API-independent interface to BlackLab operation parameters.
 *
 * <p>In addition to parameters, also knows about SearchManager, IndexManager and User,
 * which are useful to pass around with the parameters.
 *
 * <p>This class only includes "plain" parameters, not any objects derived from them.
 */
public interface QueryParams {

    /**
     * Get a view of the parameters.
     *
     * @return the view
     */
    Map<String, String> getParameters();

    String getIndexName();

    String getPattern();

    String getPattLanguage();

    String getPattGapData();

    SearchManager getSearchManager();

    default BLSConfigParameters configParam() {
        return getSearchManager().config().getParameters();
    }

    default IndexManager getIndexManager() { return getSearchManager().getIndexManager(); }

    User getUser();

    String getDocPid();

    String getDocumentFilterQuery();

    String getDocumentFilterLanguage();

    String getHitFilterCriterium();

    String getHitFilterValue();

    Optional<Double> getSampleFraction();

    Optional<Integer> getSampleNumber();

    Optional<Long> getSampleSeed();

    boolean getUseCache();

    int getForwardIndexMatchFactor();

    long getMaxRetrieve();

    long getMaxCount();

    long getFirstResultToShow();

    Optional<Long> optNumberOfResultsToShow();

    long getNumberOfResultsToShow();

    int getWordsAroundHit();

    ConcordanceType getConcordanceType();

    boolean getIncludeGroupContents();

    boolean getOmitEmptyCaptures();

    Optional<String> getFacetProps();

    Optional<String> getGroupProps();

    Optional<String> getSortProps();

    Optional<String> getViewGroup();

    /**
     * Which annotations to list actual or available values for in hit results/hit exports/indexmetadata requests.
     * IDs are not validated and may not actually exist!
     *
     * @return which annotations to list
     */
    Collection<String> getListValuesFor();

    /**
     * Which metadata fields to list actual or available values for in search results/result exports/indexmetadata requests.
     * IDs are not validated and may not actually exist!
     *
     * @return which metadata fields to list
     */
    Collection<String> getListMetadataValuesFor();

    Collection<String> getListSubpropsFor();

    boolean getWaitForTotal();

    boolean getIncludeTokenCount();

    boolean getCsvIncludeSummary();

    boolean getCsvDeclareSeparator();

    boolean getExplain();

    boolean getSensitive();

    int getWordStart();

    int getWordEnd();

    Optional<Integer> getHitStart();

    int getHitEnd();

    String getAutocompleteTerm();

    boolean isCalculateCollocations();

    String getAnnotationName();

    Set<String> getTerms();

    boolean isIncludeDebugInfo();

    String getFieldName();
}
