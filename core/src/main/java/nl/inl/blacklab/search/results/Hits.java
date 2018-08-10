package nl.inl.blacklab.search.results;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import nl.inl.blacklab.resultproperty.HitPropValue;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.Prioritizable;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.Span;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.util.ThreadPauser;

public interface Hits extends Iterable<Hit>, Prioritizable {

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

    /**
     * Returns the searcher object.
     *
     * @return the searcher object.
     */
    BlackLabIndex index();

    AnnotatedField field();

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
    Hits copy(HitsSettings settings);

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
     * Did we stop retrieving hits because we reached the maximum?
     * 
     * @return true if we reached the maximum and stopped retrieving hits
     */
    boolean hitsProcessedExceededMaximum();

    /**
     * Did we stop counting hits because we reached the maximum?
     * 
     * @return true if we reached the maximum and stopped counting hits
     */
    boolean hitsCountedExceededMaximum();

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
     * Get the captured group information in map form.
     *
     * Relatively slow; use getCapturedGroups() and getCapturedGroupNames() for a
     * faster alternative.
     *
     * @param hit hit to get the captured group map for
     * @return the captured group information map
     */
    Map<String, Span> capturedGroupMap(Hit hit);

    /**
     * Get the captured group information for this hit, if any.
     *
     * The names of the captured groups can be obtained through the
     * getCapturedGroupNames() method.
     *
     * @param hit the hit to get captured group information for
     * @return the captured group information, or null if none
     */
    Span[] capturedGroups(Hit hit);

    boolean hasCapturedGroups();

    /**
     * Get the captured group name information.
     *
     * @return the captured group names, in index order
     */
    List<String> capturedGroupNames();

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
     */
    HitsWindow window(Hit hit);

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
     * @param settings settings to use, or null to inherit
     * @return the window
     */
    HitsWindow window(int first, int windowSize, HitsSettings settings);

    TermFrequencyList collocations(Annotation annotation, QueryExecutionContext ctx, boolean sort);

    /**
     * Count occurrences of context words around hit.
     *
     * Uses the default contents field for collocations, and the default sensitivity
     * settings.
     *
     * @return the frequency of each occurring token
     */
    TermFrequencyList collocations();

    /**
     * Return a per-document view of these hits.
     *
     * @return the per-document view.
     */
    DocResults perDocResults();

    /**
     * Copy maxHitsRetrieved/-Counted and hitQueryContext from another Hits object.
     *
     * NOTE: this should be phased out, and copy() or adapters should be used.
     *
     * @param copyFrom where to copy stuff from
     */
    void copyMaxHitsRetrieved(Hits copyFrom);

    HitsSettings settings();

    Concordances concordances(int contextSize);

    Kwics kwics(int contextSize);

}
