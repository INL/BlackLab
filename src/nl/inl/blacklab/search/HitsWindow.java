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

/**
 * Represents a subset of a Hits object, for example a page of hits.
 */
public class HitsWindow extends Hits {
	/**
	 * Number of hits in the window
	 */
	private int windowSize;

	/**
	 * First hit in the window
	 */
	private int first;

	/**
	 * The source hits into which this is a window
	 */
	private Hits source;

	/**
	 * Construct a HitsWindow object.
	 *
	 * @param source
	 *            the larger Hits object we would like a window into
	 * @param first
	 *            the first hit in our window
	 * @param windowSize
	 *            the size of our window
	 */
	public HitsWindow(Hits source, int first, int windowSize) {
		super(source.searcher, source.concordanceField);
		this.source = source;
		this.first = first;
		this.windowSize = windowSize;
		this.contextFieldName = source.contextFieldName;

		// Error if first out of range
		if (first < 0 || (source.size() == 0 && first > 0)
				|| (source.size() > 0 && first >= source.size()))
			throw new RuntimeException("First hit out of range");

		// Auto-clamp number
		int number = windowSize;
		if (first + number > source.size())
			number = source.size() - first;

		// Make sublist
		hits = source.subList(first, first + number);
	}

	/**
	 * Return the concordance for the specified hit.
	 *
	 * For HitsWindow, this method will automatically fetch concordances
	 * if they haven't been fetched yet.
	 *
	 * @param h the hit
	 * @return concordance for this hit
	 */
	@Override
	public Concordance getConcordance(Hit h) {
		if (concordances == null)
			findConcordances(); // Automatically find concordances in HitsWindow
		return super.getConcordance(h);
	}

	/**
	 * Are there more hits in the original Hits object beyond our window?
	 *
	 * @return true if there are, false if not.
	 */
	public boolean hasNext() {
		return first + windowSize < source.size();
	}

	/**
	 * Are there more hits in the original Hits object "to the left" of our window?
	 *
	 * @return true if there are, false if not.
	 */
	public boolean hasPrevious() {
		return first > 0;
	}

	/**
	 * Where would the next window start?
	 *
	 * @return index of the first hit beyond our window
	 */
	public int nextFrom() {
		return first + hits.size();
	}

	/**
	 * Where would the previous window start?
	 *
	 * @return index of the start hit for the previous page
	 */
	public int prevFrom() {
		return first - windowSize;
	}

	/**
	 * What's the first in the window?
	 *
	 * @return index of the first hit
	 */
	public int first() {
		return first;
	}

	/**
	 * What's the last in the window?
	 *
	 * @return index of the last hit
	 */
	public int last() {
		return first + hits.size() - 1;
	}

	/**
	 * How many hits are in the window?
	 *
	 * Note that this may be different from the window size, as the window may not be full.
	 *
	 * @return the number of hits
	 */
	public int number() {
		return hits.size();
	}

	/**
	 * How many hits are in the original source Hits object?
	 *
	 * @return total number of hits
	 */
	public int totalHits() {
		return source.size();
	}

}
