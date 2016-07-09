package nl.inl.blacklab.search;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.Spans;

import nl.inl.blacklab.perdocument.DocResults;
import nl.inl.blacklab.search.grouping.HitGroups;
import nl.inl.blacklab.search.grouping.HitProperty;
import nl.inl.blacklab.search.grouping.HitPropertyMultiple;
import nl.inl.blacklab.search.grouping.ResultsGrouper;
import nl.inl.blacklab.search.lucene.HitQueryContext;
import nl.inl.util.ThreadPriority;

public abstract class Hits extends AbstractList<Hit> implements Cloneable {

	/** In context arrays, how many bookkeeping ints are stored at the start? */
	public final static int CONTEXTS_NUMBER_OF_BOOKKEEPING_INTS = 3;
	/** In context arrays, what index after the bookkeeping units indicates the hit start? */
	public final static int CONTEXTS_HIT_START_INDEX = 0;
	/** In context arrays, what index indicates the hit end (start of right part)? */
	public final static int CONTEXTS_RIGHT_START_INDEX = 1;
	/** In context arrays, what index indicates the length of the context? */
	public final static int CONTEXTS_LENGTH_INDEX = 2;

	/**
	 * Stop retrieving hits after this number.
	 * (-1 = don't stop retrieving)
	 */
	protected static int defaultMaxHitsToRetrieve = 1000000;

	/**
	 * Stop counting hits after this number.
	 * (-1 = don't stop counting)
	 */
	protected static int defaultMaxHitsToCount = -1;

	/** @return the default maximum number of hits to retrieve. */
	public static int getDefaultMaxHitsToRetrieve() {
		return defaultMaxHitsToRetrieve;
	}

	/** Set the default maximum number of hits to retrieve
	 * @param n the number of hits, or -1 for no limit
	 */
	public static void setDefaultMaxHitsToRetrieve(int n) {
		Hits.defaultMaxHitsToRetrieve = n;
	}

	/** @return the default maximum number of hits to count. */
	public static int getDefaultMaxHitsToCount() {
		return defaultMaxHitsToCount;
	}

	/** Set the default maximum number of hits to count
	 * @param n the number of hits, or -1 for no limit
	 */
	public static void setDefaultMaxHitsToCount(int n) {
		Hits.defaultMaxHitsToCount = n;
	}

	/**
	 * Construct an empty Hits object.
	 *
	 * @param searcher
	 *            the searcher object
	 * @param concordanceFieldName
	 *            field to use by default when finding concordances
	 */
	public static Hits emptyList(Searcher searcher, String concordanceFieldPropName) {
		return fromList(searcher, concordanceFieldPropName, (List<Hit>)null);
	}

	/**
	 * Make a wrapper Hits object for a list of Hit objects.
	 *
	 * Does not copy the list, but reuses it.
	 *
	 * @param searcher
	 *            the searcher object
	 * @param concordanceFieldName
	 *            field to use by default when finding concordances
	 * @param hits the list of hits to wrap
	 */
	public static Hits fromList(Searcher searcher, String concField, List<Hit> docHits) {
		return new HitsImpl(searcher, concField, docHits);
	}

	/**
	 * Construct a Hits object from a SpanQuery.
	 *
	 * @param searcher
	 *            the searcher object
	 * @param concordanceFieldPropName
	 *            field to use by default when finding concordances
	 * @param sourceQuery
	 *            the query to execute to get the hits
	 * @throws TooManyClauses if the query is overly broad (expands to too many terms)
	 */
	public static Hits fromSpanQuery(Searcher searcher, String field, SpanQuery query) {
		return new HitsImpl(searcher, field, query);
	}


	/**
	 * Construct a Hits object from a Spans.
	 *
	 * Used for testing. Don't use this in applications, but construct a Hits object from a
	 * SpanQuery, as it's more efficient.
	 *
	 * @param searcher
	 *            the searcher object
	 * @param concordanceFieldPropName
	 *            field to use by default when finding concordances
	 * @param source
	 *            where to retrieve the Hit objects from
	 */
	public static Hits fromSpans(Searcher searcher, String concordanceFieldPropName, Spans source) {
		return new HitsImpl(searcher, concordanceFieldPropName, source);
	}

	//----------------------------------------------------------

	protected Searcher searcher;

