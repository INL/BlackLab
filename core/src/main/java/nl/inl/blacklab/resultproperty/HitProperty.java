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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.results.CapturedGroupsImpl;
import nl.inl.blacklab.search.results.ContextSize;
import nl.inl.blacklab.search.results.Contexts;
import nl.inl.blacklab.search.results.Hit;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.HitsList;
import nl.inl.blacklab.search.results.Results;

/**
 * Abstract base class for a property of a hit, like document title, hit text,
 * right context, etc.
 */
public abstract class HitProperty implements ResultProperty<Hit> {
    protected static final Logger logger = LogManager.getLogger(HitProperty.class);

    public static HitProperty deserialize(Results<Hit> hits, String serialized) {
        return deserialize(hits.index(), hits.field(), serialized);
    }

    /**
     * Convert the String representation of a HitProperty back into the HitProperty
     * 
     * @param index our index
     * @param field field we're searching
     * @param serialized the serialized object
     * @return the HitProperty object, or null if it could not be deserialized
     */
    public static HitProperty deserialize(BlackLabIndex index, AnnotatedField field, String serialized) {
        if (PropertySerializeUtil.isMultiple(serialized)) {
            boolean reverse = false;
            if (serialized.startsWith("-(") && serialized.endsWith(")")) {
                reverse = true;
                serialized = serialized.substring(2, serialized.length() - 1);
            }
            HitProperty result = HitPropertyMultiple.deserializeProp(index, field, serialized);
            if (reverse)
                result = result.reverse();
            return result;
        }
    
        String[] parts = PropertySerializeUtil.splitPartFirstRest(serialized);
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
            result = HitPropertyDocumentDecade.deserializeProp(index, info);
            break;
        case 1:
            result = HitPropertyDocumentId.deserializeProp();
            break;
        case 2:
            result = HitPropertyDocumentStoredField.deserializeProp(info);
            break;
        case 3:
            result = HitPropertyHitText.deserializeProp(index, field, info);
            break;
        case 4:
            result = HitPropertyLeftContext.deserializeProp(index, field, info);
            break;
        case 5:
            result = HitPropertyRightContext.deserializeProp(index, field, info);
            break;
        case 6:
            result = HitPropertyWordLeft.deserializeProp(index, field, info);
            break;
        case 7:
            result = HitPropertyWordRight.deserializeProp(index, field, info);
            break;
        case 8:
            result = HitPropertyContextWords.deserializeProp(index, field, info);
            break;
        case 9:
            result = HitPropertyHitPosition.deserializeProp();
            break;
        case 10:
            result = HitPropertyDoc.deserializeProp(index);
            break;
        default:
            logger.debug("Unknown HitProperty '" + type + "'");
            return null;
        }
        if (reverse)
            result = result.reverse();
        return result;
    }

    /** The Hits object we're looking at */
    protected Results<Hit> hits;

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
    HitProperty(HitProperty prop, Results<Hit> hits, Contexts contexts, boolean invert) {
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

    @Override
    public abstract PropertyValue get(Hit hit);

    @Override
    public int compare(Hit a, Hit b) {
        PropertyValue hitPropValueA = get(a);
        PropertyValue hitPropValueB = get(b);
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

    @Override
    public abstract String getName();

    @Override
    public abstract String serialize();

    /**
     * Used by subclasses to add a dash for reverse when serializing
     * 
     * @return either a dash or the empty string
     */
    protected String serializeReverse() {
        return reverse ? "-" : "";
    }
    
    @Override
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
    public HitProperty copyWith(Results<Hit> newHits, Contexts contexts) {
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
    public abstract HitProperty copyWith(Results<Hit> newHits, Contexts contexts, boolean invert);

    @Override
    public boolean isReverse() {
        return reverse;
    }

    @Override
    public String toString() {
        return serialize();
    }
    
    @Override
    public List<String> getPropNames() {
        return Arrays.asList(getName());
    }
    
//    public Hits sort(List<Hit> hitsToSort) {
//        // Make sure we have a sort order array of sufficient size
//        // and fill it with the original hit order (0, 1, 2, ...)
//        hitsToSort.size(); // fetch all
//        List<Hit> sorted = new ArrayList<>(hitsToSort);
//
//        // We need a HitProperty with the correct Hits object
//        // If we need context, make sure we have it.
//        List<Annotation> requiredContext = needsContext();
//        HitProperty sortProp = copyWith(hitsToSort,
//                requiredContext == null ? null : new Contexts(hitsToSort, requiredContext, needsContextSize(hitsToSort.index())));
//
//        // Perform the actual sort.
//        sorted.sort(sortProp);
//
//        CapturedGroupsImpl capturedGroups = hitsToSort.capturedGroups();
//        int hitsCounted = hitsToSort.hitsCountedSoFar();
//        int docsRetrieved = hitsToSort.docsProcessedSoFar();
//        int docsCounted = hitsToSort.docsCountedSoFar();
//
//        return new HitsList(hitsToSort.queryInfo(), sorted, capturedGroups, hitsCounted, docsRetrieved, docsCounted);
//    }

    @Override
    public Hits sortResults(Results<Hit> hitsToSort) {
        List<Hit> sorted = new ArrayList<>(hitsToSort.resultsList());

        // We need a HitProperty with the correct Hits object
        // If we need context, make sure we have it.
        List<Annotation> requiredContext = needsContext();
        HitProperty sortProp = copyWith(hitsToSort,
                requiredContext == null ? null : new Contexts(hitsToSort, requiredContext, needsContextSize(hitsToSort.index())));

        // Perform the actual sort.
        sorted.sort(sortProp);

        if (hitsToSort instanceof Hits) {
            Hits hits2 = (Hits)hitsToSort;
            CapturedGroupsImpl capturedGroups = hits2.capturedGroups();
            int hitsCounted = hits2.hitsCountedSoFar();
            int docsRetrieved = hits2.docsProcessedSoFar();
            int docsCounted = hits2.docsCountedSoFar();
            return new HitsList(hitsToSort.queryInfo(), sorted, null, null, hitsCounted, docsRetrieved, docsCounted, capturedGroups);
        }
        return Hits.list(hitsToSort.queryInfo(), sorted);
    }

    @Override
    public boolean defaultSortDescending() {
        return false;
    }
}