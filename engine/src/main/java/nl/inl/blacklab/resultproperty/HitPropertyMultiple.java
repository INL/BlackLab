package nl.inl.blacklab.resultproperty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.results.ContextSize;
import nl.inl.blacklab.search.results.Contexts;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.util.PropertySerializeUtil;

/**
 * A collection of GroupProperty's identifying a particular group.
 */
public class HitPropertyMultiple extends HitProperty implements Iterable<HitProperty> {
    
    static HitProperty deserializeProp(BlackLabIndex index, AnnotatedField field, String info) {
        String[] strValues = PropertySerializeUtil.splitMultiple(info);
        List<HitProperty> values = new ArrayList<>();
        for (String strValue: strValues) {
            if (!strValue.isEmpty()) {
                values.add(HitProperty.deserialize(index, field, strValue));
            }
        }
        if (values.size() == 1) {
            // E.g. "docid,"
            return values.get(0);
        }
        return new HitPropertyMultiple(values);
    }

    /** The properties we're combining */
    final List<HitProperty> properties;

    /** All the contexts needed by the criteria */
    final List<Annotation> contextNeeded;
    
    /** Sensitivities needed for the criteria in contextNeeded (same order) */
    final List<MatchSensitivity> sensitivities;
    
    /** Which of the contexts do the individual properties need? */
    final Map<HitProperty, IntArrayList> contextIndicesPerProperty;
    
    HitPropertyMultiple(HitPropertyMultiple mprop, Hits newHits, Contexts contexts, boolean invert) {
        super(mprop, null, null, invert);
        int n = mprop.properties.size();
        this.contextNeeded = mprop.contextNeeded;
        this.sensitivities = mprop.sensitivities;
        this.properties = new ArrayList<>();
        this.contextIndicesPerProperty = new HashMap<>();
        for (int i = 0; i < n; i++) {
            HitProperty prop = mprop.properties.get(i);
            HitProperty nprop = prop.copyWith(newHits, contexts);
            IntArrayList indices = mprop.contextIndicesPerProperty.get(prop);
            if (indices != null) {
                contextIndicesPerProperty.put(nprop, indices);
                prop.setContextIndices(indices);
            }
            this.properties.add(nprop);
        }
    }

    /**
     * Quick way to create group criteria. Just call this method with the
     * GroupCriterium object(s) you want.
     *
     * @param properties the desired criteria
     */
    public HitPropertyMultiple(List<HitProperty> properties) {
        this(false, properties);
    }

    /**
     * Quick way to create group criteria. Just call this method with the
     * GroupCriterium object(s) you want.
     *
     * @param reverse reverse sort?
     * @param properties the desired criteria
     */
    public HitPropertyMultiple(boolean reverse, List<HitProperty> properties) {
        this(reverse, properties.toArray(HitProperty[]::new));
    }

    /**
     * Quick way to create group criteria. Just call this method with the
     * GroupCriterium object(s) you want.
     *
     * @param properties the desired criteria
     */
    public HitPropertyMultiple(HitProperty... properties) {
        this(false, properties);
    }
    
    /**
     * Quick way to create group criteria. Just call this method with the
     * GroupCriterium object(s) you want.
     * 
     * @param reverse reverse sort? 
     * @param properties the desired criteria
     */
    public HitPropertyMultiple(boolean reverse, HitProperty... properties) {
        super();
        this.properties = new ArrayList<>(Arrays.asList(properties));
        this.reverse = reverse;

        // Determine what context we need for each property, and let the properties know
        // at what context index/indices they can find the context(s) they need.
        // Figure out what context(s) we need
        List<Annotation> result = new ArrayList<>();
        List<MatchSensitivity> sensitivities = new ArrayList<>();
        for (HitProperty prop: properties) {
            List<Annotation> requiredContext = prop.needsContext();
            List<MatchSensitivity> propSensitivities = prop.getSensitivities();
            
            if (requiredContext == null)
                continue;
            
            for (int i = 0; i < requiredContext.size(); ++i) {
                final Annotation c = requiredContext.get(i);
                final MatchSensitivity ms = propSensitivities.get(i);
                
                if (!result.contains(c)) {
                    result.add(c);
                    sensitivities.add(ms);
                }
            }
        }
        this.contextNeeded = result.isEmpty() ? null : result;
        this.sensitivities = sensitivities.isEmpty() ? null : sensitivities; 
        
        // Let criteria know what context number(s) they need
        contextIndicesPerProperty = new HashMap<>();
        for (HitProperty prop: properties) {
            List<Annotation> requiredContext = prop.needsContext();
            if (requiredContext != null) {
                IntArrayList contextNumbers = new IntArrayList();
                for (Annotation c: requiredContext) {
                    contextNumbers.add(contextNeeded.indexOf(c));
                }
                contextIndicesPerProperty.put(prop, contextNumbers);
                prop.setContextIndices(contextNumbers);
            }
        }
    }