	/**
	 * Stop retrieving hits after this number.
	 * (-1 = don't stop retrieving)
	 */
	protected int maxHitsToRetrieve = defaultMaxHitsToRetrieve;

	/**
	 * Stop counting hits after this number.
	 * (-1 = don't stop counting)
	 */
	protected int maxHitsToCount = defaultMaxHitsToCount;

	/**
	 * The default field to use for retrieving concordance information.
	 */
	protected String concordanceFieldName;

	/** Forward index to use as text context of &lt;w/&gt; tags in concordances (words; null = no text content) */
	protected String concWordFI;

	/** Forward index to use as text context between &lt;w/&gt; tags in concordances (punctuation+whitespace; null = just a space) */
	protected String concPunctFI;

	/** Forward indices to use as attributes of &lt;w/&gt; tags in concordances (null = the rest) */
	protected Collection<String> concAttrFI; // all other FIs are attributes

	/** What to use to make concordances: forward index or content store */
	protected ConcordanceType concsType;

	/**
	 * The desired context size (number of words to fetch around hits).
	 * Defaults to Searcher.getDefaultContextSize().
	 */
	protected int desiredContextSize;

	protected HitQueryContext hitQueryContext;

	protected ThreadPriority etiquette;

	public Hits(Searcher searcher, String concordanceFieldName) {
		this.searcher = searcher;
		setConcordanceField(concordanceFieldName);
		concWordFI = searcher.getConcWordFI();
		concPunctFI = searcher.getConcPunctFI();
		concAttrFI = searcher.getConcAttrFI();
		concsType = searcher.getDefaultConcordanceType();
		desiredContextSize = searcher.getDefaultContextSize();
		hitQueryContext = new HitQueryContext(); // to keep track of captured groups, etc.
	}



	/**
	 * Set the thread priority level for this Hits object.
	 *
	 * Allows us to set a query to low-priority, or to (almost) pause it.
	 *
	 * @param level the desired priority level
	 */
	public void setPriorityLevel(ThreadPriority.Level level) {
		etiquette.setPriorityLevel(level);
	}

	/**
	 * Get the thread priority level for this Hits object.
	 *
	 * Can be normal, low-priority, or (almost) paused.
	 *
	 * @return the current priority level
	 */
	public ThreadPriority.Level getPriorityLevel() {
		return etiquette.getPriorityLevel();
	}

	/**
	 * Return a copy of this Hits object.
	 *
	 * NOTE: Why not use clone()/Cloneable? See http://www.artima.com/intv/bloch13.html
	 *
	 * @return a copy of this Hits object
	 */
	public abstract Hits copy();

	/**
	 * Copy the settings from another Hits object.
	 *
	 * NOTE: this should be phased out, and copy() or adapters should be used.
	 *
	 * @param copyFrom where to copy settings from
	 */
	public void copySettingsFrom(Hits copyFrom) {
		setMaxHitsToRetrieve(copyFrom.getMaxHitsToRetrieve());
		setMaxHitsToCount(copyFrom.getMaxHitsToCount());
		setMaxHitsRetrieved(copyFrom.maxHitsRetrieved());
		setMaxHitsCounted(copyFrom.maxHitsCounted());
		setContextSize(copyFrom.getContextSize());
		setHitQueryContext(copyFrom.getHitQueryContext());
	}

	protected abstract void setMaxHitsCounted(boolean maxHitsCounted);

	protected abstract void setMaxHitsRetrieved(boolean maxHitsRetrieved);

	/** Returns the context size.
	 * @return context size (number of words to fetch around hits)
	 */
	public int getContextSize() {
		return desiredContextSize;
	}

	/** Sets the desired context size.
	 * @param contextSize the context size (number of words to fetch around hits)
	 */
	public synchronized void setContextSize(int contextSize) {
		this.desiredContextSize = contextSize;
	}

	/**
	 * Were all hits retrieved, or did we stop because there were too many?
	 * @return true if all hits were retrieved
	 * @deprecated renamed to maxHitsRetrieved()
	 */
	@Deprecated
	public boolean tooManyHits() {
		return maxHitsRetrieved();
	}

	/**
	 * Did we stop retrieving hits because we reached the maximum?
	 * @return true if we reached the maximum and stopped retrieving hits
	 */
	public abstract boolean maxHitsRetrieved();

	/**
	 * Did we stop counting hits because we reached the maximum?
	 * @return true if we reached the maximum and stopped counting hits
	 */
	public abstract boolean maxHitsCounted();

