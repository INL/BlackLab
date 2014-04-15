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
package nl.inl.blacklab.search.grouping;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import nl.inl.blacklab.search.Hits;

import org.apache.log4j.Logger;

/**
 * Abstract base class for a property of a hit, like document title, hit text, right context, etc.
 */
public abstract class HitProperty implements Comparator<Object> {
	protected static final Logger logger = Logger.getLogger(HitProperty.class);

	/** The Hits object we're looking at */
	protected Hits hits;

	public HitProperty(Hits hits) {
		this.hits = hits;
		contextIndices = new ArrayList<Integer>();
		contextIndices.add(0);
	}

	public abstract HitPropValue get(int result);

	/**
	 * Compares two hits on this property.
	 *
	 * Note that we use Object as the type instead of Hit to save
	 * on run-time type checking. We know (slash hope :-) that this
	 * method is only ever called to compare Hits.
	 *
	 * @param a first hit
	 * @param b second hit
	 * @return 0 if equal, negative if a < b, positive if a > b.
	 */
	@Override
	public abstract int compare(Object a, Object b);

	/**
	 * Retrieve context from which field(s) prior to sorting/grouping on this
	 * property?
	 * @return null if no context is required, the fieldnames otherwise
	 */
	public List<String> needsContext() {
		return null;
	}

	public abstract String getName();

	/**
	 * Serialize this HitProperty so we can deserialize it later (to pass it
	 * via URL, for example)
	 * @return the String representation of this HitProperty
	 */
	public abstract String serialize();

	/**
	 * Convert the String representation of a HitProperty back into the HitProperty
	 * @param hits our hits object (i.e. what we're trying to sort or group)
	 * @param serialized the serialized object
	 * @return the HitProperty object, or null if it could not be deserialized
	 */
	public static HitProperty deserialize(Hits hits, String serialized) {

		if (serialized.contains(","))
			return HitPropertyMultiple.deserialize(hits, serialized);

		String[] parts = serialized.split(":", 2);
		String type = parts[0].toLowerCase();
		String info = parts.length > 1 ? parts[1] : "";
		List<String> types = Arrays.asList("decade", "docid", "field", "hit", "left", "right", "wordleft", "wordright");
		int typeNum = types.indexOf(type);
		switch (typeNum) {
		case 0:
			return HitPropertyDocumentDecade.deserialize(hits, info);
		case 1:
			return HitPropertyDocumentId.deserialize(hits);
		case 2:
			return HitPropertyDocumentStoredField.deserialize(hits, info);
		case 3:
			return HitPropertyHitText.deserialize(hits, info);
		case 4:
			return HitPropertyLeftContext.deserialize(hits, info);
		case 5:
			return HitPropertyRightContext.deserialize(hits, info);
		case 6:
			return HitPropertyWordLeft.deserialize(hits, info);
		case 7:
			return HitPropertyWordRight.deserialize(hits, info);
		}
		logger.debug("Unknown HitProperty '" + type + "'");
		return null;
	}

	/**
	 * For HitProperties that need context, the context indices that
	 * correspond to the context(s) they need in the result set.
	 * (in the same order as reported by needsContext()).
	 */
	List<Integer> contextIndices = null;

	/**
	 * For HitProperties that need context, sets the context indices that
	 * correspond to the context(s) they need in the result set.
	 * @param contextIndices the indices, in the same order as reported by needsContext().
	 */
	public void setContextIndices(List<Integer> contextIndices) {
		this.contextIndices.clear();
		this.contextIndices.addAll(contextIndices);
	}

	public void setHits(Hits hits) {
		this.hits = hits;
	}

	/**
	 * Produce a copy of this HitProperty object with a different Hits object.
	 *
	 * Used by Hits.sortedBy(), to use the specified HitProperty on a different
	 * Hits object than originally intended.
	 *
	 * @param newHits new Hits object to use
	 * @return the new HitProperty object
	 */
	public HitProperty copyWithHits(Hits newHits) {
		// A bit ugly, but it works..
		return HitProperty.deserialize(newHits, serialize());
	}
}
