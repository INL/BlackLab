/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.perdocument;

/**
 * A list of DocResult objects (document-level query results). The list may be sorted by calling
 * DocResults.sort().
 */
public class DocResultsWindow extends DocResults {
	private DocResults source;

	private int first;

	private int numberPerPage;

	public DocResultsWindow(DocResults source, int first, int numberPerPage) {
		super(source.getSearcher());
		this.source = source;
		this.first = first;
		this.numberPerPage = numberPerPage;

		if (source.size() > 0) {
			if (first < 0 || (source.size() > 0 && first >= source.size()))
				throw new RuntimeException("First hit out of range");

			int number = numberPerPage;
			if (first + number > source.size())
				number = source.size() - first;
			results = source.getResults().subList(first, first + number);
		}
	}

	public boolean hasNext() {
		return first + numberPerPage < source.size();
	}

	public boolean hasPrevious() {
		return first > 0;
	}

	public int nextFrom() {
		return first + results.size();
	}

	public int prevFrom() {
		return first - numberPerPage;
	}

	public int first() {
		return first;
	}

	public int last() {
		return first + results.size() - 1;
	}

	public int number() {
		return results.size();
	}

	public int totalResults() {
		return source.size();
	}

}