	/**
	 * Get the list of hits.
	 *
	 * @return the list of hits
	 * @deprecated Slow, breaks optimizations. Iterate over the Hits object and/or Hits.window() instead.
	 */
	@Deprecated
	public synchronized List<Hit> getHits() {
		// We do it this way because if we return (a copy of) the hits list,
		// you get it unsorted.
		List<Hit> list = new ArrayList<>();
		for (Hit h: this) {
			list.add(h);
		}
		return list;
	}


	/**
	 * Sort the list of hits.
	 *
	 * Note that if the thread is interrupted during this, sort may return
	 * without the hits actually being fully read and sorted. We don't want
	 * to add throws declarations to our whole API, so we assume the calling
	 * method will check for thread interruption if the application uses it.
	 *
	 * @param sortProp
	 *            the hit property/properties to sort on
	 * @param reverseSort
	 *            if true, sort in descending order
	 * @deprecated use sortedBy()
	 */
	@Deprecated
	public void sort(HitProperty[] sortProp, boolean reverseSort) {
		if (sortProp.length == 1)
			sort(sortProp[0], reverseSort);
		else
			sort(new HitPropertyMultiple(sortProp), reverseSort);
	}

	/**
	 * Sort the list of hits.
	 *
	 * Note that if the thread is interrupted during this, sort may return
	 * without the hits actually being fully read and sorted. We don't want
	 * to add throws declarations to our whole API, so we assume the calling
	 * method will check for thread interruption if the application uses it.
	 *
	 * @param sortProp
	 *            the hit property/properties to sort on
	 * @deprecated use sortedBy()
	 */
	@Deprecated
	public void sort(HitProperty... sortProp) {
		sort(sortProp, false);
	}

	/**
	 * Sort the list of hits.
	 *
	 * Note that if the thread is interrupted during this, sort may return
	 * without the hits actually being fully read and sorted. We don't want
	 * to add throws declarations to our whole API, so we assume the calling
	 * method will check for thread interruption if the application uses it.
	 *
	 * @param sortProp
	 *            the hit property to sort on
	 * @deprecated use sortedBy()
	 */
	@Deprecated
	public void sort(final HitProperty sortProp) {
		sort(sortProp, false, searcher.isDefaultSearchCaseSensitive());
	}

	/**
	 * Sort the list of hits.
	 *
	 * Note that if the thread is interrupted during this, sort may return
	 * without the hits actually being fully read and sorted. We don't want
	 * to add throws declarations to our whole API, so we assume the calling
	 * method will check for thread interruption if the application uses it.
	 *
	 * Case-sensitivity depends on the default case-sensitivity set on the Searcher
	 * object.
	 *
	 * @param sortProp
	 *            the hit property to sort on
	 * @param reverseSort
	 *            if true, sort in descending order
	 * @deprecated use sortedBy()
	 */
	@Deprecated
	public void sort(final HitProperty sortProp, boolean reverseSort) {
		sort(sortProp, reverseSort, searcher.isDefaultSearchCaseSensitive());
	}

	/**
	 * Sort the list of hits.
	 *
	 * Note that if the thread is interrupted during this, sort may return
	 * without the hits actually being fully read and sorted. We don't want
	 * to add throws declarations to our whole API, so we assume the calling
	 * method will check for thread interruption if the application uses it.
	 *
	 * @param sortProp
	 *            the hit property to sort on
	 * @param reverseSort
	 *            if true, sort in descending order
	 * @param sensitive whether to sort case-sensitively or not
	 * @deprecated use sortedBy()
	 */
	@Deprecated
	public abstract void sort(HitProperty sortProp, boolean reverseSort, boolean sensitive);

	/**
	 * Return a new Hits object with these hits sorted by the given property.
	 *
	 * This keeps the existing sort (or lack of one) intact and allows you to cache
	 * different sorts of the same resultset. The hits themselves are reused between
	 * the two Hits instances, so not too much additional memory is used.
	 *
	 * @param sortProp
	 *            the hit property to sort on
	 * @param reverseSort
	 *            if true, sort in descending order
	 * @param sensitive whether to sort case-sensitively or not
	 * @return a new Hits object with the same hits, sorted in the specified way
	 */
	public Hits sortedBy(HitProperty sortProp, boolean reverseSort, boolean sensitive) {
		Hits hits = copy();
		sortProp = sortProp.copyWithHits(hits);
		hits.sort(sortProp, reverseSort, sensitive);
		return hits;
	}

