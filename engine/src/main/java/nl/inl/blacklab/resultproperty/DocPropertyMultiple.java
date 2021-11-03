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
import java.util.Iterator;
import java.util.List;

import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BooleanQuery.Builder;
import org.apache.lucene.search.Query;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.results.DocResult;

/**
 * A collection of GroupProperty's identifying a particular group.
 */
public class DocPropertyMultiple extends DocProperty implements Iterable<DocProperty> {
    List<DocProperty> criteria;

    DocPropertyMultiple(DocPropertyMultiple mprop, boolean invert) {
        super(mprop, invert);
        this.criteria = mprop.criteria;
    }

    /**
     * Quick way to create group criteria. Just call this method with the
     * GroupCriterium object(s) you want.
     *
     * @param criteria the desired criteria
     */
    public DocPropertyMultiple(List<DocProperty> criteria) {
        this.criteria = new ArrayList<>(criteria);
    }

    /**
     * Quick way to create group criteria. Just call this method with the
     * GroupCriterium object(s) you want.
     *
     * @param criteria the desired criteria
     */
    public DocPropertyMultiple(DocProperty... criteria) {
        this.criteria = new ArrayList<>(Arrays.asList(criteria));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((criteria == null) ? 0 : criteria.hashCode());
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
        DocPropertyMultiple other = (DocPropertyMultiple) obj;
        if (criteria == null) {
            if (other.criteria != null)
                return false;
        } else if (!criteria.equals(other.criteria))
            return false;
        return true;
    }

    public void addCriterium(DocProperty crit) {
        criteria.add(crit);
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        int i = 0;
        for (DocProperty prop : criteria) {
            if (i > 0)
                str.append(",");
            str.append(prop.toString());
            i++;
        }
        return str.toString();
    }

    @Override
    public Iterator<DocProperty> iterator() {
        return criteria.iterator();
    }

    @Override
    public PropertyValueMultiple get(DocResult result) {
        PropertyValue[] rv = new PropertyValue[criteria.size()];
        int i = 0;
        for (DocProperty crit : criteria) {
            rv[i] = crit.get(result);
            i++;
        }
        return new PropertyValueMultiple(rv);
    }

    /**
     * Compares two docs on this property
     * 
     * @param a first doc
     * @param b second doc
     * @return 0 if equal, negative if a < b, positive if a > b.
     */
    @Override
    public int compare(DocResult a, DocResult b) {
        for (DocProperty crit : criteria) {
            int cmp = reverse ? crit.compare(b, a) : crit.compare(a, b);
            if (cmp != 0)
                return cmp;
        }
        return 0;
    }

    @Override
    public String name() {
        StringBuilder b = new StringBuilder();
        for (DocProperty crit : criteria) {
            if (b.length() > 0)
                b.append(", ");
            b.append(crit.name());
        }
        return b.toString();
    }

    @Override
    public String serialize() {
        String[] values = new String[criteria.size()];
        for (int i = 0; i < criteria.size(); i++) {
            values[i] = criteria.get(i).serialize();
        }
        return (reverse ? "-(" : "") + PropertySerializeUtil.combineMultiple(values) + (reverse ? ")" : "");
    }
    
    @Override
    public boolean isCompound() {
        return true;
    }
    
    @Override
    public List<DocProperty> props() {
        return Collections.unmodifiableList(criteria);
    }

    public static DocPropertyMultiple deserialize(BlackLabIndex index, String info) {
        String[] strValues = PropertySerializeUtil.splitMultiple(info);
        DocProperty[] values = new DocProperty[strValues.length];
        int i = 0;
        for (String strValue : strValues) {
            values[i] = DocProperty.deserialize(index, strValue);
            i++;
        }
        return new DocPropertyMultiple(values);
    }

    @Override
    public DocProperty reverse() {
        return new DocPropertyMultiple(this, true);
    }

    @Override
    public Query query(BlackLabIndex index, PropertyValue value) {
        PropertyValue[] values = ((PropertyValueMultiple)value).value();
        Builder bqb = new BooleanQuery.Builder();
        int n = 0;
        for (int i = 0; i < criteria.size(); i++) {
            Query q = criteria.get(i).query(index, values[i]);
            if (q != null) {
                bqb.add(q, Occur.MUST);
                n++;
            }
        }
        if (n > 0)
            return bqb.build();
        return null;
    }
    
    @Override
    public boolean canConstructQuery(BlackLabIndex index, PropertyValue value) {
        PropertyValue[] values = ((PropertyValueMultiple)value).value();
        for (int i = 0; i < criteria.size(); i++) {
            if (!criteria.get(i).canConstructQuery(index, values[i]))
                return false;
        }
        return true;
    }
    
}
