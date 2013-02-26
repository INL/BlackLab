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
package nl.inl.blacklab.perdocument;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import nl.inl.blacklab.search.Hit;
import nl.inl.blacklab.search.Hits;
import nl.inl.blacklab.search.Searcher;
import nl.inl.util.ReverseComparator;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.spans.SpanQuery;

/**
 * A list of DocResult objects (document-level query results). The list may be sorted by calling
 * DocResults.sort().
 */
public class DocResults {
	protected List<DocResult> results = new ArrayList<DocResult>();

	private Searcher searcher;

	public Searcher getSearcher() {
		return searcher;
	}

	public void add(DocResult r) {
		results.add(r);
	}

	public DocResults(Searcher searcher, Hits hits) {
		this.searcher = searcher;
		try {
			// Fill list of document results
			int doc = -1;
			DocResult dr = null;
			IndexReader indexReader = searcher.getIndexReader();
			for (Hit hit : hits) {
				if (hit.doc != doc) {
					if (dr != null)
						results.add(dr);
					doc = hit.doc;
					dr = new DocResult(searcher, hits.getConcordanceFieldName(), hit.doc,
							indexReader.document(hit.doc));
					dr.setContextField(hits.getContextFieldPropName()); // make sure we remember what kind of
													// context we have, if any
				}
				dr.addHit(hit);
			}
			// add the final dr instance to the results collection
			if (dr != null)
				results.add(dr);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public DocResults(Searcher searcher, String field, SpanQuery query) {
		this(searcher, new Hits(searcher, field, query));
	}

	public DocResults(Searcher searcher, Scorer sc) {
		this.searcher = searcher;
		if (sc == null)
			return; // no matches, empty result set
		try {
			IndexReader indexReader = searcher.getIndexReader();
			while (true) {
				int docId;
				try {
					docId = sc.nextDoc();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
				if (docId == DocIdSetIterator.NO_MORE_DOCS)
					break;

				Document d = indexReader.document(docId);
				DocResult dr = new DocResult(searcher, null, docId, d, sc.score());
				results.add(dr);
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public DocResults(Searcher searcher, Query query) {
		this(searcher, searcher.findDocScores(query));
	}

	public DocResults(Searcher searcher) {
		this.searcher = searcher;
	}

	public List<DocResult> getResults() {
		return results;
	}

	/**
	 * Sort the results using the given comparator.
	 *
	 * @param comparator
	 *            how to sort the results
	 */
	void sort(Comparator<DocResult> comparator) {
		Collections.sort(results, comparator);
	}

	public int size() {
		return results.size();
	}

	public void sort(DocProperty prop, boolean sortReverse) {
		Comparator<DocResult> comparator = new ComparatorDocProperty(prop);
		if (sortReverse) {
			comparator = new ReverseComparator<DocResult>(comparator);
		}
		Collections.sort(results, comparator);
	}

}
