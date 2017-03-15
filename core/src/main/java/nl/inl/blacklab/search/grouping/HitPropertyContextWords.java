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
import java.util.List;

import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.search.Hits;
import nl.inl.blacklab.search.Searcher;

/**
 * A hit property for grouping on the context of the hit. Requires
 * HitConcordances as input (so we have the hit text available).
 */
public class HitPropertyContextWords extends HitProperty {

	final int MAX_HIT_LENGTH = 10;

	/** A location in the hit context to start a stretch of words from. */
	public enum ContextStart {
		LEFT_OF_HIT("L"),          // left context of the hit
		HIT_TEXT_FROM_START("H"),  // hit text
		HIT_TEXT_FROM_END("E"),    // hit text, backwards from last matched word
		RIGHT_OF_HIT("R");         // right context of the hit

		private String code;

		ContextStart(String code) {
			this.code = code;
		}

		@Override
		public String toString() {
			return code;
		}
	}

	/** A stretch of words from the (surroundings of) the matched text.
	 *  Note the public members because usage of this object in
	 *  sorting/grouping is performance-critical.
	 */
	public static class ContextPart {

		/*
		 * More generic:
		 * - part/dir (left / hit / hitFromEnd / right)
		 * - startOffset (=firstWord)
		 * - direction
		 * - maxLength (terminated by end of part)
		 */

		public ContextStart startFrom;

		public int firstWord;

		//public int lastWord;

		/** Direction: 1 = default direction, -1 = reverse direction.
		 *  Default direction is right for ContextStart.HIT_TEXT_FROM_START and ContextStart.RIGHT_OF_HIT,
		 *  left for ContextStart.HIT_TEXT_FROM_END and ContextStart.LEFT_OF_HIT.
		 */
		public int direction;

		public int maxLength;

		public ContextPart(ContextStart startFrom, int firstWord, int lastWord) {
			this.startFrom = startFrom;
			this.firstWord = firstWord;
			if (lastWord == Integer.MAX_VALUE) {
				this.direction = 1;
				this.maxLength = Integer.MAX_VALUE;
			} else {
				this.direction = firstWord <= lastWord ? 1 : -1;
				this.maxLength = Math.abs(firstWord - lastWord) + 1;
			}
		}

		public ContextPart(ContextStart startFrom, int firstWord, int direction, int maxLength) {
			this.startFrom = startFrom;
			this.firstWord = firstWord;
			this.direction = direction;
			this.maxLength = maxLength < 0 ? Integer.MAX_VALUE : maxLength;
		}

		@Override
		public String toString() {
			return startFrom.toString() + (firstWord + 1) + (maxLength != Integer.MAX_VALUE ? "-" + (firstWord + direction * (maxLength - 1) + 1) : "");
		}

		public int getAbsoluteDirection() {
			boolean defaultRight = startFrom == ContextStart.HIT_TEXT_FROM_START || startFrom == ContextStart.RIGHT_OF_HIT;
			return defaultRight ? direction : -direction;
		}
	}

	private String luceneFieldName;

	private String propName;

	private boolean sensitive;

	private Searcher searcher;

	private List<ContextPart> words;

	int totalWords;

	public HitPropertyContextWords(Hits hits, String field, String property, boolean sensitive, String wordSpec) {
		this(hits, field, property, sensitive, parseContextWordSpec(wordSpec));
	}