	/**
	 * Return a new Hits object with these hits sorted by the given property.
	 *
	 * This keeps the existing sort (or lack of one) intact and allows you to cache
	 * different sorts of the same resultset. The hits themselves are reused between
	 * the two Hits instances, so not too much additional memory is used.
	 *
	 * @param sortProp
	 *            the hit property to sort on
	 * @param reverseSort
	 *            if true, sort in descending order
	 * @return a new Hits object with the same hits, sorted in the specified way
	 */
	public Hits sortedBy(final HitProperty sortProp, boolean reverseSort) {
		return sortedBy(sortProp, reverseSort, searcher.isDefaultSearchCaseSensitive());
	}

	/**
	 * Return a new Hits object with these hits sorted by the given property.
	 *
	 * This keeps the existing sort (or lack of one) intact and allows you to cache
	 * different sorts of the same resultset. The hits themselves are reused between
	 * the two Hits instances, so not too much additional memory is used.
	 *
	 * @param sortProp
	 *            the hit property to sort on
	 * @return a new Hits object with the same hits, sorted in the specified way
	 */
	public Hits sortedBy(final HitProperty sortProp) {
		return sortedBy(sortProp, false, searcher.isDefaultSearchCaseSensitive());
	}

	/**
	 * Group these hits by a criterium (or several criteria).
	 *
	 * @param criteria
	 *            the hit property to group on
	 * @return a HitGroups object representing the grouped hits
	 */
	@SuppressWarnings("deprecation") // ResultsGrouper constructor will be made package-private eventually
	public HitGroups groupedBy(final HitProperty criteria) {
		return new ResultsGrouper(this, criteria);
	}

	/**
	 * Return a per-document view of these hits.
	 *
	 * @return the per-document view.
	 */
	@SuppressWarnings("deprecation") // DocResults constructor will be made package-private eventually
	public DocResults perDocResults() {
		return new DocResults(getSearcher(), this);
	}

	/**
	 * Determines if there are at least a certain number of hits
	 *
	 * This may be used if we don't want to process all hits (which
	 * may be a lot) but we do need to know something about the size
	 * of the result set (such as for paging).
	 *
	 * Note that this method applies to the hits retrieved, which may
	 * be less than the total number of hits (depending on maxHitsToRetrieve).
	 *
	 * @param lowerBound the number we're testing against
	 *
	 * @return true if the size of this set is at least lowerBound, false otherwise.
	 */
	public abstract boolean sizeAtLeast(int lowerBound);

	/**
	 * Return the number of hits available.
	 *
	 * Note that this method applies to the hits retrieved, which may
	 * be less than the total number of hits (depending on maxHitsToRetrieve).
	 * Use totalSize() to find the total hit count (which may also be limited
	 * depending on maxHitsToCount).
	 *
	 * @return the number of hits available
	 */
	@Override
	public abstract int size();

	/**
	 * Return the total number of hits.
	 *
	 * NOTE: Depending on maxHitsToRetrieve, hit retrieval may stop
	 * before all hits are seen. We do keep counting hits though
	 * (until we reach maxHitsToCount, or that value is negative).
	 * This method returns our total hit count. Some of these hits
	 * may not be available.
	 *
	 * @return the total hit count
	 */
	public abstract int totalSize();

	/**
	 * Return the number of documents in the hits we've retrieved.
	 *
	 * @return the number of documents.
	 */
	public abstract int numberOfDocs();

	/**
	 * Return the total number of documents in all hits.
	 * This counts documents even in hits that are not stored, only counted.
	 *
	 * @return the total number of documents.
	 */
	public abstract int totalNumberOfDocs();

	/**
	 * Return the number of hits counted so far.
	 *
	 * If you're retrieving hit in a background thread, call this
	 * method from another thread to get an update of the count so far.
	 *
	 * @return the current total hit count
	 */
	public abstract int countSoFarHitsCounted();

	/**
	 * Return the number of hits retrieved so far.
	 *
	 * If you're retrieving hits in a background thread, call this
	 * method from another thread to get an update of the count so far.
	 *
	 * @return the current total hit count
	 */
	public abstract int countSoFarHitsRetrieved();

