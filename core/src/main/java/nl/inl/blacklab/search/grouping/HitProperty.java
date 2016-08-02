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

import org.apache.log4j.Logger;

import nl.inl.blacklab.search.Hits;

/**
 * Abstract base class for a property of a hit, like document title, hit text, right context, etc.
 */
public abstract class HitProperty implements Comparator<Object> {
	protected static final Logger logger = Logger.getLogger(HitProperty.class);

	/** The Hits object we're looking at */
	protected Hits hits;

	/** Reverse comparison result or not? */
	protected boolean reverse = false;

	public HitProperty(Hits hits) {
		this.hits = hits;
		contextIndices = new ArrayList<>();
		contextIndices.add(0); // in case it's accidentally not set, set a default value
	}

	public abstract HitPropValue get(int result);

	/**
	 * Compares two hits on this property.
	 *
	 * The default implementation uses get() to compare
	 * the two hits. Subclasses may override this method to
	 * provide a more efficient implementation.
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
	public int compare(Object a, Object b) {
		HitPropValue hitPropValueA = get((Integer)a);
		HitPropValue hitPropValueB = get((Integer)b);
		return hitPropValueA.compareTo(hitPropValueB);
	}

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
	 * Used by subclasses to add a dash for reverse when serializing
	 * @return either a dash or the empty string
	 */
	protected String serializeReverse() {
		return reverse ? "-" : "";
	}

	/**
	 * Convert the String representation of a HitProperty back into the HitProperty
	 * @param hits our hits object (i.e. what we're trying to sort or group)
	 * @param serialized the serialized object
	 * @return the HitProperty object, or null if it could not be deserialized
	 */
	public static HitProperty deserialize(Hits hits, String serialized) {
		if (PropValSerializeUtil.isMultiple(serialized)) {
			boolean reverse = false;
			if (serialized.startsWith("-(") && serialized.endsWith(")")) {
				reverse = true;
				serialized = serialized.substring(2, serialized.length() - 1);
			}
			HitPropertyMultiple result = HitPropertyMultiple.deserialize(hits, serialized);
			result.setReverse(reverse);
			return result;
		}

		String[] parts = PropValSerializeUtil.splitPartFirstRest(serialized);
		String type = parts[0].toLowerCase();
		boolean reverse = false;
		if (type.length() > 0 && type.charAt(0) == '-') {
			reverse = true;
			type = type.substring(1);
		}
		String info = parts.length > 1 ? parts[1] : "";
		List<String> types = Arrays.asList("decade", "docid", "field", "hit", "left", "right", "wordleft", "wordright", "context");
		int typeNum = types.indexOf(type);
		HitProperty result;
		switch (typeNum) {
		case 0:
			result = HitPropertyDocumentDecade.deserialize(hits, info);
			break;
		case 1:
			result = HitPropertyDocumentId.deserialize(hits);
			break;
		case 2:
			result = HitPropertyDocumentStoredField.deserialize(hits, info);
			break;
		case 3:
			result = HitPropertyHitText.deserialize(hits, info);
			break;
		case 4:
			result = HitPropertyLeftContext.deserialize(hits, info);
			break;
		case 5:
			result = HitPropertyRightContext.deserialize(hits, info);
			break;
		case 6:
			result = HitPropertyWordLeft.deserialize(hits, info);
			break;
		case 7:
			result = HitPropertyWordRight.deserialize(hits, info);
			break;
		case 8:
			result = HitPropertyContextWords.deserialize(hits, info);
			break;
		default:
			logger.debug("Unknown HitProperty '" + type + "'");
			return null;
		}
		result.setReverse(reverse);
		return result;
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

	/**
	 * Is the comparison reversed?
	 * @return true if it is, false if not
	 */
	public boolean isReverse() {
		return reverse;
	}

	/**
	 * Set whether to reverse the comparison.
	 * @param reverse if true, reverses comparison
	 */
	public void setReverse(boolean reverse) {
		this.reverse = reverse;
	}

	@Override
	public String toString() {
		return serialize();
	}
}
