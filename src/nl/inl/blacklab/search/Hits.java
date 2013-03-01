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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.search.grouping.HitProperty;
import nl.inl.blacklab.search.grouping.HitPropertyMultiple;
import nl.inl.util.StringUtil;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BooleanQuery.TooManyClauses;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.Spans;

/**
 * Represents a list of Hit objects. Also maintains information about the context (concordance)
 * information stored in the Hit objects.
 */
public class Hits implements Iterable<Hit> {

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
	 * If we have context information, this specifies the property (i.e. word, lemma, pos) the context came from.
	 * Otherwise, it is null.
	 */
	protected String contextFieldPropName;

	/**
	 * The default field to use for retrieving concordance information.
	 */
	protected String concordanceFieldName;

	/**
	 * Lucene name for the main property field of the current contents field.
	 */
	private String concordanceMainFieldPropName;

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
	 * For extremely large queries, stop retrieving hits after this number.
	 */
	public final static int MAX_HITS_TO_RETRIEVE = 10000000;

	/**
	 * For extremely large queries, stop retrieving hits at some point.
	 */
	private boolean tooManyHits = false;

	/**
	 * The desired context size (number of words to fetch around hits).
	 * Defaults to Searcher.getDefaultContextSize().
	 */
	private int desiredContextSize;

	/**
	 * The current context size (number of words around hits we now have).
	 */
	private int currentContextSize;

	/**
	 * Construct an empty Hits object
	 *
	 * @param searcher
	 *            the searcher object
	 */
	public Hits(Searcher searcher) {
		this(searcher, searcher.getContentsFieldMainPropName());
	}

	/**
	 * Construct an empty Hits object
	 *
	 * @param searcher
	 *            the searcher object
	 * @param concordanceFieldPropName
	 *            field to use by default when finding concordances
	 */
	public Hits(Searcher searcher, String concordanceFieldPropName) {
		this.searcher = searcher;
		hits = new ArrayList<Hit>();
		totalNumberOfHits = 0;
		setConcordanceField(concordanceFieldPropName);
		desiredContextSize = searcher == null ? 0 /* only for test */ : searcher.getDefaultContextSize();
		currentContextSize = -1;
	}

	/**
	 * Construct an empty Hits object.
	 *
	 * If possible, don't use this constructor, use the one that takes
	 * a SpanQuery, as it's more efficient.
	 *
	 * @param searcher
	 *            the searcher object
	 * @param concordanceFieldPropName
	 *            field to use by default when finding concordances
	 * @param source
	 *            where to retrieve the Hit objects from
	 * @deprecated supply a SpanQuery to a Hits object instead
	 */
	@Deprecated
	Hits(Searcher searcher, String concordanceFieldPropName, Spans source) {
		this(searcher, concordanceFieldPropName);

		totalNumberOfHits = -1; // "not known yet"
		sourceSpans = source;
		sourceSpansFullyRead = false;
	}

