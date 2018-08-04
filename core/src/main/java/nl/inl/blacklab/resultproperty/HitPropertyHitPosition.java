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

import nl.inl.blacklab.search.Hit;
import nl.inl.blacklab.search.Hits;

/**
 * A hit property for sorting on hit token position. Usually to be combined with
 * sorting on document id, for a fast and reproducible sort.
 */
public class HitPropertyHitPosition extends HitProperty {

    public HitPropertyHitPosition(Hits hits) {
        super(hits);
    }

    @Override
    public HitPropValueInt get(int hitNumber) {
        Hit result = hits.getByOriginalOrder(hitNumber);
        return new HitPropValueInt(result.start);
    }

    @Override
    public String getName() {
        return "hit position";
    }

    @Override
    public List<String> getPropNames() {
        return Arrays.asList("hit: position");
    }

    @Override
    public int compare(Object i, Object j) {
        Hit a = hits.getByOriginalOrder((Integer) i);
        Hit b = hits.getByOriginalOrder((Integer) j);
        if (a.start == b.start)
            return reverse ? b.end - a.end : a.end - b.end;
        return reverse ? b.start - a.start : a.start - b.start;
    }

    @Override
    public String serialize() {
        return serializeReverse() + "hitposition";
    }

    public static HitPropertyHitPosition deserialize(Hits hits) {
        return new HitPropertyHitPosition(hits);
    }
}
