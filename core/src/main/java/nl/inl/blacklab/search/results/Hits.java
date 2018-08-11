package nl.inl.blacklab.search.results;

import java.util.Iterator;
import java.util.List;

import nl.inl.blacklab.exceptions.ResultNotFound;
import nl.inl.blacklab.exceptions.WildcardTermTooBroad;
import nl.inl.blacklab.resultproperty.HitPropValue;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.Prioritizable;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.util.ThreadPauser;

public interface Hits extends Iterable<Hit>, Prioritizable {

    /**
     * Construct a Hits object from a SpanQuery.
     * 
     * @param queryInfo information about the original query
     * @param query the query to execute to get the hits
     * @return hits found
     * @throws WildcardTermTooBroad if a wildcard term matches too many terms in the index
     */
    static Hits fromSpanQuery(QueryInfo queryInfo, BLSpanQuery query) throws WildcardTermTooBroad {
        return new HitsFromQuery(queryInfo, query);
    }

    /**
     * Make a wrapper Hits object for a list of Hit objects.
     *
     * Does not copy the list, but reuses it.
     * 
     * @param queryInfo information about the original query
     * @param hits the list of hits to wrap, or null for empty Hits object
     * @return hits found
     */
    static Hits fromList(QueryInfo queryInfo, List<Hit> hits) {
        return new HitsImpl(queryInfo, hits);
    }

    /**
     * Construct an empty Hits object.
     * 
     * @param queryInfo query info 
     * @return hits found
     */
    static Hits emptyList(QueryInfo queryInfo) {
        return Hits.fromList(queryInfo, (List<Hit>) null);
    }

    // Inherited from Results
    //--------------------------------------------------------------------
    
    /**
     * For Hits, this is an alias of hitsProcessedTotal.
     * 
     * Other Results classes each have size(), but the meaning depends on the type of results.
     * 
     * @return number of hits processed total
     */
    int size();

    int resultsObjId();

    ThreadPauser threadPauser();

    /**
     * Pause or unpause this search.
     *
     * This can be used to stop a heavy search from consuming CPU resources
     * when other users are waiting.
     * 
     * Pausing actually amounts to "proceeding very slowly".
     *
     * @param pause if true, pause the search; if false, unpause it
     */
    void pause(boolean pause);

    /**
     * Is this search currently paused?
     *
     * @return true if search is paused, false if not
     */
    @Override
    boolean isPaused();

    /**
     * Return a copy of this Hits object.
     *
     * NOTE: Why not use clone()/Cloneable? See
     * http://www.artima.com/intv/bloch13.html
     * 
     * @param settings settings to use, or null to copy settings too
     *
     * @return a copy of this Hits object
     */
    Hits copy();

    /**
     * Return the specified hit.
     *
     * @param i index of the desired hit
     * @return the hit, or null if it's beyond the last hit
     */
    Hit get(int i);

    /**
     * Return an iterator over these hits.
     *
     * The order is the sorted order, not the original order. Use
     * hitsInOriginalOrder() to iterate in the original order.
     *
     * @return the iterator
     */
    @Override
    Iterator<Hit> iterator();

    /**
     * Return the specified hit number, based on the order they were originally
     * found (not the sorted order).
     *
     * @param i index of the desired hit
     * @return the hit, or null if it's beyond the last hit
     */
    Hit getByOriginalOrder(int i);

    Iterable<Hit> originalOrder();

    @Override
    String toString();

    /**
     * Group these hits by a criterium (or several criteria).
     *
     * @param criteria the hit property to group on
     * @return a HitGroups object representing the grouped hits
     */
    HitGroups groupedBy(HitProperty criteria);

    /**
     * Select only the hits where the specified property has the specified value.
     * 
     * @param property property to select on, e.g. "word left of hit"
     * @param value value to select on, e.g. 'the'
     * @return filtered hits
     */
    Hits filteredBy(HitProperty property, HitPropValue value);

    /**
     * Get a sorted copy of these hits.
     *
     * Note that if the thread is interrupted during this, sort may return without
     * the hits actually being fully read and sorted. We don't want to add throws
     * declarations to our whole API, so we assume the calling method will check for
     * thread interruption if the application uses it.
     *
     * @param sortProp the hit property to sort on
     * @param reverseSort if true, sort in descending order
     * @return sorted hits
     */
    Hits sortedBy(HitProperty sortProp, boolean reverseSort);

    /**
     * Return a new Hits object with these hits sorted by the given property.
     *
     * This keeps the existing sort (or lack of one) intact and allows you to cache
     * different sorts of the same resultset. The hits themselves are reused between
     * the two Hits instances, so not too much additional memory is used.
     *
     * @param sortProp the hit property to sort on
     * @return a new Hits object with the same hits, sorted in the specified way
     */
    Hits sortedBy(HitProperty sortProp);
    
    // Specific to Hits
    //--------------------------------------------------------------------

    /**
     * Return the number of hits retrieved so far.
     *
     * If you're retrieving hits in a background thread, call this method from
     * another thread to get an update of the count so far.
     *
     * @return the current total hit count
     */
    int hitsProcessedSoFar();

    /**
     * Determines if there are at least a certain number of hits
     *
     * This may be used if we don't want to process all hits (which may be a lot)
     * but we do need to know something about the size of the result set (such as
     * for paging).
     *
     * Note that this method applies to the hits retrieved, which may be less than
     * the total number of hits (depending on maxHitsToRetrieve).
     *
     * @param lowerBound the number we're testing against
     *
     * @return true if the size of this set is at least lowerBound, false otherwise.
     */
    boolean hitsProcessedAtLeast(int lowerBound);

