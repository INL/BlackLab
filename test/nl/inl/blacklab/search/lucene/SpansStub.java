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
/**
 *
 */
package nl.inl.blacklab.search.lucene;

import java.util.Collection;

import org.apache.lucene.search.spans.Spans;

/**
 * Stub Spans class for testing. Takes arrays and iterates through 'hits'
 * from these arrays.
 */
public class SpansStub extends Spans {
	private int[] doc;

	private int[] start;

	private int[] end;

	private int current = -1;

	public SpansStub(int[] doc, int[] start, int[] end) {
		this.doc = doc;
		this.start = start;
		this.end = end;
	}

	@Override
	public int doc() {
		return doc[current];
	}

	@Override
	public int end() {
		return end[current];
	}

	@Override
	public Collection<byte[]> getPayload() {
		return null;
	}

	@Override
	public boolean isPayloadAvailable() {
		return false;
	}

	@Override
	public boolean next() {
		current++;
		return current < doc.length;
	}

	@Override
	public boolean skipTo(int target) {
		boolean more = true;
		while (more && (current < 0 || doc() < target))
			more = next();
		return more;
	}

	@Override
	public int start() {
		return start[current];
	}
}