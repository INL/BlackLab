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

import java.io.IOException;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.results.Contexts;
import nl.inl.blacklab.search.results.Hit;
import nl.inl.blacklab.search.results.Results;

/**
 * A hit property for grouping on by decade based on a stored field in the
 * corresponding Lucene document containing a year.
 */
public class HitPropertyDocumentDecade extends HitProperty {

    /** The value we store when the decade is unknown */
    public static final int UNKNOWN_VALUE = 10000000;

    static HitPropertyDocumentDecade deserializeProp(BlackLabIndex index, String info) {
        return new HitPropertyDocumentDecade(index.metadataField(info));
    }

    IndexReader reader;

    String fieldName;

    HitPropertyDocumentDecade(HitPropertyDocumentDecade prop, Results<Hit> hits, boolean invert) {
        super(prop, hits, null, invert);
        this.reader = hits == null ? null : hits.index().reader();
        this.fieldName = prop.fieldName;
    }

    public HitPropertyDocumentDecade(MetadataField field) {
        super();
        this.fieldName = field.name();
    }

    @Override
    public HitProperty copyWith(Results<Hit> newHits, Contexts contexts, boolean invert) {
        return new HitPropertyDocumentDecade(this, newHits, invert);
    }

    @Override
    public PropertyValueDecade get(Hit result) {
        try {
            Document d = reader.document(result.doc());
            String strYear = d.get(fieldName);
            int year;
            try {
                year = Integer.parseInt(strYear);
                year -= year % 10;
            } catch (NumberFormatException e) {
                year = UNKNOWN_VALUE;
            }
            return new PropertyValueDecade(year);
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    @Override
    public int compare(Hit a, Hit b) {
        try {
            Document d = reader.document(a.doc());
            String strYearA = d.get(fieldName);
            if (strYearA == null)
                strYearA = "";
            d = reader.document(b.doc());
            String strYearB = d.get(fieldName);
            if (strYearB == null)
                strYearB = "";
            if (strYearA.length() == 0) // sort missing year at the end
                return strYearB.length() == 0 ? 0 : (reverse ? -1 : 1);
            if (strYearB.length() == 0) // sort missing year at the end
                return reverse ? 1 : -1;
            int aYear;
            try {
                aYear = Integer.parseInt(strYearA);
                aYear -= aYear % 10;
            } catch (NumberFormatException e) {
                aYear = UNKNOWN_VALUE;
            }
            int bYear;
            try {
                bYear = Integer.parseInt(strYearB);
                bYear -= bYear % 10;
            } catch (NumberFormatException e) {
                bYear = UNKNOWN_VALUE;
            }

            return reverse ? bYear - aYear : aYear - bYear;

        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    @Override
    public String name() {
        return "document: decade";
    }

    @Override
    public String serialize() {
        return serializeReverse() + PropertySerializeUtil.combineParts("decade", fieldName);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((fieldName == null) ? 0 : fieldName.hashCode());
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
        HitPropertyDocumentDecade other = (HitPropertyDocumentDecade) obj;
        if (fieldName == null) {
            if (other.fieldName != null)
                return false;
        } else if (!fieldName.equals(other.fieldName))
            return false;
        return true;
    }

    @Override
    public DocProperty docPropsOnly() {
        DocPropertyDecade result = new DocPropertyDecade(fieldName);
        return reverse ? result.reverse() : result;
    }

    @Override
    public PropertyValue docPropValues(PropertyValue value) {
        return value;
    }
    
}