    /**
     * Return the number of hits available.
     *
     * Note that this method applies to the hits retrieved, which may be less than
     * the total number of hits (depending on maxHitsToRetrieve). Use totalSize() to
     * find the total hit count (which may also be limited depending on
     * maxHitsToCount).
     *
     * @return number of hits processed total
     */
    int hitsProcessedTotal();

    /**
     * Return the number of hits counted so far.
     *
     * If you're retrieving hit in a background thread, call this method from
     * another thread to get an update of the count so far.
     *
     * @return the current total hit count
     */
    int hitsCountedSoFar();

    /**
     * Return the total number of hits.
     *
     * NOTE: Depending on maxHitsToRetrieve, hit retrieval may stop before all hits
     * are seen. We do keep counting hits though (until we reach maxHitsToCount, or
     * that value is negative). This method returns our total hit count. Some of
     * these hits may not be available.
     *
     * @return the total hit count
     */
    int hitsCountedTotal();

    /**
     * Return the number of documents retrieved so far.
     *
     * If you're retrieving hits in a background thread, call this method from
     * another thread to get an update of the count so far.
     *
     * @return the current total hit count
     */
    int docsProcessedSoFar();

    /**
     * Return the number of documents in the hits we've retrieved.
     *
     * @return the number of documents.
     */
    int docsProcessedTotal();

    /**
     * Return the number of documents counted so far.
     *
     * If you're retrieving hit in a background thread, call this method from
     * another thread to get an update of the count so far.
     *
     * @return the current total hit count
     */
    int docsCountedSoFar();

    /**
     * Return the total number of documents in all hits. This counts documents even
     * in hits that are not stored, only counted.
     *
     * @return the total number of documents.
     */
    int docsCountedTotal();

    /**
     * Check if we're done retrieving/counting hits.
     *
     * If you're retrieving hits in a background thread, call this method from
     * another thread to check if all hits have been processed.
     *
     * @return true iff all hits have been retrieved/counted.
     */
    boolean doneProcessingAndCounting();

    /**
     * Get the captured group information, if any.
     *
     * @return the captured group information, or null if there were no captured groups
     */
    CapturedGroups capturedGroups();

    /**
     * Do we have captured groups?
     * 
     * @return true if we do, false if we don't
     */
    boolean hasCapturedGroups();

    /**
     * Convenience method to get all hits in a single doc from a larger hitset.
     *
     * Don't use this for grouping or per-document results as it's relatively
     * inefficient.
     *
     * @param docid the doc id to get hits for
     * @return the list of hits in this doc (if any)
     */
    Hits getHitsInDoc(int docid);

    /**
     * Get a window with a single hit in it.
     * 
     * @param hit the hit we want (must be in this Hits object)
     * @return window
     * @throws ResultNotFound if hit was not found in this result set
     */
    HitsWindow window(Hit hit) throws ResultNotFound;

    /**
     * Get a window into this list of hits.
     *
     * Use this if you're displaying part of the resultset, like in a paging
     * interface. It makes sure BlackLab only works with the hits you want to
     * display and doesn't do any unnecessary processing on the other hits.
     *
     * HitsWindow includes methods to assist with paging, like figuring out if there
     * hits before or after the window.
     *
     * @param first first hit in the window (0-based)
     * @param windowSize size of the window
     * @return the window
     */
    HitsWindow window(int first, int windowSize);

    /**
     * Count occurrences of context words around hit.
     *
     * Uses the default contents field for collocations, and the default sensitivity
     * settings.
     * 
     * @param contextSize how many words around the hits to use
     * @param annotation what annotation to get collocations for
     * @param ctx query execution context; contains sensitivity setting
     * @param sort sort the resulting collocations by descending frequency?
     * @return the frequency of each occurring token
     */
    TermFrequencyList collocations(int contextSize, Annotation annotation, QueryExecutionContext ctx, boolean sort);

    /**
     * Count occurrences of context words around hit.
     *
     * Uses the default contents field for collocations, and the default sensitivity
     * settings.
     * 
     * @param contextSize how many words around the hits to use
     * @return the frequency of each occurring token
     */
    TermFrequencyList collocations(int contextSize);

    /**
     * Return a per-document view of these hits.
     *
     * @return the per-document view.
     */
    DocResults perDocResults();

    /**
     * Create concordances from the forward index or content store.
     * 
     * @param type where to create concordances from
     * @param contextSize desired context size
     * @return concordances
     */
    Concordances concordances(int contextSize, ConcordanceType type);

    /**
     * Create concordances from the forward index.
     * 
     * @param contextSize desired context size
     * @return concordances
     */
    default Concordances concordances(int contextSize) {
        return concordances(contextSize, ConcordanceType.FORWARD_INDEX);
    }
    
    /**
     * Create KWICs from the forward index.
     * 
     * KWIC = KeyWord In Context, essentially snippets around all the hits. 
     * 
     * @param contextSize desired context size
     * @return KWICs
     */
    Kwics kwics(int contextSize);

    /**
     * Get information about the original query.
     * 
     * This includes the index, field, max. settings, and max. stats
     * (whether the max. settings were reached).
     * 
     * @return query info
     */
    QueryInfo queryInfo();

}