	public HitPropertyContextWords(Hits hits, String field, String property, boolean sensitive, List<ContextPart> words) {
		super(hits);
		this.searcher = hits.getSearcher();
		if (property == null || property.length() == 0) {
			this.luceneFieldName = ComplexFieldUtil.mainPropertyField(searcher.getIndexStructure(), field);
			this.propName = ComplexFieldUtil.getDefaultMainPropName();
		} else {
			this.luceneFieldName = ComplexFieldUtil.propertyField(field, property);
			this.propName = property;
		}
		this.sensitive = sensitive;
		this.words = words;
		if (words == null) {
			// "entire hit text"
			this.words = new ArrayList<>();
			this.words.add(new ContextPart(ContextStart.HIT_TEXT_FROM_START, 0, 1, hits.settings().contextSize()));
		} else {
			// Determine the maximum length of each part, by limiting it to the
			// maximum possible given the anchor point, direction and first word.
			for (ContextPart part: words) {
				if (part.direction < 0) {
					// Reverse direction, so back towards our anchor point.
					part.maxLength = Math.min(part.maxLength, part.firstWord + 1);
				} else {
					// Default direction, so away from our anchor point.
					switch(part.startFrom) {
					case HIT_TEXT_FROM_END:
					case HIT_TEXT_FROM_START:
						// Limit to length of hit text. We don't know how long the hits will be;
						// Assume hits won't be more than 10 tokens.
						part.maxLength = Math.min(part.maxLength, MAX_HIT_LENGTH - part.firstWord);
						break;
					default:
						// Limit to length of left or right context.
						part.maxLength = Math.min(part.maxLength, hits.settings().contextSize() - part.firstWord);
						break;
					}
					if (part.maxLength < 0)
						part.maxLength = 0;
				}
			}
		}
		// Add the maximum length of each part so we know the length of the context array needed
		totalWords = 0;
		for (ContextPart contextWordDef: this.words) {
			totalWords += contextWordDef.maxLength;
		}
	}

	@Override
	public HitPropValueContextWords get(int hitNumber) {
		int[] context = hits.getHitContext(hitNumber);
		int contextHitStart = context[Hits.CONTEXTS_HIT_START_INDEX];
		int contextRightStart = context[Hits.CONTEXTS_RIGHT_START_INDEX];
		int contextLength = context[Hits.CONTEXTS_LENGTH_INDEX];

		int[] dest = new int[totalWords];
		int destIndex = 0;
		for (ContextPart ctxPart: words) {
			// Determine anchor position, direction to move in, and edge of part (left/hit/right)
			int srcStartIndex, srcDirection, firstInvalidSrcIndex;
            srcDirection = ctxPart.getAbsoluteDirection();
			int firstWordSrcIndex;
			switch (ctxPart.startFrom) {
			case LEFT_OF_HIT:
	            srcStartIndex = contextHitStart - 1;  // first word before hit
				firstWordSrcIndex = srcStartIndex - ctxPart.firstWord;
	            firstInvalidSrcIndex = srcDirection < 0 ? -1 : contextHitStart; // end/start of left context
				break;
			case RIGHT_OF_HIT:
				srcStartIndex = contextRightStart;       // first word after hit
				firstWordSrcIndex = srcStartIndex + ctxPart.firstWord;
	            firstInvalidSrcIndex = srcDirection > 0 ? contextLength : contextRightStart - 1; // end/start of right context
				break;
			case HIT_TEXT_FROM_END:
				srcStartIndex = contextRightStart - 1; // last hit word
				firstWordSrcIndex = srcStartIndex - ctxPart.firstWord;
	            firstInvalidSrcIndex = srcDirection < 0 ? contextHitStart : contextRightStart - 1;  // first/last hit word
				break;
			case HIT_TEXT_FROM_START: default:
				srcStartIndex = contextHitStart;            // first hit word
				firstWordSrcIndex = srcStartIndex + ctxPart.firstWord;
	            firstInvalidSrcIndex = srcDirection > 0 ? contextRightStart : contextHitStart - 1; // last/first hit word
				break;
			}
			// Determine start position, stop position
			boolean valuesToCopy = true;
			if (srcDirection > 0) {
				firstInvalidSrcIndex = Math.min(firstInvalidSrcIndex, srcStartIndex + ctxPart.firstWord + ctxPart.maxLength);
				if (firstWordSrcIndex >= firstInvalidSrcIndex)
					valuesToCopy = false; // there are no values to copy
			} else {
				firstInvalidSrcIndex = Math.max(firstInvalidSrcIndex, srcStartIndex - ctxPart.firstWord - ctxPart.maxLength);
				if (firstWordSrcIndex <= firstInvalidSrcIndex)
					valuesToCopy = false; // there are no values to copy
			}
			// Copy the words we want to our dest array
			int valuesCopied = 0;
			int contextStartIndex = contextLength * contextIndices.get(0) + Hits.CONTEXTS_NUMBER_OF_BOOKKEEPING_INTS;
			if (valuesToCopy) {
				for (int srcIndex = firstWordSrcIndex; srcIndex != firstInvalidSrcIndex; srcIndex += srcDirection) {
					dest[destIndex] = context[contextStartIndex + srcIndex];
					destIndex++;
					valuesCopied++;
				}
			}
			// If we don't have enough (e.g. because the hit is shorter), add dummy values
			for ( ; valuesCopied < ctxPart.maxLength; valuesCopied++) {
				dest[destIndex] = Terms.NO_TERM;
				destIndex++;
			}
		}
		return new HitPropValueContextWords(hits, propName, dest, sensitive);
	}