    @Override
    public HitProperty copyWith(Hits newHits, Contexts contexts, boolean invert) {
        return new HitPropertyMultiple(this, newHits, contexts, invert);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((properties == null) ? 0 : properties.hashCode());
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
        HitPropertyMultiple other = (HitPropertyMultiple) obj;
        if (properties == null) {
            if (other.properties != null)
                return false;
        } else if (!properties.equals(other.properties))
            return false;
        return true;
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        int i = 0;
        for (HitProperty prop: properties) {
            if (i > 0)
                str.append(",");
            str.append(prop.toString());
            i++;
        }
        return str.toString();
    }

    @Override
    public Iterator<HitProperty> iterator() {
        return properties.iterator();
    }

    @Override
    public List<Annotation> needsContext() {
        return contextNeeded;
    }
    
    @Override
    public List<MatchSensitivity> getSensitivities() {
        return sensitivities;
    }

    @Override
    public ContextSize needsContextSize(BlackLabIndex index) {
        // Get ContextSize that's large enough for all our properties
        return properties.stream().map(p -> p.needsContextSize(index)).filter(Objects::nonNull).reduce(ContextSize::union).orElse(null);
    }

    @Override
    public PropertyValueMultiple get(long hitIndex) {
        PropertyValue[] rv = new PropertyValue[properties.size()];
        int i = 0;
        for (HitProperty crit: properties) {
            rv[i] = crit.get(hitIndex);
            i++;
        }
        return new PropertyValueMultiple(rv);
    }

    @Override
    public int compare(long indexA, long indexB) {
        for (HitProperty crit: properties) {
            int cmp = reverse ? crit.compare(indexB, indexA) : crit.compare(indexA, indexB);
            if (cmp != 0)
                return cmp;
        }
        return 0;
    }

    @Override
    public String name() {
        StringBuilder b = new StringBuilder();
        for (HitProperty crit: properties) {
            if (b.length() > 0)
                b.append(", ");
            b.append(crit.name());
        }
        return b.toString();
    }
    
    @Override
    public boolean isCompound() {
        return true;
    }
    
    @Override
    public List<HitProperty> props() {
        return Collections.unmodifiableList(properties);
    }

    @Override
    public String serialize() {
        return getString();
    }

    private String getString() {
        return PropertySerializeUtil.serializeMultiple(reverse, properties);
    }

    @Override
    public DocProperty docPropsOnly() {
        List<DocProperty> crit = properties.stream().map(HitProperty::docPropsOnly).filter(Objects::nonNull).collect(Collectors.toList());
        if (crit.isEmpty())
            return null;
        if (crit.size() == 1) {
            return crit.get(0);
        }
        return new DocPropertyMultiple(crit);
    }

    @Override
    public PropertyValue docPropValues(PropertyValue value) {
        List<PropertyValue> result = new ArrayList<>();
        List<PropertyValue> values = value.valuesList();
        int i = 0;
        for (HitProperty prop: properties) {
            PropertyValue v = prop.docPropValues(values.get(i));
            if (v != null)
                result.add(v);
            i++;
        }
        return new PropertyValueMultiple(result);
    }

    @Override
    public boolean isDocPropOrHitText() {
        for (HitProperty p : properties) {
            if (!p.isDocPropOrHitText()) 
                return false;
        }
        return true;
    }
}