	/**
	 * Return the number of documents counted so far.
	 *
	 * If you're retrieving hit in a background thread, call this
	 * method from another thread to get an update of the count so far.
	 *
	 * @return the current total hit count
	 */
	public abstract int countSoFarDocsCounted();

	/**
	 * Return the number of documents retrieved so far.
	 *
	 * If you're retrieving hits in a background thread, call this
	 * method from another thread to get an update of the count so far.
	 *
	 * @return the current total hit count
	 */
	public abstract int countSoFarDocsRetrieved();

	/**
	 * Check if we're done retrieving/counting hits.
	 *
	 * If you're retrieving hits in a background thread, call this
	 * method from another thread to check if all hits have been processed.
	 *
	 * @return true iff all hits have been retrieved/counted.
	 */
	public abstract boolean doneFetchingHits();

	/**
	 * Iterate over the hits in the original (pre-sort) order.
	 * @return an iterable object that will produce hits in the original order.
	 */
	public Iterable<Hit> hitsInOriginalOrder() {
		return new Iterable<Hit>() {
			@Override
			public Iterator<Hit> iterator() {
				return Hits.this.getIterator(true);
			}
		};
	}

	/**
	 * Return an iterator over these hits that produces the
	 * hits in their original order.
	 *
	 * @param originalOrder if true, returns hits in original order. If false,
	 *   returns them in sorted order (if any)
	 * @return the iterator
	 */
	public Iterator<Hit> getIterator(boolean originalOrder) {
		return new Iterator<Hit>() {
			int i = -1;

			@Override
			public boolean hasNext() {
				return i + 1 < size();
			}

			@Override
			public Hit next() {
				i++;
				return get(i);
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}

		};
	}

	/**
	 * Return an iterator over these hits.
	 *
	 * The order is the sorted order, not the original order. Use
	 * hitsInOriginalOrder() to iterate in the original order.
	 *
	 * @return the iterator
	 */
	@Override
	public Iterator<Hit> iterator() {
		return getIterator(false);
	}

	/**
	 * Return the specified hit number, based on the order they
	 * were originally found (not the sorted order).
	 *
	 * @param i
	 *            index of the desired hit
	 * @return the hit, or null if it's beyond the last hit
	 */
	public abstract Hit getByOriginalOrder(int i);

	/**
	 * Return the specified hit.
	 *
	 * @param i
	 *            index of the desired hit
	 * @return the hit, or null if it's beyond the last hit
	 */
	@Override
	public abstract Hit get(int i);

	/**
	 * Return the concordance for the specified hit.
	 *
	 * The first call to this method will fetch the concordances for all the hits in this
	 * Hits object. So make sure to select an appropriate HitsWindow first: don't call this
	 * method on a Hits set with >1M hits unless you really want to display all of them in one
	 * go.
	 *
	 * @param h the hit
	 * @return concordance for this hit
	 */
	public Concordance getConcordance(Hit h) {
		return getConcordance(h, desiredContextSize);
	}

	/**
	 * Return the KWIC for the specified hit.
	 *
	 * The first call to this method will fetch the KWICs for all the hits in this
	 * Hits object. So make sure to select an appropriate HitsWindow first: don't call this
	 * method on a Hits set with >1M hits unless you really want to display all of them in one
	 * go.
	 *
	 * @param h the hit
	 * @return KWIC for this hit
	 */
	public Kwic getKwic(Hit h) {
		return getKwic(h, desiredContextSize);
	}

	/**
	 * Retrieve a single concordance. Only use if you need a larger snippet around a single
	 * hit. If you need concordances for a set of hits, just instantiate a HitsWindow and call
	 * getConcordance() on that; it will fetch all concordances in the window in a batch, which
	 * is more efficient.
	 *
	 * @param fieldName field to use for building the concordance
	 * @param hit the hit for which we want a concordance
	 * @param contextSize the desired number of words around the hit
	 * @return the concordance
	 */
	public abstract Concordance getConcordance(String fieldName, Hit hit, int contextSize);

	/**
	 * Retrieve a single KWIC (KeyWord In Context). Only use if you need a larger
	 * snippet around a single
	 * hit. If you need KWICs for a set of hits, just instantiate a HitsWindow and call
	 * getKwic() on that; it will fetch all KWICs in the window in a batch, which
	 * is more efficient.
	 *
	 * @param fieldName field to use for building the KWIC
	 * @param hit the hit for which we want a KWIC
	 * @param contextSize the desired number of words around the hit
	 * @return the KWIC
	 */
	public abstract Kwic getKwic(String fieldName, Hit hit, int contextSize);

