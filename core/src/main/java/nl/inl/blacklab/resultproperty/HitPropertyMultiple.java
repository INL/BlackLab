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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.results.ContextSize;
import nl.inl.blacklab.search.results.Contexts;
import nl.inl.blacklab.search.results.Hits;

/**
 * A collection of GroupProperty's identifying a particular group.
 */
public class HitPropertyMultiple extends HitProperty implements Iterable<HitProperty> {
    
    /** The properties we're combining */
    List<HitProperty> properties;

    /** All the contexts needed by the criteria */
    List<Annotation> contextNeeded;
    
    /** Which of the contexts do the individual properties need? */
    Map<HitProperty, List<Integer>> contextIndicesPerProperty;
    
    public HitPropertyMultiple(HitPropertyMultiple prop, List<HitProperty> criteria, boolean invert) {
        super(prop, null, null, invert);
        this.properties = criteria;
        this.contextNeeded = prop.contextNeeded;
        this.contextIndicesPerProperty = prop.contextIndicesPerProperty;
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
        for (HitProperty prop: properties) {
            List<Annotation> requiredContext = prop.needsContext();
            if (requiredContext != null) {
                for (Annotation c: requiredContext) {
                    if (!result.contains(c))
                        result.add(c);
                }
            }
        }
        contextNeeded = result.isEmpty() ? null : result;
        
        // Let criteria know what context number(s) they need
        contextIndicesPerProperty = new HashMap<>();
        for (HitProperty prop: properties) {
            List<Annotation> requiredContext = prop.needsContext();
            if (requiredContext != null) {
                List<Integer> contextNumbers = new ArrayList<>();
                for (Annotation c: requiredContext) {
                    contextNumbers.add(contextNeeded.indexOf(c));
                }
                contextIndicesPerProperty.put(prop, contextNumbers);
            }
        }
        contextNeeded = result.isEmpty() ? null : result;
    }

    @Override
    public HitProperty copyWith(Hits newHits, Contexts contexts, boolean invert) {
        int n = properties.size();
        HitProperty[] newCriteria = new HitProperty[n];
        for (int i = 0; i < n; i++) {
            HitProperty prop = properties.get(i);
            newCriteria[i] = prop.copyWith(newHits, contexts);
            List<Integer> indices = contextIndicesPerProperty.get(prop);
            if (indices != null)
                newCriteria[i].setContextIndices(indices);
        }
        return new HitPropertyMultiple(invert ? !reverse : reverse, newCriteria);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj instanceof HitPropertyMultiple) {
            return ((HitPropertyMultiple) obj).properties.equals(properties);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return properties.hashCode();
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
    public ContextSize needsContextSize(BlackLabIndex index) {
        // Get ContextSize that's large enough for all our properties
        return properties.stream().map(p -> p.needsContextSize(index)).reduce( (a, b) -> ContextSize.union(a, b) ).orElse(index.defaultContextSize());
    }

    @Override
    public HitPropValueMultiple get(int hitNumber) {
        HitPropValue[] rv = new HitPropValue[properties.size()];
        int i = 0;
        for (HitProperty crit: properties) {
            rv[i] = crit.get(hitNumber);
            i++;
        }
        return new HitPropValueMultiple(rv);
    }

    @Override
    public int compare(Object i, Object j) {
        for (HitProperty crit: properties) {
            int cmp = reverse ? crit.compare(j, i) : crit.compare(i, j);
            if (cmp != 0)
                return cmp;
        }
        return 0;
    }

    @Override
    public String getName() {
        StringBuilder b = new StringBuilder();
        for (HitProperty crit: properties) {
            if (b.length() > 0)
                b.append(", ");
            b.append(crit.getName());
        }
        return b.toString();
    }

    @Override
    public List<String> getPropNames() {
        List<String> names = new ArrayList<>();
        for (HitProperty prop: properties)
            names.addAll(prop.getPropNames());
        return names;
    }

    @Override
    public String serialize() {
        String[] values = new String[properties.size()];
        for (int i = 0; i < properties.size(); i++) {
            values[i] = properties.get(i).serialize();
        }
        return (reverse ? "-(" : "") + PropValSerializeUtil.combineMultiple(values) + (reverse ? ")" : "");
    }

    public static HitPropertyMultiple deserialize(Hits hits, String info) {
        String[] strValues = PropValSerializeUtil.splitMultiple(info);
        HitProperty[] values = new HitProperty[strValues.length];
        int i = 0;
        for (String strValue: strValues) {
            values[i] = HitProperty.deserialize(hits, strValue);
            i++;
        }
        return new HitPropertyMultiple(values);
    }
}
