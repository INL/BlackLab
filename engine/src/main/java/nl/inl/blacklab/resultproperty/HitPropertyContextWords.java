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
package nl.inl.blacklab.resultproperty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.results.ContextSize;
import nl.inl.blacklab.search.results.Contexts;
import nl.inl.blacklab.search.results.Hits;

/**
 * A hit property for grouping on the context of the hit. Requires
 * HitConcordances as input (so we have the hit text available).
 */
public class HitPropertyContextWords extends HitProperty {

    private static final int MAX_HIT_LENGTH = 10;

    /** A location in the hit context to start a stretch of words from. */
    public enum ContextStart {
        LEFT_OF_HIT("L"), // left context of the hit
        HIT_TEXT_FROM_START("H"), // hit text
        HIT_TEXT_FROM_END("E"), // hit text, backwards from last matched word
        RIGHT_OF_HIT("R"); // right context of the hit

        private String code;

        ContextStart(String code) {
            this.code = code;
        }

        @Override
        public String toString() {
            return code;
        }
    }

    /**
     * A stretch of words from the (surroundings of) the matched text. Note the
     * public members because usage of this object in sorting/grouping is
     * performance-critical.
     */
    public static class ContextPart {

        /*
         * More generic:
         * - part/dir (left / hit / hitFromEnd / right)
         * - startOffset (=firstWord)
         * - direction
         * - maxLength (terminated by end of part)
         */
        
        public static ContextPart get(ContextStart startFrom, int firstWord, int lastWord) {
            return new ContextPart(startFrom, firstWord, lastWord);
        }

        public static ContextPart get(ContextStart startFrom, int firstWord, int direction, int maxLength) {
            return new ContextPart(startFrom, firstWord, direction, maxLength);
        }

        protected ContextStart startFrom;

        protected int firstWord;

        /**
         * Direction: 1 = default direction, -1 = reverse direction. Default direction
         * is right for ContextStart.HIT_TEXT_FROM_START and ContextStart.RIGHT_OF_HIT,
         * left for ContextStart.HIT_TEXT_FROM_END and ContextStart.LEFT_OF_HIT.
         */
        protected int direction;

        protected int maxLength;

        protected ContextPart(ContextStart startFrom, int firstWord, int lastWord) {
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

        protected ContextPart(ContextStart startFrom, int firstWord, int direction, int maxLength) {
            this.startFrom = startFrom;
            this.firstWord = firstWord;
            this.direction = direction;
            this.maxLength = maxLength < 0 ? Integer.MAX_VALUE : maxLength;
        }

        @Override
        public String toString() {
            return startFrom.toString() + (firstWord + 1)
                    + (maxLength != Integer.MAX_VALUE ? "-" + (firstWord + direction * (maxLength - 1) + 1) : "");
        }

        public int absoluteDirection() {
            boolean defaultRight = startFrom == ContextStart.HIT_TEXT_FROM_START
                    || startFrom == ContextStart.RIGHT_OF_HIT;
            return defaultRight ? direction : -direction;
        }
    }

    static HitPropertyContextWords deserializeProp(BlackLabIndex index, AnnotatedField field, String info) {
        String[] parts = PropertySerializeUtil.splitParts(info);
        String propName = parts[0];
        if (propName.length() == 0)
            propName = AnnotatedFieldNameUtil.getDefaultMainAnnotationName();
        MatchSensitivity sensitivity = parts.length > 1 ? MatchSensitivity.fromLuceneFieldSuffix(parts[1]) : MatchSensitivity.SENSITIVE;
        List<ContextPart> whichWords = null;
        if (parts.length > 2)
            whichWords = parseContextWordSpec(parts[2]);
        Annotation annotation = field.annotation(propName);
        return new HitPropertyContextWords(index, annotation, sensitivity, whichWords);
    }

