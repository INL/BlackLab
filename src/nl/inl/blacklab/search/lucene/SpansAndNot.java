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

import org.apache.lucene.search.spans.Spans;

/**
 * "AND NOT"-samenvoeging van twee Spans objecten.
 *
 * Bepaalt nieuwe spans, gebaseerd op twee spans-objecten: een met te includen documenten, en een
 * met te excluden documenten.
 */
public class SpansAndNot extends Spans {
	/** AND gedeelte (documenten met deze spans wel, tenzij ze ook in exclude_spans staan) */
	private Spans includeSpans;

	/** NOT gedeelte (documenten met deze spans uitsluiten) */
	private Spans excludeSpans;

	private boolean excludeSpansNexted;

	private boolean moreIncludeSpans;

	private boolean moreExcludeSpans;

	public SpansAndNot(Spans include_spans, Spans exclude_spans) {
		includeSpans = include_spans;
		excludeSpans = exclude_spans;
		excludeSpansNexted = false;
		moreIncludeSpans = true;
		moreExcludeSpans = true;
	}

	/**
	 * @return het huidige documentnummer
	 */
	@Override
	public int doc() {
		return includeSpans.doc();
	}

	/**
	 * @return het einde van de huidige span
	 */
	@Override
	public int end() {
		return includeSpans.end();
	}

	/**
	 * Ga naar de volgende span.
	 *
	 * @return true als we op een geldige span staan, false als we klaar zijn.
	 * @throws IOException
	 */
	@Override
	public boolean next() throws IOException {
		// Dit moet direct al gebeuren, maar we willen het niet in de
		// constructor vanwege de throws clause
		if (!excludeSpansNexted) {
			excludeSpansNexted = true;
			moreExcludeSpans = excludeSpans.next();
		}

		boolean done = false;
		int newDocId = -1;
		do {
			if (moreIncludeSpans && includeSpans.next()) {
				// Voldoet deze?
				newDocId = includeSpans.doc();
				if (moreExcludeSpans && excludeSpans.doc() < newDocId) {
					moreExcludeSpans = excludeSpans.skipTo(newDocId);
				}
			} else {
				// Geen spans meer over!
				done = true;
			}
		} while (!done && moreExcludeSpans && includeSpans.doc() == excludeSpans.doc());
		return !done;
	}

	/**
	 * Ga naar het opgegeven document, als daarin hits zitten. Zo niet, ga naar het eerstvolgende
	 * document met hits daarna.
	 *
	 * @param doc
	 *            het documentnummer om (over)heen te skippen
	 * @return true als er nog een document met hits gevonden is (hoeft niet het opgegeven doc te
	 *         zijn), false anders
	 * @throws IOException
	 */
	@Override
	public boolean skipTo(int doc) throws IOException {
		if (moreIncludeSpans)
			moreIncludeSpans = includeSpans.skipTo(doc);
		if (!moreIncludeSpans)
			return false;

		if (moreExcludeSpans) {
			moreExcludeSpans = excludeSpans.skipTo(doc);
			if (moreExcludeSpans && includeSpans.doc() == excludeSpans.doc()) {
				return next();
			}
		}

		return true;
	}

	/**
	 * @return het begin van de huidige span
	 */
	@Override
	public int start() {
		return includeSpans.start();
	}

	@Override
	public String toString() {
		return "AndNotSpans(" + "----" + ", " + includeSpans + ", " + excludeSpans + ")";
	}

	@Override
	public Collection<byte[]> getPayload() {
		return null;
	}

	@Override
	public boolean isPayloadAvailable() {
		return false;
	}
}
