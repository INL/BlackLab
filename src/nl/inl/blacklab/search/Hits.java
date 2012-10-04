/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.search;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import nl.inl.blacklab.search.grouping.HitProperty;
import nl.inl.blacklab.search.grouping.HitPropertyMultiple;

import org.apache.lucene.search.spans.Spans;

/**
 * Represents a list of Hit objects. Also maintains information about the context (concordance)
 * information stored in the Hit objects.
 */
public class Hits implements Iterable<Hit> {
	/** Don't actually use term vector for sorting/grouping, use content */
	private static final boolean AVOID_USING_TERM_VECTOR = false;

	/**
	 * The types of concordance information the hits may have.
	 */
	public static enum ConcType {
		/** Hits have no concordance information */
		NONE,

		/**
		 * Hits have concordance information from the term vector (Lucene index), useful for sorting
		 */
		TERM_VECTOR,

		/** Hits have actual content concordances (original XML content), useful for display */
		CONTENT
	}

	/**
	 * The hits.
	 */
	protected List<Hit> hits;

	/**
	 * The searcher object.
	 */
	protected Searcher searcher;

	/**
	 * If we have concordance information, this specifies the field the concordances came from.
	 */
	protected String concFieldName;

	/**
	 * The default field to use for retrieving concordance information.
	 */
	protected String defaultConcField;

	/**
	 * The type of concordance information our hits currently have.
	 */
	protected ConcType concType = ConcType.NONE;

	/**
	 * Construct an empty Hits object
	 *
	 * @param searcher
	 *            the searcher object
	 * @param defaultConcField
	 *            field to use by default when finding concordances
	 */
	public Hits(Searcher searcher, String defaultConcField) {
		this.searcher = searcher;
		hits = new ArrayList<Hit>();
		this.defaultConcField = defaultConcField;
	}

	/**
	 * Construct an empty Hits object
	 *
	 * @param searcher
	 *            the searcher object
	 * @param source
	 *            where to retrieve the Hit objects from
	 * @param defaultConcField
	 *            field to use by default when finding concordances
	 */
	public Hits(Searcher searcher, Spans source, String defaultConcField) {
		this.searcher = searcher;
		hits = Hit.hitList(source);
		this.defaultConcField = defaultConcField;
	}

	/**
	 * Get the list of hits.
	 *
	 * @return the list of hits
	 */
	public List<Hit> getHits() {
		return Collections.unmodifiableList(hits);
	}

	/**
	 * Sort the list of hits.
	 *
	 * @param sortProp
	 *            the hit property/properties to sort on
	 * @param reverseSort
	 *            if true, sort in descending order
	 */
	public void sort(HitProperty[] sortProp, boolean reverseSort) {
		if (sortProp.length == 1)
			sort(sortProp[0], reverseSort);
		else
			sort(new HitPropertyMultiple(sortProp), reverseSort);
	}

	/**
	 * Sort the list of hits.
	 *
	 * @param sortProp
	 *            the hit property/properties to sort on
	 */
	public void sort(HitProperty... sortProp) {
		sort(sortProp, false);
	}

	/**
	 * Sort the list of hits.
	 *
	 * @param sortProp
	 *            the hit property to sort on
	 * @param reverseSort
	 *            if true, sort in descending order
	 */
	public void sort(HitProperty sortProp, boolean reverseSort) {
		// Do we need concordances and don't we have them yet?
		Collator collator;
		if (sortProp.needsConcordances() && concType == ConcType.NONE) {
			// Get 'em
			findConcordances(true);

			// Context needs to be sorted per-word. Get the appropriate collator.
			collator = searcher.getPerWordCollator();
		} else {
			// For other sorting tasks, use the regular collator.
			collator = searcher.getCollator();
		}

		for (Hit hit : hits) {
			hit.sort = collator.getCollationKey(sortProp.get(hit));
		}

		// Sort on the hits' sort property
		Comparator<Object> comparator;

		// NOTE: we define two separate Comparators for normal and reverse sort
		// because this method gets called a LOT for large result sets, so
		// saving one if/then/else is worth it.

		// NOTE2: we use Comparator<Object> and not Comparator<Hit> because this is
		// significantly faster when doing many comparisons, presumably because less
		// runtime type checking is done. We know all our objects our Hits, so this
		// is okay.
		if (reverseSort) {
			comparator = new Comparator<Object>() {
				@Override
				public int compare(Object o1, Object o2) {
					return ((Hit) o2).sort.compareTo(((Hit) o1).sort);
				}
			};
		} else {
			comparator = new Comparator<Object>() {
				@Override
				public int compare(Object o1, Object o2) {
					return ((Hit) o1).sort.compareTo(((Hit) o2).sort);
				}
			};
		}
		Collections.sort(hits, comparator);

		// Clear the collation keys to free up memory
		for (Hit hit : hits) {
			hit.sort = null;
		}
	}

