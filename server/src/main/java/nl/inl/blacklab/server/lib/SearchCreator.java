package nl.inl.blacklab.server.lib;

import java.util.Optional;

import org.apache.lucene.search.Query;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.results.SampleParameters;
import nl.inl.blacklab.search.results.SearchSettings;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.searches.SearchCount;
import nl.inl.blacklab.searches.SearchDocGroups;
import nl.inl.blacklab.searches.SearchDocs;
import nl.inl.blacklab.searches.SearchFacets;
import nl.inl.blacklab.searches.SearchHitGroups;
import nl.inl.blacklab.searches.SearchHits;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.index.IndexManager;
import nl.inl.blacklab.server.jobs.ContextSettings;
import nl.inl.blacklab.server.jobs.HitSortSettings;
import nl.inl.blacklab.server.jobs.WindowSettings;

/**
 * Extends the WebserviceParams class with methods that instantiate searches
 * based on the parameter values.
 */
public interface SearchCreator extends WebserviceParams {
    BlackLabIndex blIndex();

    boolean hasPattern() throws BlsException;

    Optional<TextPattern> pattern() throws BlsException;

    boolean hasFilter() throws BlsException;

    String getDocPid();

    Query filterQuery() throws BlsException;

    /**
     * @return hits - filtered then sorted then sampled then windowed
     */
    SearchHits hitsWindow() throws BlsException;

    WindowSettings windowSettings();

    boolean includeGroupContents();

    boolean omitEmptyCapture();

    HitSortSettings hitsSortSettings();

    SampleParameters sampleSettings();

    boolean hasFacets();

    SearchSettings searchSettings();

    ContextSettings contextSettings();

    boolean useCache();

    /**
     * @return hits - filtered then sorted then sampled
     */
    SearchHits hitsSample() throws BlsException;

    SearchDocs docsWindow() throws BlsException;

    SearchDocs docsSorted() throws BlsException;

    SearchCount docsCount() throws BlsException;

    SearchDocs docs() throws BlsException;

    /**
     * Return our subcorpus.
     * The subcorpus is defined as all documents satisfying the metadata query.
     * If no metadata query is given, the subcorpus is all documents in the corpus.
     *
     * @return subcorpus
     */
    SearchDocs subcorpus() throws BlsException;

    SearchHitGroups hitsGroupedStats() throws BlsException;

    SearchHitGroups hitsGroupedWithStoredHits() throws BlsException;

    SearchDocGroups docsGrouped() throws BlsException;

    SearchFacets facets() throws BlsException;

}
