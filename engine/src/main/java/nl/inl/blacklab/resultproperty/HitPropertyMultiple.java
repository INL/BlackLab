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
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.results.ContextSize;
import nl.inl.blacklab.search.results.Contexts;
import nl.inl.blacklab.search.results.Hits;

/**
 * A collection of GroupProperty's identifying a particular group.
 */
public class HitPropertyMultiple extends HitProperty implements Iterable<HitProperty> {
    
    static HitPropertyMultiple deserializeProp(BlackLabIndex index, AnnotatedField field, String info) {
        String[] strValues = PropertySerializeUtil.splitMultiple(info);
        HitProperty[] values = new HitProperty[strValues.length];
        int i = 0;
        for (String strValue: strValues) {
            values[i] = HitProperty.deserialize(index, field, strValue);
            i++;
        }
        return new HitPropertyMultiple(values);
    }

    /** The properties we're combining */
    List<HitProperty> properties;

    /** All the contexts needed by the criteria */
    List<Annotation> contextNeeded;
    
    /** Sensitivities needed for the criteria in contextNeeded (same order) */
    List<MatchSensitivity> sensitivities; 
    
    /** Which of the contexts do the individual properties need? */
    Map<HitProperty, IntArrayList> contextIndicesPerProperty;
    
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
        return properties.stream().map(p -> p.needsContextSize(index)).filter(s -> s != null).reduce( (a, b) -> ContextSize.union(a, b) ).orElse(null);
    }

    @Override
    public PropertyValueMultiple get(int hitIndex) {
        PropertyValue[] rv = new PropertyValue[properties.size()];
        int i = 0;
        for (HitProperty crit: properties) {
            rv[i] = crit.get(hitIndex);
            i++;
        }
        return new PropertyValueMultiple(rv);
    }

    @Override
    public int compare(int indexA, int indexB) {
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
        String[] values = new String[properties.size()];
        for (int i = 0; i < properties.size(); i++) {
            values[i] = properties.get(i).serialize();
        }
        return (reverse ? "-(" : "") + PropertySerializeUtil.combineMultiple(values) + (reverse ? ")" : "");
    }

    @Override
    public DocProperty docPropsOnly() {
        List<DocProperty> crit = properties.stream().map(hp -> hp.docPropsOnly()).filter(prop -> prop != null).collect(Collectors.toList());
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
        List<PropertyValue> values = value.values();
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
