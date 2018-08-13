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

import java.util.Arrays;
import java.util.List;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.results.Contexts;
import nl.inl.blacklab.search.results.Hit;
import nl.inl.blacklab.search.results.Hits;

/**
 * A hit property for grouping per document.
 */
public class HitPropertyDoc extends HitProperty {

    private BlackLabIndex index;

    HitPropertyDoc(HitPropertyDoc prop, Hits hits, boolean invert) {
        super(prop, hits, null, invert);
        this.index = hits.queryInfo().index();
    }

    public HitPropertyDoc() {
        super();
    }

    @Override
    public HitProperty copyWith(Hits newHits, Contexts contexts, boolean invert) {
        return new HitPropertyDoc(this, newHits, invert);
    }

    @Override
    public HitPropValueDoc get(Hit result) {
        return new HitPropValueDoc(index.doc(result.doc()));
    }

    @Override
    public String getName() {
        return "document";
    }

    @Override
    public List<String> getPropNames() {
        return Arrays.asList("document");
    }

    @Override
    public int compare(Hit a, Hit b) {
        return reverse ? b.doc() - a.doc() : a.doc() - b.doc();
    }

    @Override
    public String serialize() {
        return serializeReverse() + "doc";
    }

    public static HitPropertyDoc deserialize(Hits hits) {
        return new HitPropertyDoc();
    }
}