	/**
	 * Get a concordance with a custom context size.
	 *
	 * Don't call this directly for displaying a list of results. In that case,
	 * just instantiate a HitsWindow, call setContextSize() on it to set a
	 * default context size and call getConcordance(Hit) for each hit. That's
	 * more efficient if you're dealing with many hits.
	 *
	 * This method is mostly just for getting a larger snippet around
	 * a single hit.
	 *
	 * @param h the hit
	 * @param contextSize the context size for this concordance
	 *   (only use if you want a different one than the preset preference)
	 * @return concordance for this hit
	 */
	public abstract Concordance getConcordance(Hit h, int contextSize);

	/**
	 * Get a KWIC with a custom context size.
	 *
	 * Don't call this directly for displaying a list of results. In that case,
	 * just instantiate a HitsWindow, call setContextSize() on it to set a
	 * default context size and call getKwic(Hit) for each hit. That's
	 * more efficient if you're dealing with many hits.
	 *
	 * This method is mostly just for getting a larger snippet around
	 * a single hit.
	 *
	 * @param h the hit
	 * @param contextSize the context size for this KWIC
	 *   (only use if you want a different one than the preset preference)
	 * @return KWIC for this hit
	 */
	public abstract Kwic getKwic(Hit h, int contextSize);

	/**
	 * Retrieve context words for the hits.
	 *
	 * @param fieldProps
	 *            the field and properties to use for the context
	 */
	public abstract void findContext(List<String> fieldProps);

	/**
	 * Clear any cached concordances so new ones will be created on next call to getConcordance().
	 */
	public abstract void clearConcordances();

	/**
	 * Count occurrences of context words around hit.
	 *
	 * Uses the default contents field for collocations, and the default
	 * sensitivity settings.
	 *
	 * @return the frequency of each occurring token
	 */
	public TermFrequencyList getCollocations() {
		return getCollocations(null, null);
	}


	/**
	 * Count occurrences of context words around hit.
	 *
	 * @param propName the property to use for the collocations, or null if default
	 * @param ctx query execution context, containing the sensitivity settings
	 *
	 * @return the frequency of each occurring token
	 */
	public abstract TermFrequencyList getCollocations(String propName, QueryExecutionContext ctx);

	public abstract boolean hasCapturedGroups();

	/**
	 * Get the captured group information for this hit, if any.
	 *
	 * The names of the captured groups can be obtained through
	 * the getCapturedGroupNames() method.
	 *
	 * @param hit the hit to get captured group information for
	 * @return the captured group information, or null if none
	 */
	public abstract Span[] getCapturedGroups(Hit hit);

	/**
	 * Get the captured group name information.
	 *
	 * @return the captured group names, in index order
	 */
	public List<String> getCapturedGroupNames() {
		if (hitQueryContext == null)
			return null;
		return hitQueryContext.getCapturedGroupNames();
	}


	/**
	 * Get the captured group information in map form.
	 *
	 * Relatively slow; use getCapturedGroups() and getCapturedGroupNames()
	 * for a faster alternative.
	 *
	 * @param hit hit to get the captured group map for
	 * @return the captured group information map
	 */
	public abstract Map<String, Span> getCapturedGroupMap(Hit hit);

	/**
	 * Returns the searcher object.
	 *
	 * @return the searcher object.
	 */
	public Searcher getSearcher() {
		return searcher;
	}

	/**
	 * Returns the field to use for retrieving concordances.
	 *
	 * @return the field name
	 */
	public String getConcordanceFieldName() {
		return concordanceFieldName;
	}

	/**
	 * Sets the field to use for retrieving concordances.
	 *
	 * @param concordanceFieldName
	 *            the field name
	 */
	public void setConcordanceField(String concordanceFieldName) {
		this.concordanceFieldName = concordanceFieldName;
	}


	/**
	 * Get the field our current concordances were retrieved from
	 *
	 * @return the field name
	 */
	public abstract List<String> getContextFieldPropName();

