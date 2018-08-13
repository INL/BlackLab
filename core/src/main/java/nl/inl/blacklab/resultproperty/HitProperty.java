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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.results.CapturedGroupsImpl;
import nl.inl.blacklab.search.results.ContextSize;
import nl.inl.blacklab.search.results.Contexts;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.HitsAbstract;
import nl.inl.blacklab.search.results.HitsList;

/**
 * Abstract base class for a property of a hit, like document title, hit text,
 * right context, etc.
 */
public abstract class HitProperty implements Comparator<Object>, Serializable {
    protected static final Logger logger = LogManager.getLogger(HitProperty.class);

    /** The Hits object we're looking at */
    protected Hits hits;

    /** Reverse comparison result or not? */
    protected boolean reverse = false;

    /** Hit contexts, if any */
    protected Contexts contexts = null;

    /**
     * For HitProperties that need context, the context indices that correspond to
     * the context(s) they need in the result set. (in the same order as reported by
     * needsContext()).
     */
    List<Integer> contextIndices;

    public HitProperty() {
        this.hits = null;
    }

    /**
     * Copy a HitProperty, with some optional changes.
     * 
     * @param prop property to copy
     * @param hits new hits to use, or null to inherit
     * @param contexts new contexts to use, or null to inherit
     * @param invert true to invert the previous sort order; false to keep it the same
     */
    HitProperty(HitProperty prop, Hits hits, Contexts contexts, boolean invert) {
        this.hits = hits == null ? prop.hits : hits;
        this.reverse = prop.reverse;
        if (invert)
            this.reverse = !this.reverse;
        this.setContexts(contexts); // this will initialize contextIndices to default value...
        if (prop.contextIndices != null)
            this.contextIndices = prop.contextIndices; // ...but if we already had different values, use those
    }

    HitProperty(HitProperty prop, Hits hits, Contexts contexts) {
        this(prop, hits, contexts, false);
    }
    
    HitProperty(HitProperty prop, Hits hits) {
        this(prop, hits, null, false);
    }

    HitProperty(HitProperty prop) {
        this(prop, null, null, false);
    }

    /**
     * Set contexts to use.
     * 
     * Only ever used by constructor, so doesn't break immutability.
     * 
     * @param contexts contexts to use, or null for none
     */
    protected HitProperty setContexts(Contexts contexts) {
        if (needsContext() != null) {
            this.contexts = contexts;
    
            // Unless the client sets different context indices, assume we got the ones we wanted in the correct order
            if (contexts != null)
                this.contextIndices = IntStream.range(0, contexts.size()).boxed().collect(Collectors.toList());
        }
        return this;
    }

    /**
     * For HitProperties that need context, sets the context indices that correspond
     * to the context(s) they need in the result set.
     * 
     * Only needed if the context indices differ from the assumed default of 0, 1,
     * 2, ...
     * 
     * Only called from the {@link HitPropertyMultiple#copyWith(Hits, Contexts, boolean)}, so doesn't break immutability.
     * 
     * @param contextIndices the indices, in the same order as reported by
     *            needsContext().
     */
    protected void setContextIndices(List<Integer> contextIndices) {
        if (this.contextIndices == null)
            this.contextIndices = new ArrayList<>();
        else
            this.contextIndices.clear();
        this.contextIndices.addAll(contextIndices);
    }

    public abstract HitPropValue get(int result);

    /**
     * Compares two hits on this property.
     *
     * The default implementation uses get() to compare the two hits. Subclasses may
     * override this method to provide a more efficient implementation.
     *
     * Note that we use Object as the type instead of Hit to save on run-time type
     * checking. We know (slash hope :-) that this method is only ever called to
     * compare Hits.
     *
     * @param a first hit
     * @param b second hit
     * @return 0 if equal, negative if a < b, positive if a > b.
     */
    @Override
    public int compare(Object a, Object b) {
        HitPropValue hitPropValueA = get((Integer) a);
        HitPropValue hitPropValueB = get((Integer) b);
        return hitPropValueA.compareTo(hitPropValueB);
    }

    /**
     * Retrieve context from which field(s) prior to sorting/grouping on this
     * property?
     * 
     * @return null if no context is required, the fieldnames otherwise
     */
    public List<Annotation> needsContext() {
        return null;
    }

