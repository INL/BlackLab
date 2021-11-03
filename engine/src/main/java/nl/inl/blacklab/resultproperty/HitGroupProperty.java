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

import nl.inl.blacklab.search.results.Hit;
import nl.inl.blacklab.search.results.HitGroup;

/**
 * Abstract base class for a property of a hit, like document title, hit text,
 * right context, etc.
 */
public abstract class HitGroupProperty extends GroupProperty<Hit, HitGroup> {

    static HitGroupPropertyIdentity propIdentity = new HitGroupPropertyIdentity();

    static HitGroupPropertySize propSize = new HitGroupPropertySize();

    public static HitGroupPropertyIdentity identity() {
        return propIdentity;
    }

    public static HitGroupPropertySize size() {
        return propSize;
    }

    HitGroupProperty(HitGroupProperty prop, boolean invert) {
        super(prop, invert);
    }

    public HitGroupProperty() {
        super();
    }

    @Override
    public abstract PropertyValue get(HitGroup result);

    /**
     * Compares two groups on this property
     *
     * @param a first group
     * @param b second group
     * @return 0 if equal, negative if a < b, positive if a > b.
     */
    @Override
    public abstract int compare(HitGroup a, HitGroup b);

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

    public static HitGroupProperty deserialize(String serialized) {
        if (PropertySerializeUtil.isMultiple(serialized)) {
            boolean reverse = false;
            if (serialized.startsWith("-(") && serialized.endsWith(")")) {
                reverse = true;
                serialized = serialized.substring(2, serialized.length() - 1);
            }
            HitGroupProperty result = HitGroupPropertyMultiple.deserializeProp(serialized);
            if (reverse)
                result = result.reverse();
            return result;
        }

        boolean reverse = false;
        if (serialized.length() > 0 && serialized.charAt(0) == '-') {
            reverse = true;
            serialized = serialized.substring(1);
        }
        String propName = ResultProperty.ignoreSensitivity(serialized);
        HitGroupProperty result;
        if (propName.equalsIgnoreCase("identity"))
            result = propIdentity;
        else
            result = propSize;
        if (reverse)
            result = result.reverse();
        return result;
    }

    /**
     * Is the comparison reversed?
     *
     * @return true if it is, false if not
     */
    @Override
    public boolean isReverse() {
        return reverse;
    }

    /**
     * Reverse the comparison.
     *
     * @return doc group property with reversed comparison
     */
    @Override
    public abstract HitGroupProperty reverse();

    @Override
    public String toString() {
        return serialize();
    }
}