	/**
	 * Retrieve a sublist of hits.
	 *
	 * If toIndex is beyond the last hit, will return a list up to and
	 * including the last hit.
	 *
	 * @param fromIndex first hit to include in the resulting list
	 * @param toIndex first hit not to include in the resulting list
	 * @return the sublist
	 * @deprecated use window()
	 */
	@Override
	@Deprecated
	public List<Hit> subList(int fromIndex, int toIndex) {
		return getHits().subList(fromIndex, toIndex);
	}


	/**
	 * Get a window into this list of hits.
	 *
	 * Use this if you're displaying part of the resultset, like
	 * in a paging interface. It makes sure BlackLab only works with
	 * the hits you want to display and doesn't do any unnecessary
	 * processing on the other hits.
	 *
	 * HitsWindow includes methods to assist with paging, like figuring
	 * out if there hits before or after the window.
	 *
	 * @param first first hit in the window (0-based)
	 * @param windowSize size of the window
	 * @return the window
	 */
	@SuppressWarnings("deprecation") // we'll make it package-private instead of removing the method
	public HitsWindow window(int first, int windowSize) {
		return new HitsWindow(this, first, windowSize);
	}

	/**
	 * Set the field properties to retrieve context from
	 * @param contextField the field properties
	 */
	public abstract void setContextField(List<String> contextField);

	/** @return the maximum number of hits to retrieve. */
	public int getMaxHitsToRetrieve() {
		return maxHitsToRetrieve;
	}

	/** Set the maximum number of hits to retrieve
	 * @param n the number of hits, or -1 for no limit
	 */
	public void setMaxHitsToRetrieve(int n) {
		this.maxHitsToRetrieve = n;
	}

	/** @return the maximum number of hits to count. */
	public int getMaxHitsToCount() {
		return maxHitsToCount;
	}

	/** Set the maximum number of hits to count
	 * @param n the number of hits, or -1 for no limit
	 */
	public void setMaxHitsToCount(int n) {
		this.maxHitsToCount = n;
	}

	/**
	 * Convenience method to get all hits in a single doc from a larger hitset.
	 *
	 * Don't use this for grouping or per-document results as it's relatively inefficient.
	 *
	 * @param docid the doc id to get hits for
	 * @return the list of hits in this doc (if any)
	 */
	public abstract Hits getHitsInDoc(int docid);

	/**
	 * Return the context(s) for the specified hit number
	 * @param hitNumber which hit we want the context(s) for
	 * @return the context(s)
	 */
	public abstract int[] getHitContext(int hitNumber);

	/**
	 * Indicate how to use the forward indices to build concordances.
	 *
	 * @param wordFI FI to use as the text content of the &lt;w/&gt; tags (default "word"; null for no text content)
	 * @param punctFI FI to use as the text content between &lt;w/&gt; tags (default "punct"; null for just a space)
	 * @param attrFI FIs to use as the attributes of the &lt;w/&gt; tags (null for all other FIs)
	 */
	public void setForwardIndexConcordanceParameters(String wordFI, String punctFI, Collection<String> attrFI) {
		concWordFI = wordFI;
		concPunctFI = punctFI;
		concAttrFI = attrFI;
	}

	/**
	 * Are we making concordances using the forward index (true) or using
	 * the content store (false)? Forward index is more efficient but returns
	 * concordances that don't include XML tags.
	 *
	 * @return true iff we use the forward index for making concordances.
	 */


	/**
	 * Are we making concordances using the forward index (true) or using
	 * the content store (false)? Forward index is more efficient but returns
	 * concordances that don't include XML tags.
	 *
	 * @return true iff we use the forward index for making concordances.
	 */
	public ConcordanceType getConcordanceType() {
		return concsType;
	}

	/**
	 * Do we want to retrieve concordances from the forward index or from the
	 * content store? Forward index is more efficient but doesn't exactly reproduces the
	 * original XML.
	 *
	 * The default type can be set by calling Searcher.setDefaultConcordanceType().
	 *
	 * @param type the type of concordances to make
	 */
	public void setConcordanceType(ConcordanceType type) {
		this.concsType = type;
	}

	public String getConcWordFI() {
		return concWordFI;
	}

	public String getConcPunctFI() {
		return concPunctFI;
	}

	public Collection<String> getConcAttrFI() {
		return concAttrFI;
	}

	public HitQueryContext getHitQueryContext() {
		return hitQueryContext;
	}

	protected void setHitQueryContext(HitQueryContext hitQueryContext) {
		this.hitQueryContext = hitQueryContext;
	}

}