package nl.inl.blacklab.resultproperty;

import java.util.List;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongComparator;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.forwardindex.FiidLookup;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.results.ContextSize;
import nl.inl.blacklab.search.results.Contexts;
import nl.inl.blacklab.search.results.Hit;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.Results;
import nl.inl.blacklab.util.PropertySerializeUtil;

/**
 * Abstract base class for a property of a hit, like document title, hit text,
 * right context, etc.
 */
public abstract class HitProperty implements ResultProperty<Hit>, LongComparator {
    protected static final Logger logger = LogManager.getLogger(HitProperty.class);

    public static HitProperty deserialize(Results<Hit, HitProperty> hits, String serialized) {
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
        HitProperty result;
        switch (type) {
        case "decade":
            result = HitPropertyDocumentDecade.deserializeProp(index, ResultProperty.ignoreSensitivity(info));
            break;
        case "docid":
            result = HitPropertyDocumentId.deserializeProp();
            break;
        case "field":
            result = HitPropertyDocumentStoredField.deserializeProp(index, ResultProperty.ignoreSensitivity(info));
            break;
        case "hit":
            result = HitPropertyHitText.deserializeProp(index, field, info);
            break;
        case "left":
            result = HitPropertyLeftContext.deserializeProp(index, field, info);
            break;
        case "right":
            result = HitPropertyRightContext.deserializeProp(index, field, info);
            break;
        case "wordleft":
            result = HitPropertyWordLeft.deserializeProp(index, field, info);
            break;
        case "wordright":
            result = HitPropertyWordRight.deserializeProp(index, field, info);
            break;
        case "context":
            result = HitPropertyContextWords.deserializeProp(index, field, info);
            break;
        case "hitposition":
            result = HitPropertyHitPosition.deserializeProp();
            break;
        case "doc":
            result = HitPropertyDoc.deserializeProp(index);
            break;
            
        case "fieldlen":
            throw new BlackLabRuntimeException("Grouping hit results by " + type + " is not yet supported");
            /*result = HitPropertyDocumentAnnotatedFieldLength.deserialize(ResultProperty.ignoreSensitivity(info));
            break;*/
            
        case "numhits":
            throw new BlackLabRuntimeException("Cannot group hit results by " + type);
            
        default:
            logger.debug("Unknown HitProperty '" + type + "'");
            return null;
        }
        if (reverse)
            result = result.reverse();
        return result;
    }

    /** The Hits object we're looking at */
    protected final Hits hits;

    /** Reverse comparison result or not? */
    protected boolean reverse;

    /** Hit contexts, if any */
    protected Contexts contexts = null;

    /**
     * For HitProperties that need context, the context indices that correspond to
     * the context(s) they need in the result set. (in the same order as reported by
     * needsContext()).
     */
    IntArrayList contextIndices;

    public HitProperty() {
        this.hits = null;
        this.reverse = sortDescendingByDefault();
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
            this.contextIndices = new IntArrayList(prop.contextIndices); // ...but if we already had different values, use those
    }

    /**
     * Is the default for this property to sort descending?
     * 
     * This is usually a good default for "group size" or "number of hits".
     * 
     * @return whether to sort descending by default
     */
    protected boolean sortDescendingByDefault() {
        return false;
    }