	/**
	 * Add a hit to the list
	 *
	 * @param hit
	 *            the hit
	 */
	public void add(Hit hit) {
		hits.add(hit);
	}

	/**
	 * Return the number of hits.
	 *
	 * @return the number of hits
	 */
	public int size() {
		return hits.size();
	}

	/**
	 * Return an iterator over these hits.
	 *
	 * @return the iterator
	 */
	@Override
	public Iterator<Hit> iterator() {
		return hits.iterator();
	}

	/**
	 * Return the specified hit.
	 *
	 * @param i
	 *            index of the desired hit
	 * @return the hit
	 */
	public Hit get(int i) {
		return hits.get(i);
	}

	/**
	 * Retrieve concordances for the hits.
	 *
	 * @param fieldName
	 *            the field to use for the concordances (ignore the default concordance field)
	 * @param useTermVector
	 *            if true, retrieves concordances using the Lucene term vector instead of the
	 *            original content. Faster, used for sorting.
	 */
	public void findConcordances(String fieldName, boolean useTermVector) {
		// Make sure we don't have the desired concordances already
		if (concType != ConcType.NONE && fieldName.equals(concFieldName)) {
			if (concType == ConcType.TERM_VECTOR && useTermVector || concType == ConcType.CONTENT
					&& !useTermVector)
				return;
		}

		// Get the concordances
		searcher.retrieveConcordances(fieldName, useTermVector, hits);

		concFieldName = fieldName;
		concType = useTermVector ? ConcType.TERM_VECTOR : ConcType.CONTENT;
	}

	/**
	 * Retrieve concordances for the hits.
	 *
	 * Uses the default concordance field.
	 *
	 * @param useTermVector
	 *            if true, retrieves concordances using the Lucene term vector instead of the
	 *            original content. Faster, used for sorting.
	 */
	public void findConcordances(boolean useTermVector) {
		findConcordances(defaultConcField, AVOID_USING_TERM_VECTOR ? false : useTermVector);
	}

	/**
	 * Retrieve concordances for the hits.
	 *
	 * Uses the default concordance field.
	 */
	public void findConcordances() {
		findConcordances(defaultConcField, false);
	}

	/**
	 * Returns the searcher object.
	 *
	 * @return the searcher object.
	 */
	public Searcher getSearcher() {
		return searcher;
	}

	/**
	 * Returns the default field to use for retrieving concordances.
	 *
	 * @return the field name
	 */
	public String getDefaultConcordanceField() {
		return defaultConcField;
	}

	/**
	 * Sets the default field to use for retrieving concordances.
	 *
	 * @param defaultConcField
	 *            the field name
	 */
	public void setDefaultConcordanceField(String defaultConcField) {
		this.defaultConcField = defaultConcField;
	}

	/**
	 * Get the field our current concordances were retrieved from
	 *
	 * @return the field name
	 */
	public String getConcordanceField() {
		return concFieldName;
	}

	/**
	 * Get type of the current concordance information in our hits.
	 *
	 * @return the concordance type
	 */
	public ConcType getConcordanceType() {
		return concType;
	}

	/**
	 * Set the current concordance type and field for our hits.
	 *
	 * @param concField
	 *            the field name
	 * @param concType
	 *            the type of concordances
	 */
	public void setConcordanceStatus(String concField, ConcType concType) {
		concFieldName = concField;
		this.concType = concType;
	}
}
