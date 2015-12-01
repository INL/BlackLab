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
package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Collection;

import nl.inl.blacklab.search.Span;
import nl.inl.blacklab.search.sequences.PerDocumentSortedSpans;

import org.apache.lucene.search.spans.Spans;
import org.apache.lucene.search.spans.TermSpans;


/**
 * Wrap a "simple" Spans object in a BLSpans object. It will
 * give the guarantees appropriate for single-term Spans like
 * that of SpanTermQuery, SpanRegexQuery, etc.
 */
public class BLSpansWrapper extends BLSpans {

	private Spans source;

	public BLSpansWrapper(Spans source) {
		if (source == null)
			throw new RuntimeException("Cannot wrap null Spans!");

		if (!(source instanceof TermSpans)) {
			// For anything but the very basic TermSpans,
			// this wrapper shouldn't be used anymore because everything is already BLSpans.
			// (which is needed for token tagging)
			// Just to make sure, print an error for now (will upgrade to
			// throwing an exception in the future)
			System.err.println("### BLSpansWrapper: " + source + " ###");
		}

		this.source = source;
	}

	@Override
	public boolean equals(Object obj) {
		return source.equals(obj);
	}

	@Override
	public Collection<byte[]> getPayload() throws IOException {
		return source.getPayload();
	}

	@Override
	public int hashCode() {
		return source.hashCode();
	}

	@Override
	public boolean isPayloadAvailable() throws IOException {
		return source.isPayloadAvailable();
	}

	@Override
	public String toString() {
		return source.toString();
	}

	public static BLSpans optWrap(Spans spans) {
		if (spans == null)
			return null;
		if (spans instanceof BLSpans)
			return (BLSpans)spans;
		return new BLSpansWrapper(spans);
	}

	public static BLSpans optWrapSort(Spans spans) {
		BLSpans result;
		if (spans instanceof BLSpans)
			result = (BLSpans)spans;
		else
			result = new BLSpansWrapper(spans);
		if (!result.hitsStartPointSorted())
			result = new PerDocumentSortedSpans(result, false, false);
		return result;
	}

	public static BLSpans optWrapSortUniq(Spans spans) {
		BLSpans result;
		if (spans instanceof BLSpans)
			result = (BLSpans)spans;
		else
			result = new BLSpansWrapper(spans);
		if (result.hitsStartPointSorted()) {
			if (result.hitsAreUnique()) {
				return result;
			}
			return new SpansUnique(result);
		}
		return new PerDocumentSortedSpans(result, false, !result.hitsAreUnique());
	}

	@Override
	public void passHitQueryContextToClauses(HitQueryContext context) {
		if (source instanceof BLSpans) // shouldn't happen, but ok
			((BLSpans) source).setHitQueryContext(context);
	}

	@Override
	public void getCapturedGroups(Span[] capturedGroups) {
		if (!childClausesCaptureGroups)
			return;
		if (source instanceof BLSpans) // shouldn't happen, but ok
			((BLSpans) source).getCapturedGroups(capturedGroups);
	}

	@Override
	public int nextDoc() throws IOException {
		return source.nextDoc();
	}

	@Override
	public int docID() {
		return source.docID();
	}

	@Override
	public int nextStartPosition() throws IOException {
		return source.nextStartPosition();
	}

	@Override
	public int startPosition() {
		return source.startPosition();
	}

	@Override
	public int endPosition() {
		return source.endPosition();
	}

	@Override
	public int advance(int target) throws IOException {
		return source.advance(target);
	}

}