    /**
     * Set contexts to use.
     * 
     * Only ever used by constructor, so doesn't break immutability.
     * 
     * @param contexts contexts to use, or null for none
     */
    protected void setContexts(Contexts contexts) {
        if (needsContext() != null) {
            this.contexts = contexts;
    
            // Unless the client sets different context indices, assume we got the ones we wanted in the correct order
            if (contexts != null) {
                int numberOfAnnotations = contexts.annotations().size();
                this.contextIndices = new IntArrayList(numberOfAnnotations);
                for (int i = 0; i < numberOfAnnotations; ++i)
                    this.contextIndices.add(i, i);
            }
        }
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
    protected void setContextIndices(IntArrayList contextIndices) {
        this.contextIndices = new IntArrayList(contextIndices);
    }

    public abstract PropertyValue get(long hitIndex);

    /**
     * Get a hit's sort value, for distributed sort.
     *
     * This is used in distributed search to make merging sorted hits from
     * several nodes possible without transmitting all the details (context
     * for all annotations, document metadata) about every hit.
     *
     * Instead, the value to sort on is distilled to a single string that will
     * still produce the correct sort.
     *
     * @param hitIndex hit to get the sort value
     * @return the sort value
     */
    public String[] getSortValue(long hitIndex) {
        return get(hitIndex).getSortValue();
    }

    // A default implementation is nice, but slow.
    @Override
    public int compare(long indexA, long indexB) {
        PropertyValue hitPropValueA = get(indexA);
        PropertyValue hitPropValueB = get(indexB);
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
     * Return the required sensitivies for all Annotations that require context.
     * Sensitivies are returned in the same order as the annotations are returned from {@link #needsContext()}
     * 
     * @return null if no context is required.
     */
    public List<MatchSensitivity> getSensitivities() {
        return null;
    }

    /**
     * If this property need context(s), how large should they be?
     * 
     * @param index index, so we can find the default context size if we need to 
     * @return required context size
     */
    public abstract ContextSize needsContextSize(BlackLabIndex index);

    @Override
    public abstract String name();

    @Override
    public abstract String serialize();

    /**
     * Used by subclasses to add a dash for reverse when serializing
     * 
     * @return either a dash or the empty string
     */
    @Override
    public String serializeReverse() {
        return reverse ? "-" : "";
    }
    
    @Override
    public HitProperty reverse() {
        return copyWith(hits, contexts, true);
    }

    /**
     * Produce a copy of this HitProperty object with a different Hits object.
     *
     * Will automatically fetch any necessary Contexts as well.
     *
     * @param hits new Hits to use
     * @return the new HitProperty object
     */
    public HitProperty copyWith(Hits hits) {
        // If the filter property requires contexts, fetch them now.
        List<Annotation> contextsNeeded = needsContext();
        HitProperty result;
        if (contextsNeeded != null) {
            List<FiidLookup> fiidLookups = FiidLookup.getList(contextsNeeded, hits.index().reader(), !hits.hasAscendingLuceneDocIds());
            Contexts contexts = new Contexts(hits, contextsNeeded, needsContextSize(hits.index()), fiidLookups);
            result = copyWith(hits, contexts, false);
        } else {
            result = copyWith(hits, null, false);
        }
        return result;
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

    @Override
    public boolean isReverse() {
        return reverse;
    }

    @Override
    public String toString() {
        return serialize();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HitProperty that = (HitProperty) o;
        return reverse == that.reverse;
    }

    @Override
    public int hashCode() {
        return Objects.hash(reverse);
    }

    @Override
    public List<HitProperty> props() {
        return null;
    }

    /**
     * Return only the DocProperty portion (if any) of this HitProperty, if any.
     * 
     * E.g. if this is a HitPropertyMultiple of HitPropertyContextWords and HitPropertyDocumentStoredField,
     * return the latter as a DocPropertyStoredField.
     * 
     * This is used for calculating the relative frequency when grouping on a metadata field.
     *
     * It is also used in HitGroupsTokenFrequencies to speed up large frequency list requests.
     * 
     * @return metadata portion of this property, or null if there is none
     */
    public DocProperty docPropsOnly() {
        return null;
    }

    /**
     * Return only the values corresponding to DocProperty's of the given PropertyValue, if any.
     * 
     * E.g. if this is a HitPropertyMultiple of HitPropertyContextWords and HitPropertyDocumentStoredField,
     * return the latter of the two values in the supplied PropertyValue.
     * 
     * This is used for calculating the relative frequency when grouping on a metadata field.
     * 
     * @param value value to extract the values corresponding to DocProperty's from
     * @return metadata portion of this value, or null if there is none
     */
    public PropertyValue docPropValues(PropertyValue value) {
        return null;
    }

    /**
     * Does this property only use the hit's direct annotations (word, lemma, etc... not surrounding context) and/or properties of the hit's document (metadata). 
     * For example, as derived statistic (such as group size, document length, decade) should return FALSE here. 
     * Properties that just read docValues and such should return TRUE.
     * @return true if it does, false if not
     */
    public abstract boolean isDocPropOrHitText();
}