    /**
     * If this property need context(s), how large should they be?
     * 
     * @param index index, so we can find the default context size if we need to 
     * @return required context size
     */
    public ContextSize needsContextSize(BlackLabIndex index) {
        return index.defaultContextSize();
    }

    public abstract String getName();

    /**
     * Serialize this HitProperty so we can deserialize it later (to pass it via
     * URL, for example)
     * 
     * @return the String representation of this HitProperty
     */
    public abstract String serialize();

    /**
     * Used by subclasses to add a dash for reverse when serializing
     * 
     * @return either a dash or the empty string
     */
    protected String serializeReverse() {
        return reverse ? "-" : "";
    }

    /**
     * Convert the String representation of a HitProperty back into the HitProperty
     * 
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
            HitProperty result = HitPropertyMultiple.deserialize(hits, serialized);
            if (reverse)
                result = result.reverse();
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
        List<String> types = Arrays.asList("decade", "docid", "field", "hit", "left", "right", "wordleft", "wordright",
                "context", "hitposition", "doc");
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
        case 9:
            result = HitPropertyHitPosition.deserialize(hits);
            break;
        case 10:
            result = HitPropertyDoc.deserialize(hits);
            break;
        default:
            logger.debug("Unknown HitProperty '" + type + "'");
            return null;
        }
        if (reverse)
            result = result.reverse();
        return result;
    }

    /**
     * Reverse the sort order of this hit property.
     * 
     * @return a new hit property with the sort order reversed
     */
    public HitProperty reverse() {
        return copyWith(null, null, true);
    }

    /**
     * Produce a copy of this HitProperty object with a different Hits and Contexts
     * object.
     *
     * @param newHits new Hits to use
     * @param contexts new Contexts to use, or null for none
     * @return the new HitProperty object
     */
    public HitProperty copyWith(Hits newHits, Contexts contexts) {
        return copyWith(newHits, contexts, false);
    }

    /**
     * Produce a copy of this HitProperty object with a different Hits and Contexts
     * object.
     *
     * @param newHits new Hits to use, or null to inherit
     * @param contexts new Contexts to use, or null to inherit
     * @param invert true if we should invert the previous sort order; false to keep it the same
     * @return the new HitProperty object
     */
    public abstract HitProperty copyWith(Hits newHits, Contexts contexts, boolean invert);

    /**
     * Is the comparison reversed?
     * 
     * @return true if it is, false if not
     */
    public boolean isReverse() {
        return reverse;
    }

    @Override
    public String toString() {
        return serialize();
    }

    /**
     * Get the names of all (sub-)properties separately.
     * 
     * @return the list
     */
    public abstract List<String> getPropNames();

    public Hits sortHits(HitsAbstract hitsToSort, boolean reverseSort) {
        // Make sure we have a sort order array of sufficient size
        // and fill it with the original hit order (0, 1, 2, ...)
        int n = hitsToSort.size(); // also triggers fetching all hits
        Integer[] sortOrder = new Integer[n];
        for (int i = 0; i < n; i++)
            sortOrder[i] = i;

        // We need a HitProperty with the correct Hits object
        // If we need context, make sure we have it.
        List<Annotation> requiredContext = needsContext();
        HitProperty sortProp = copyWith(hitsToSort,
                requiredContext == null ? null : new Contexts(hitsToSort, requiredContext, needsContextSize(hitsToSort.queryInfo().index())));

        // Perform the actual sort.
        Arrays.sort(sortOrder, sortProp);

        if (reverseSort) {
            // Instead of creating a new Comparator that reverses the order of the
            // sort property (which adds an extra layer of indirection to each of the
            // O(n log n) comparisons), just reverse the hits now (which runs
            // in linear time).
            for (int i = 0; i < n / 2; i++) {
                sortOrder[i] = sortOrder[n - i - 1];
            }
        }

        CapturedGroupsImpl capturedGroups = hitsToSort.capturedGroups();
        int hitsCounted = hitsToSort.hitsCountedSoFar();
        int docsRetrieved = hitsToSort.docsProcessedSoFar();
        int docsCounted = hitsToSort.docsCountedSoFar();

        return new HitsList(hitsToSort.queryInfo(), hitsToSort.hitsList(), sortOrder, capturedGroups, hitsCounted,
                docsRetrieved, docsCounted);
    }
}