	// OPT: provide specific compare() method that compares contexts in-place

	@Override
	public List<String> needsContext() {
		return Arrays.asList(luceneFieldName);
	}

	@Override
	public String getName() {
		return "left context";
	}

	@Override
	public String serialize() {
		String[] parts = ComplexFieldUtil.getNameComponents(luceneFieldName);
		String thePropName = parts.length > 1 ? parts[1] : "";
		String contextWordSpec = serializeContextWordSpec();
		return serializeReverse() + PropValSerializeUtil.combineParts("context", thePropName, sensitive ? "s" : "i", contextWordSpec);
	}

	/**
	 * Build a context word specification from the list of context word definition objects.
	 * @return context word specification string, e.g. "L1-3,R1-3"
	 */
	private String serializeContextWordSpec() {
		StringBuilder result = new StringBuilder();
		for (ContextPart contextWordPart: words) {
			if (result.length() > 0)
				result.append(";");
			result.append(contextWordPart.toString());
		}
		return result.toString();
	}

	public static HitPropertyContextWords deserialize(Hits hits, String info) {
		String[] parts = PropValSerializeUtil.splitParts(info);
		String fieldName = hits.settings().concordanceField();
		String propName = parts[0];
		if (propName.length() == 0)
			propName = ComplexFieldUtil.getDefaultMainPropName();
		boolean sensitive = parts.length > 1 ? parts[1].equalsIgnoreCase("s") : true;
		List<ContextPart> whichWords = null;
		if (parts.length > 2)
			whichWords = parseContextWordSpec(parts[2]);
		if (fieldName == null || fieldName.length() == 0)
			return new HitPropertyContextWords(hits, hits.getSearcher().getMainContentsFieldName(), null, sensitive, whichWords);
		return new HitPropertyContextWords(hits, fieldName, propName, sensitive, whichWords);
	}

	/** Parse context word specification such as "L1-3,R1-3"
	 * @param contextWordSpec specification string
	 * @return stretches of context words indicated in the string
	 */
	private static List<ContextPart> parseContextWordSpec(String contextWordSpec) {
		List<ContextPart> result = new ArrayList<>();
		for (String part: contextWordSpec.split("\\s*;\\s*")) {
			if (part.length() == 0)
				continue;
			ContextStart startFrom;
			switch(part.charAt(0)) {
			case 'L':
				startFrom = ContextStart.LEFT_OF_HIT;
				break;
			case 'E':
				startFrom = ContextStart.HIT_TEXT_FROM_END;
				break;
			case 'R':
				startFrom = ContextStart.RIGHT_OF_HIT;
				break;
			default:
			case 'H':
				startFrom = ContextStart.HIT_TEXT_FROM_START;
				break;
			}
			int firstWord = 0;
			int lastWord = Integer.MAX_VALUE; // == "as much as possible"
			if (part.length() > 1) {
				if (part.contains("-")) {
					// Two numbers, or a number followed by a dash ("until end of part")
					String[] numbers = part.substring(1).split("\\-");
					try {
						firstWord = Integer.parseInt(numbers[0]) - 1;
						if (numbers.length > 1)
							lastWord = Integer.parseInt(numbers[1]) - 1;
					} catch (NumberFormatException e) {
						// ignore and accept the defaults
					}
				} else {
					// Single number: single word
					firstWord = lastWord = Integer.parseInt(part.substring(1)) - 1;
				}
			}
			result.add(new ContextPart(startFrom, firstWord, lastWord));
		}
		return result;
	}

}
