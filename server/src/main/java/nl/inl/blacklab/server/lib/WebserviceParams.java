package nl.inl.blacklab.server.lib;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import nl.inl.blacklab.search.ConcordanceType;

/** API-independent interface to BlackLab operation parameters */
public interface WebserviceParams {

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

    String getFieldName();

    Set<String> getTerms();

    boolean isIncludeDebugInfo();
}
