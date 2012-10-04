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
/*
 * de AndSpans gedragen zich op documentniveau als een boolese 'AND',
 * en binnen het document als en 'OR':
 * alle hits van spans[0] en spans[1] binnen het document worden afgeteld
 */

package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Collection;

import org.apache.lucene.search.spans.Spans;

/**
 * "AND"-samenvoeging van twee Spans objecten.
 */
public class SpansDocLevelAnd extends Spans {
	/** Het document id waarvoor we spans teruggeven */
	private int currentDocId;

	/** De spans-index waarin de huidige span staat */
	private int currentSpansIndex;

	/** De span-lijsten */
	private Spans[] spans;

	/** Zijn we voorbij de laatste span gegaan? */
	private boolean stillValidSpans[];

	private boolean spans1nexted;

	public SpansDocLevelAnd(Spans leftClause, Spans rightClause) {
		spans = new Spans[2];
		spans[0] = leftClause;
		spans[1] = rightClause;
		currentDocId = -1; // we hebben nog geen current document
		stillValidSpans = new boolean[2];
		spans1nexted = false; // spans[1] zetten we straks alvast op de eerste span
		stillValidSpans[1] = true;
		stillValidSpans[0] = true;
		currentSpansIndex = 0;
	}

	/**
	 * Retourneert doc nummer
	 *
	 * @return het huidige docnr
	 */
	@Override
	public int doc() {
		return spans[currentSpansIndex].doc();
	}

	/**
	 * Controleert of de aangegeven spans-lijst nog wel naar het huidige document wijst, of dat deze
	 * lijst naar een ander document wijst of uitgeput is.
	 *
	 * @param spansNumber
	 *            de index in het spans[] array waarvan we dit willen weten
	 * @return true als deze spans-lijst nog naar het huidige document wijst, false anders
	 */
	private boolean doesSpansPointToCurrentDoc(int spansNumber) {
		return stillValidSpans[spansNumber] && spans[spansNumber].doc() == currentDocId;
	}

	/**
	 * @return het eind van de huidige span
	 */
	@Override
	public int end() {
		return spans[currentSpansIndex].end();
	}

	/**
	 * Ga naar de volgende span.
	 *
	 * @return true als we op een geldige span staan, false als we klaar zijn.
	 * @throws IOException
	 */
	@Override
	public boolean next() throws IOException {
		// Dit moet als eerste gebeuren zodat spans[1] alvast op een geldige span
		// staat, maar we willen het niet in de constructor i.v.m de throws clause
		if (!spans1nexted) {
			spans1nexted = true;
			stillValidSpans[1] = spans[1].next();
		}

		// Zorg dat we de span die we vorige keer teruggaven 'doordraaien',
		// zodat we in spans[0] en spans[1] een 'verse' span hebben staan.
		// (Natuurlijk kan het voorkomen dat spans[0] of spans[1] hiermee uitgeput
		// raakt; dat controleren we met de validspans[0] en validspans[1] members)

		stillValidSpans[currentSpansIndex] = spans[currentSpansIndex].next();

		// Als we nog geen document hadden, of we zijn klaar met het huidige document:
		// ga door naar het volgende document
		if (currentDocId == -1
				|| ((!stillValidSpans[0] || spans[0].doc() != currentDocId) && (!stillValidSpans[1] || spans[1]
						.doc() != currentDocId))) {
			boolean ok = synchronize();
			if (!ok)
				return false;
		}

		// Als je hier komt, weet je zeker dat je met een geldig document bezig bent.
		// Wel kan het zijn dat een van de spans uitgeput is en dat je de andere spans nog af
		// moet maken.

		boolean spansPointsToCurrentDoc[] = new boolean[2];
		spansPointsToCurrentDoc[0] = doesSpansPointToCurrentDoc(0);
		spansPointsToCurrentDoc[1] = doesSpansPointToCurrentDoc(1);
		if (spansPointsToCurrentDoc[0] && spansPointsToCurrentDoc[1]) {
			// We hebben twee spans om uit te kiezen, kies degene die het eerste in het document
			// voorkomt.
			if (spans[0].start() < spans[1].start())
				currentSpansIndex = 0;
			else
				currentSpansIndex = 1;
		} else if (spansPointsToCurrentDoc[0]) {
			// Alleen spans[0] heeft nog spans uit het huidige document.
			currentSpansIndex = 0;
		} else if (spansPointsToCurrentDoc[1]) {
			// Alleen spans[1] heeft nog spans uit het huidige document.
			currentSpansIndex = 1;
		} else {
			// kan niet (de checks bovenaan de functie maken dit onmogelijk, we zouden
			// dan al in een nieuw document zitten of helemaal klaar zijn)
			assert (false);
		}

		// Nieuwe span gevonden; we onthouden welke span dit is in de 'currentSpanFromspans[0]'
		// member, zodat de doc(), item(), getSearchTerm(), start() en end() functies
		// weten welke van de twee spans ze moeten gebruiken.
		return true;
	}

	/**
	 * Indien spans[0] en spans[1] op een verschillend documentnummer staan, zoekt deze functie het
	 * eerstvolgende overeenkomende documentnummer.
	 *
	 * @return true als er een volgend document gevonden is, false als we klaar zijn
	 */
	private boolean synchronize() throws IOException {
		// Waren we al klaar?
		if (!stillValidSpans[0] || !stillValidSpans[1]) {
			// Ja
			return false;
		}

		// Loop net zo lang tot we een match in spans[0] en spans[1] vinden
		int doc1, doc2;
		doc1 = spans[0].doc();
		doc2 = spans[1].doc();
		while (doc1 != doc2) {
			// Welke van de twee moeten we doordraaien?
			if (doc1 < doc2) {
				// spans[0] ligt achter op spans[1]; skip spans[0] tot de positie van spans[1]
				stillValidSpans[0] = spans[0].skipTo(doc2);
				if (!stillValidSpans[0]) {
					// spans[0] is uitgeput; we zijn klaar
					return false;
				}
				doc1 = spans[0].doc();
			} else {
				// spans[1] ligt achter op spans[0]; skip spans[1] tot de positie van spans[0]
				stillValidSpans[1] = spans[1].skipTo(doc1);
				if (!stillValidSpans[1]) {
					// spans[1] is uitgeput; we zijn klaar
					return false;
				}
				doc2 = spans[1].doc();
			}
		}

		// We hebben een match gevonden; dit is ons nieuwe current document.
		// spans[0] en spans[1] staan nu op de eerste match van dit document.
		currentDocId = doc1;
		return true;
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
		// Skip beiden tot aan doc
		stillValidSpans[0] = spans[0].skipTo(doc);
		stillValidSpans[1] = spans[1].skipTo(doc);
		return synchronize();
	}

	/**
	 * @return het begin van de huidige span
	 */
	@Override
	public int start() {
		return spans[currentSpansIndex].start();
	}

	@Override
	public String toString() {
		return "SpansDocLevelAnd(" + spans[0] + ", " + spans[1] + ")";
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