    /**
     * Parse context word specification such as "L1-3,R1-3"
     * 
     * @param contextWordSpec specification string
     * @return stretches of context words indicated in the string
     */
    private static List<ContextPart> parseContextWordSpec(String contextWordSpec) {
        List<ContextPart> result = new ArrayList<>();
        for (String part : contextWordSpec.split("\\s*;\\s*")) {
            if (part.length() == 0)
                continue;
            ContextStart startFrom;
            switch (part.charAt(0)) {
            case 'L':
                startFrom = ContextStart.LEFT_OF_HIT;
                break;
            case 'E':
                startFrom = ContextStart.HIT_TEXT_FROM_END;
                break;
            case 'R':
                startFrom = ContextStart.RIGHT_OF_HIT;
                break;
            case 'H':
            default:
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

    private Annotation annotation;

    private MatchSensitivity sensitivity;

    private BlackLabIndex index;

    private List<ContextPart> words;

    int totalWords;

    HitPropertyContextWords(HitPropertyContextWords prop, Hits hits, Contexts contexts, boolean invert) {
        super(prop, hits, contexts, invert);
        this.annotation = prop.annotation;
        if (!hits.field().equals(this.annotation.field())) {
            throw new IllegalArgumentException(
                    "Hits passed to HitProperty must be in the field it was declared with! (declared with "
                            + this.annotation.field().name() + ", hits has " + hits.field().name() + "; class=" + getClass().getName() + ")");
        }
        this.sensitivity = prop.sensitivity;
        this.index = hits.index();
        this.words = prop.words;
        this.totalWords = prop.totalWords;
    }

    public HitPropertyContextWords(BlackLabIndex index, Annotation annotation, MatchSensitivity sensitivity, String wordSpec) {
        this(index, annotation, sensitivity, parseContextWordSpec(wordSpec));
    }

    public HitPropertyContextWords(BlackLabIndex index, Annotation annotation, MatchSensitivity sensitivity, List<ContextPart> words) {
        super();
        init(index, annotation, sensitivity, words);
    }

    private void init(BlackLabIndex blIndex, Annotation annotation, MatchSensitivity sensitivity, List<ContextPart> words) {
        this.index = blIndex;
        if (annotation == null) {
            this.annotation = index.mainAnnotatedField().mainAnnotation();
        } else {
            this.annotation = annotation;
        }
        this.sensitivity = sensitivity;
        this.words = words;
        if (words == null) {
            // "entire hit text"
            this.words = new ArrayList<>();
            this.words.add(new ContextPart(ContextStart.HIT_TEXT_FROM_START, 0, 1, blIndex.defaultContextSize().left()));
        } else {
            // Determine the maximum length of each part, by limiting it to the
            // maximum possible given the anchor point, direction and first word.
            for (ContextPart part : words) {
                if (part.direction < 0) {
                    // Reverse direction, so back towards our anchor point.
                    part.maxLength = Math.min(part.maxLength, part.firstWord + 1);
                } else {
                    // Default direction, so away from our anchor point.
                    switch (part.startFrom) {
                    case HIT_TEXT_FROM_END:
                    case HIT_TEXT_FROM_START:
                        // Limit to length of hit text. We don't know how long the hits will be;
                        // Assume hits won't be more than 10 tokens.
                        part.maxLength = Math.min(part.maxLength, MAX_HIT_LENGTH - part.firstWord);
                        break;
                    default:
                        // Limit to length of left or right context.
                        part.maxLength = Math.min(part.maxLength, blIndex.defaultContextSize().left() - part.firstWord);
                        break;
                    }
                    if (part.maxLength < 0)
                        part.maxLength = 0;
                }
            }
        }
        // Add the maximum length of each part so we know the length of the context array needed
        totalWords = 0;
        for (ContextPart contextWordDef : this.words) {
            totalWords += contextWordDef.maxLength;
        }
    }

    @Override
    public HitProperty copyWith(Hits newHits, Contexts contexts, boolean invert) {
        return new HitPropertyContextWords(this, newHits, contexts, invert);
    }

    @Override
    public PropertyValueContextWords get(int hitIndex) {
        int[] context = contexts.get(hitIndex);
        int contextHitStart = context[Contexts.HIT_START_INDEX];
        int contextRightStart = context[Contexts.RIGHT_START_INDEX];
        int contextLength = context[Contexts.LENGTH_INDEX];

        int[] dest = new int[totalWords];
        int destIndex = 0;
        boolean allPartsReversed = true;
        for (ContextPart ctxPart : words) {
            // Determine anchor position, direction to move in, and edge of part (left/hit/right)
            int srcStartIndex, srcDirection, firstInvalidSrcIndex;
            srcDirection = ctxPart.absoluteDirection();
            int firstWordSrcIndex;
            switch (ctxPart.startFrom) {
            case LEFT_OF_HIT:
                srcStartIndex = contextHitStart - 1; // first word before hit
                firstWordSrcIndex = srcStartIndex - ctxPart.firstWord;
                firstInvalidSrcIndex = srcDirection < 0 ? -1 : contextHitStart; // end/start of left context
                break;
            case RIGHT_OF_HIT:
                srcStartIndex = contextRightStart; // first word after hit
                firstWordSrcIndex = srcStartIndex + ctxPart.firstWord;
                firstInvalidSrcIndex = srcDirection > 0 ? contextLength : contextRightStart - 1; // end/start of right context
                break;
            case HIT_TEXT_FROM_END:
                srcStartIndex = contextRightStart - 1; // last hit word
                firstWordSrcIndex = srcStartIndex - ctxPart.firstWord;
                firstInvalidSrcIndex = srcDirection < 0 ? contextHitStart : contextRightStart - 1; // first/last hit word
                break;
            case HIT_TEXT_FROM_START:
            default:
                srcStartIndex = contextHitStart; // first hit word
                firstWordSrcIndex = srcStartIndex + ctxPart.firstWord;
                firstInvalidSrcIndex = srcDirection > 0 ? contextRightStart : contextHitStart - 1; // last/first hit word
                break;
            }
            // Determine start position, stop position
            boolean valuesToCopy = true;
            if (srcDirection > 0) {
                allPartsReversed = false;
                firstInvalidSrcIndex = Math.min(firstInvalidSrcIndex,
                        srcStartIndex + ctxPart.firstWord + ctxPart.maxLength);
                if (firstWordSrcIndex >= firstInvalidSrcIndex)
                    valuesToCopy = false; // there are no values to copy
            } else {
                firstInvalidSrcIndex = Math.max(firstInvalidSrcIndex,
                        srcStartIndex - ctxPart.firstWord - ctxPart.maxLength);
                if (firstWordSrcIndex <= firstInvalidSrcIndex)
                    valuesToCopy = false; // there are no values to copy
            }
            // Copy the words we want to our dest array
            int valuesCopied = 0;
            int contextStartIndex = contextLength * contextIndices.getInt(0) + Contexts.NUMBER_OF_BOOKKEEPING_INTS;
            if (valuesToCopy) {
                for (int srcIndex = firstWordSrcIndex; srcIndex != firstInvalidSrcIndex; srcIndex += srcDirection) {
                    dest[destIndex] = context[contextStartIndex + srcIndex];
                    destIndex++;
                    valuesCopied++;
                }
            }
            // If we don't have enough (e.g. because the hit is shorter), add dummy values
            for (; valuesCopied < ctxPart.maxLength; valuesCopied++) {
                dest[destIndex] = Terms.NO_TERM;
                destIndex++;
            }
        }
        return new PropertyValueContextWords(index, annotation, sensitivity, dest, allPartsReversed);
    }

    // OPT: provide specific compare() method that compares contexts in-place

    @Override
    public List<Annotation> needsContext() {
        return Arrays.asList(annotation);
    }
    
    @Override
    public List<MatchSensitivity> getSensitivities() {
        return Arrays.asList(sensitivity);
    }

    @Override
    public String name() {
        return "left context: " + annotation.name();
    }

    @Override
    public String serialize() {
        String[] parts = AnnotatedFieldNameUtil.getNameComponents(annotation.luceneFieldPrefix());
        String thePropName = parts.length > 1 ? parts[1] : "";
        String contextWordSpec = serializeContextWordSpec();
        return serializeReverse()
                + PropertySerializeUtil.combineParts("context", thePropName, sensitivity.luceneFieldSuffix(), contextWordSpec);
    }

    /**
     * Build a context word specification from the list of context word definition
     * objects.
     * 
     * @return context word specification string, e.g. "L1-3,R1-3"
     */
    private String serializeContextWordSpec() {
        StringBuilder result = new StringBuilder();
        for (ContextPart contextWordPart : words) {
            if (result.length() > 0)
                result.append(";");
            result.append(contextWordPart.toString());
        }
        return result.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((annotation == null) ? 0 : annotation.hashCode());
        result = prime * result + ((index == null) ? 0 : index.hashCode());
        result = prime * result + ((sensitivity == null) ? 0 : sensitivity.hashCode());
        result = prime * result + ((words == null) ? 0 : words.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        HitPropertyContextWords other = (HitPropertyContextWords) obj;
        if (annotation == null) {
            if (other.annotation != null)
                return false;
        } else if (!annotation.equals(other.annotation))
            return false;
        if (index == null) {
            if (other.index != null)
                return false;
        } else if (!index.equals(other.index))
            return false;
        if (sensitivity != other.sensitivity)
            return false;
        if (words == null) {
            if (other.words != null)
                return false;
        } else if (!words.equals(other.words))
            return false;
        return true;
    }

    @Override
    public boolean isDocPropOrHitText() {
        return false;
    }
    
    @Override
    public ContextSize needsContextSize(BlackLabIndex index) {
        int maxContextSize = index.defaultContextSize().left();
        return this.words.stream().map(w -> ContextSize.get(Math.min(w.maxLength, maxContextSize))).reduce(ContextSize::union).orElse(null);
    }
}