	/**
	 * Construct an empty Hits object
	 *
	 * @param searcher
	 *            the searcher object
	 * @param concordanceFieldPropName
	 *            field to use by default when finding concordances
	 * @param sourceQuery
	 *            the query to execute to get the hits
	 * @throws TooManyClauses if the query is overly broad (expands to too many terms)
	 */
	public Hits(Searcher searcher, String concordanceFieldPropName, SpanQuery sourceQuery) throws TooManyClauses {
		this(searcher, concordanceFieldPropName);

		sourceSpans = findSpans(sourceQuery);

		// Count how many hits there are in total
		try {
			tooManyHits = false;
			while (sourceSpans.next()) {
				totalNumberOfHits++;
				if (totalNumberOfHits >= MAX_HITS_TO_RETRIEVE) {
					// Too many hits; stop collecting here
					tooManyHits = true;
					break;
				}
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		sourceSpans = findSpans(sourceQuery); // Counted 'em. Now reset.
		sourceSpansFullyRead = false;
	}

	/**
	 * Construct an empty Hits object
	 *
	 * @param searcher
	 *            the searcher object
	 * @param sourceQuery
	 *            the query to execute to get the hits
	 * @throws TooManyClauses if the query is overly broad (expands to too many terms)
	 */
	public Hits(Searcher searcher, SpanQuery sourceQuery) throws TooManyClauses {
		this(searcher, searcher.getContentsFieldMainPropName(), sourceQuery);
	}

	/** Returns the context size.
	 * @return context size (number of words to fetch around hits)
	 */
	public int getContextSize() {
		return desiredContextSize;
	}

	/** Sets the desired context size.
	 * @param contextSize the context size (number of words to fetch around hits)
	 */
	public void setContextSize(int contextSize) {
		if (this.desiredContextSize == contextSize)
			return; // no need to reset anything
		this.desiredContextSize = contextSize;

		// Reset context and concordances so we get the correct context size next time
		currentContextSize = -1;
		contextFieldPropName = null;
		concordances = null;
	}

	/**
	 * Executes the SpanQuery to get a Spans object.
	 *
	 * @param spanQuery
	 *            the query
	 * @return the results object
	 * @throws BooleanQuery.TooManyClauses
	 *             if a wildcard or regular expression term is overly broad
	 */
	Spans findSpans(SpanQuery spanQuery) throws BooleanQuery.TooManyClauses {
		try {
			IndexReader reader = null;
			if (searcher != null) { // this may happen while testing with stub classes; don't try to rewrite
				reader = searcher.getIndexReader();
			}
			spanQuery = (SpanQuery) spanQuery.rewrite(reader);
			return spanQuery.getSpans(reader);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Were all hits retrieved, or did we stop because there were too many?
	 * @return true if all hits were retrieved
	 */
	public boolean tooManyHits() {
		return tooManyHits;
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
				if (totalNumberOfHits >= 0 && hits.size() >= totalNumberOfHits) {
					// Either we've got them all, or we should stop
					// collecting them because there's too many
					break;
				}
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
		// Do we need context and don't we have it yet?
		String requiredContext = sortProp.needsContext();
		if (requiredContext != null && (!requiredContext.equals(contextFieldPropName) || currentContextSize != desiredContextSize)) {
			// Get 'em
			findContext(requiredContext);
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
	 * The first call to this method will fetch the concordances for all the hits in this
	 * Hits object. So make sure to select an appropriate HitsWindow first: don't call this
	 * method on a Hits set with >1M hits unless you really want to display all of them in one
	 * go.
	 *
	 * @param h the hit
	 * @return concordance for this hit
	 */
	public Concordance getConcordance(Hit h) {
		ensureAllHitsRead();
		if (concordances == null) {
			findConcordances(); // just try to find the default concordances
		}
		Concordance conc = concordances.get(h);
		if (conc == null)
			throw new RuntimeException("Concordance for hit not found: " + h);
		return conc;
	}

	/**
	 * Retrieve concordances for the hits.
	 *
	 * You shouldn't have to call this manually, as it's automatically called when
	 * you call getConcordance() for the first time.
	 */
	void findConcordances() {
		ensureAllHitsRead();
		// Make sure we don't have the desired concordances already
		if (concordances != null) {
			return;
		}

		// Get the concordances
		concordances = searcher.retrieveConcordances(concordanceFieldName, hits, desiredContextSize);
	}

	/**
	 * Retrieve context words for the hits.
	 *
	 * @param fieldPropName
	 *            the field and property to use for the concordances
	 */
	public void findContext(String fieldPropName) {
		ensureAllHitsRead();
		// Make sure we don't have the desired concordances already
		if (contextFieldPropName != null && fieldPropName.equals(contextFieldPropName) && desiredContextSize == currentContextSize) {
			return;
		}

		// Get the concordances
		searcher.retrieveContext(fieldPropName, hits, desiredContextSize);
		currentContextSize = desiredContextSize;

		contextFieldPropName = fieldPropName;
	}

	/**
	 * Retrieve context for the hits, for sorting/grouping.
	 *
	 * NOTE: you should never have to call this manually; it is
	 * called if needed by the sorting/grouping code.
	 *
	 * Uses the main property field.
	 */
	void findContext() {
		findContext(concordanceMainFieldPropName);
	}

	/**
	 * Clear any cached concordances so new ones will be created on next call to getConcordance().
	 */
	public void clearConcordances() {
		concordances = null;
	}

	/**
	 * Clear any cached concordances so new ones will be created when necessary.
	 */
	public void clearContext() {
		for (Hit hit: hits) {
			hit.context = null;
		}
		contextFieldPropName = null;
	}

	/**
	 * Count occurrences of context words around hit.
	 *
	 * Uses the default contents field for collocations.
	 */
	public TokenFrequencyList getCollocations() {
		return getCollocations(null, null);
	}

	/**
	 * Count occurrences of context words around hit.
	 *
	 * @param propName the property to use for the collocations, or null if default
	 * @param ctx query execution context, containing the sensitivity settings
	 */
	public TokenFrequencyList getCollocations(String propName, QueryExecutionContext ctx) {
		findContext(ctx.luceneField(false));
		Map<Integer, Integer> coll = new HashMap<Integer, Integer>();
		for (Hit hit: hits) {
			int[] context = hit.context;

			// Count words
			for (int i = 0; i < context.length; i++) {
				if (i >= hit.contextHitStart && i < hit.contextRightStart)
					continue; // don't count words in hit itself, just around
				int w = context[i];
				Integer n = coll.get(w);
				if (n == null)
					n = 1;
				else
					n++;
				coll.put(w, n);
			}
		}

		// Get the actual words from the sort positions
		boolean caseSensitive = searcher.isDefaultSearchCaseSensitive();
		boolean diacSensitive = searcher.isDefaultSearchDiacriticsSensitive();
		TokenFrequencyList collocations = new TokenFrequencyList(coll.size());
		//Map<String, Integer> collStr = new HashMap<String, Integer>();
		Terms terms = searcher.getTerms(contextFieldPropName);
		for (Map.Entry<Integer, Integer> e: coll.entrySet()) {
			String word = terms.getFromSortPosition(e.getKey());
			if (!diacSensitive) {
				word = StringUtil.removeAccents(word);
			}
			if (!caseSensitive) {
				word = word.toLowerCase();
			}
			collocations.add(new TokenFrequency(word, e.getValue()));
		}
		return collocations;
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
		if (searcher == null) {
			// Can occur during testing. Just use default main property name.
			concordanceMainFieldPropName = ComplexFieldUtil.propertyField(concordanceFieldName, ComplexFieldUtil.getDefaultMainPropName());
		}
		else {
			// Get the main property name from the index structure.
			concordanceMainFieldPropName = ComplexFieldUtil.mainPropertyField(searcher.getIndexStructure(), concordanceFieldName);
		}
	}

	/**
	 * Get the field our current concordances were retrieved from
	 *
	 * @return the field name
	 */
	public String getContextFieldPropName() {
		return contextFieldPropName;
	}

	/**
	 * Retrieve a sublist of hits.
	 * @param fromIndex first hit to include in the resulting list
	 * @param toIndex first hit not to include in the resulting list
	 * @return the sublist
	 */
	public List<Hit> subList(int fromIndex, int toIndex) {
		ensureHitsRead(toIndex - 1);
		return hits.subList(fromIndex, toIndex);
	}

	public void setContextField(String contextField) {
		this.contextFieldPropName = contextField;
	}
}
