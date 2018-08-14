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
import java.util.Arrays;
import java.util.List;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.search.results.Contexts;
import nl.inl.blacklab.search.results.Hit;
import nl.inl.blacklab.search.results.Hits;

/**
 * A hit property for grouping on a stored field in the corresponding Lucene
 * document.
 */
public class HitPropertyDocumentStoredField extends HitProperty {
    IndexReader reader;

    String fieldName;

    private String friendlyName;

    HitPropertyDocumentStoredField(HitPropertyDocumentStoredField prop, Hits hits, boolean invert) {
        super(prop, hits, null, invert);
        this.reader = hits.index().reader();
        this.fieldName = prop.fieldName;
        this.friendlyName = prop.friendlyName;
    }

    public HitPropertyDocumentStoredField(String fieldName, String friendlyName) {
        super();
        this.fieldName = fieldName;
        this.friendlyName = friendlyName;
    }

    public HitPropertyDocumentStoredField(String fieldName) {
        this(fieldName, fieldName);
    }

    @Override
    public HitProperty copyWith(Hits newHits, Contexts contexts, boolean invert) {
        return new HitPropertyDocumentStoredField(this, newHits, invert);
    }

    @Override
    public HitPropValueString get(Hit result) {
        try {
            Document d = reader.document(result.doc());
            String value = d.get(fieldName);
            if (value == null)
                value = "";
            return new HitPropValueString(value);
        } catch (Exception e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    @Override
    public int compare(Hit a, Hit b) {
        try {
            Document d = reader.document(a.doc());
            String va = d.get(fieldName);
            if (va == null)
                va = "";
            d = reader.document(b.doc());
            String vb = d.get(fieldName);
            if (vb == null)
                vb = "";
            if (va.length() == 0) // sort empty string at the end
                return vb.length() == 0 ? 0 : (reverse ? -1 : 1);
            if (vb.length() == 0) // sort empty string at the end
                return reverse ? 1 : -1;

            return reverse ? HitPropValue.collator.compare(vb, va) : HitPropValue.collator.compare(va, vb);
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    @Override
    public String getName() {
        return friendlyName;
    }

    @Override
    public List<String> getPropNames() {
        return Arrays.asList("document: " + friendlyName);
    }

    @Override
    public String serialize() {
        return serializeReverse() + PropValSerializeUtil.combineParts("field", fieldName);
    }

    public static HitPropertyDocumentStoredField deserialize(Hits hits, String info) {
        return new HitPropertyDocumentStoredField(info);
    }
}
