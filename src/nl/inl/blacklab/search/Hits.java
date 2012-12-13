/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package nl.inl.blacklab.search;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import nl.inl.blacklab.search.grouping.HitProperty;
import nl.inl.blacklab.search.grouping.HitPropertyMultiple;

import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.Spans;

/**
 * Represents a list of Hit objects. Also maintains information about the context (concordance)
 * information stored in the Hit objects.
 */
public class Hits implements Iterable<Hit> {
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
	 * The concordances, if they have been retrieved.
	 */
	protected Map<Hit, Concordance> concordances;

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
	 * Did we completely read our Spans object?
	 */
	protected boolean sourceSpansFullyRead = true;

	/**
	 * Our Spans object, which may not have been fully read yet.
	 */
	protected Spans sourceSpans;

	/**
	 * How many hits do we have in total?
	 * (We keep this separately because we may run through a Spans first to
	 * count the hits without storing them, to avoid unnecessarily instantiating
	 * Hit objects)
	 */
	private int totalNumberOfHits;

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
		totalNumberOfHits = 0;
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
		sourceSpans = source;
		sourceSpansFullyRead = false;
		totalNumberOfHits = -1; // unknown
		hits = new ArrayList<Hit>(); //Hit.hitList(source);
		this.defaultConcField = defaultConcField;
	}

	/**
	 * Construct an empty Hits object
	 *
	 * @param searcher
	 *            the searcher object
	 * @param sourceQuery
	 *            the query to execute to get the hits
	 * @param defaultConcField
	 *            field to use by default when finding concordances
	 */
	public Hits(Searcher searcher, SpanQuery sourceQuery, String defaultConcField) {
		this.searcher = searcher;
		sourceSpans = searcher.findSpans(sourceQuery);
		totalNumberOfHits = 0;
		try {
			while (sourceSpans.next()) {
				totalNumberOfHits++;
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		sourceSpans = searcher.findSpans(sourceQuery); // Counted 'em. Now reset.
		sourceSpansFullyRead = false;
		hits = new ArrayList<Hit>(); //Hit.hitList(source);
		this.defaultConcField = defaultConcField;
	}

	/**
	 * Get the list of hits.
	 *
	 * @return the list of hits
	 * @deprecated Breaks optimizations. Use iteration or subList() instead.
	 */
	@Deprecated
	public List<Hit> getHits() {
		ensureAllHitsRead();
		return Collections.unmodifiableList(hits);
	}

	/**
	 * If we still have only partially read our Spans object,
	 * read the rest of it and add all the hits.
	 */
	private void ensureAllHitsRead() {
		if (sourceSpansFullyRead)
			return;
		sourceSpansFullyRead = true;

		try {
			while (sourceSpans.next()) {
				hits.add(Hit.getHit(sourceSpans));
			}
			totalNumberOfHits = hits.size();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * If we still have only partially read our Spans object,
	 * read the rest of it and add all the hits.
	 */
	private void ensureHitsRead(int index) {
		if (sourceSpansFullyRead)
			return;

		try {
			while (hits.size() <= index) {
				if (!sourceSpans.next()) {
					sourceSpansFullyRead = true;
					break;
				}
				hits.add(Hit.getHit(sourceSpans));
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
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
	public void sort(final HitProperty sortProp, boolean reverseSort) {
		ensureAllHitsRead();
		// Do we need concordances and don't we have them yet?
		//Collator collator;
		if (sortProp.needsConcordances() && concType == ConcType.NONE) {
			// Get 'em
			findContext();

			// Context needs to be sorted per-word. Get the appropriate collator.
			//collator = searcher.getPerWordCollator();
		} else {
			// For other sorting tasks, use the regular collator.
			//collator = searcher.getCollator();
		}

//		for (Hit hit : hits) {
//			hit.sort = collator.getCollationKey(sortProp.get(hit));
//		}

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
					return sortProp.compare((Hit) o2, (Hit) o1);
					//return ((Hit) o2).sort.compareTo(((Hit) o1).sort);
				}
			};
		} else {
			comparator = new Comparator<Object>() {
				@Override
				public int compare(Object o1, Object o2) {
					return sortProp.compare((Hit) o1, (Hit) o2);
					//return ((Hit) o1).sort.compareTo(((Hit) o2).sort);
				}
			};
		}
		Collections.sort(hits, comparator);

//		// Clear the collation keys to free up memory
//		for (Hit hit : hits) {
//			hit.sort = null;
//		}
	}

	/**
	 * Add a hit to the list
	 *
	 * @param hit
	 *            the hit
	 */
	public void add(Hit hit) {
		ensureAllHitsRead();
		hits.add(hit);
		totalNumberOfHits++;
	}

	/**
	 * Return the number of hits.
	 *
	 * @return the number of hits
	 */
	public int size() {
		if (totalNumberOfHits >= 0)
			return totalNumberOfHits; // fully known, or pre-counted

		// Probably not all hits have been seen yet. Collect them all.
		ensureAllHitsRead();
		return totalNumberOfHits;
	}

	/**
	 * Return an iterator over these hits.
	 *
	 * @return the iterator
	 */
	@Override
	public Iterator<Hit> iterator() {
//		ensureSpansRead();
//		return hits.iterator();

//		while (sourceSpans.next()) {
//			hits.add(Hit.getHit(sourceSpans));
//		}

		// Construct a custom iterator that iterates over the hits in the hits
		// list, but can also take into account the Spans object that may not have
		// been fully read. This ensures we don't instantiate Hit objects for all hits
		// if we just want to display the first few.
		return new Iterator<Hit>() {

			int index = -1;

			@Override
			public boolean hasNext() {
				// Do we still have hits in the hits list?
				if (index + 1 >= hits.size()) {
					// No; are there more hits to be read in the Spans?
					if (sourceSpansFullyRead)
						return false;
					try {
						if (!sourceSpans.next())
							return false;
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
					// Yep; add hit from Spans to hits list and position the iterator correctly
					hits.add(Hit.getHit(sourceSpans));
					index = hits.size() - 2;
				}
				return true;
			}

			@Override
			public Hit next() {
				// Check if there is a next, taking unread hits from Spans into account
				if (hasNext()) {
					index++;
					return hits.get(index);
				}
				throw new NoSuchElementException();
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}

		};
	}

	/**
	 * Return the specified hit.
	 *
	 * @param i
	 *            index of the desired hit
	 * @return the hit
	 */
	public Hit get(int i) {
		ensureHitsRead(i);
		return hits.get(i);
	}

	/**
	 * Return the concordance for the specified hit.
	 *
	 * You can only call this method after fetching concordances using Hits.findConcordances().
	 *
	 * @param h the hit
	 * @return concordance for this hit
	 */
	public Concordance getConcordance(Hit h) {
		ensureAllHitsRead();
		if (concordances == null)
			throw new RuntimeException("Concordances haven't been retrieved yet; call Hits.findConcordances()");
		Concordance conc = concordances.get(h);
		if (conc == null)
			throw new RuntimeException("Concordance for hit not found: " + h);
		return conc;
	}

	/**
	 * Retrieve concordances for the hits.
	 *
	 * @param fieldName
	 *            the field to use for the concordances (ignore the default concordance field)
	 */
	public void findConcordances(String fieldName) {
		ensureAllHitsRead();
		// Make sure we don't have the desired concordances already
		if (concordances != null && fieldName.equals(concFieldName)) {
			return;
		}
//		if (concType != ConcType.NONE && fieldName.equals(concFieldName)) {
//			if (concType == ConcType.CONTENT)
//				return;
//		}

		// Get the concordances
		concordances = searcher.retrieveConcordances(fieldName, hits);
		concFieldName = fieldName;
		//concType = ConcType.CONTENT;
	}

	/**
	 * Retrieve context words for the hits.
	 *
	 * @param fieldName
	 *            the field to use for the concordances (ignore the default concordance field)
	 */
	public void findContext(String fieldName) {
		ensureAllHitsRead();
		// Make sure we don't have the desired concordances already
		if (concType != ConcType.NONE && fieldName.equals(concFieldName)) {
			if (concType == ConcType.TERM_VECTOR)
				return;
		}

		// Get the concordances
		searcher.retrieveContext(fieldName, hits);

		concFieldName = fieldName;
		concType = ConcType.TERM_VECTOR;
	}

	/**
	 * Retrieve concordances for the hits.
	 *
	 * Uses the default concordance field.
	 */
	public void findConcordances() {
		findConcordances(defaultConcField);
	}

	/**
	 * Retrieve concordances for the hits.
	 *
	 * Uses the default concordance field.
	 */
	public void findContext() {
		findContext(defaultConcField);
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

	public List<Hit> subList(int fromIndex, int toIndex) {
		ensureHitsRead(toIndex - 1);
		return hits.subList(fromIndex, toIndex);
	}
}
