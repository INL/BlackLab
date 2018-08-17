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
import java.util.List;

import nl.inl.blacklab.search.results.DocGroup;
import nl.inl.blacklab.search.results.DocGroups;
import nl.inl.blacklab.search.results.DocResult;
import nl.inl.blacklab.search.results.ResultGroups;
import nl.inl.blacklab.search.results.Results;
import nl.inl.blacklab.search.results.SampleParameters;
import nl.inl.blacklab.search.results.WindowStats;

/**
 * Abstract base class for a property of a hit, like document title, hit text,
 * right context, etc.
 */
public abstract class DocGroupProperty extends GroupProperty<DocResult, DocGroup> {

    static DocGroupPropertyIdentity propIdentity = new DocGroupPropertyIdentity();

    static DocGroupPropertySize propSize = new DocGroupPropertySize();

    public static DocGroupPropertyIdentity identity() {
        return propIdentity;
    }

    public static DocGroupPropertySize size() {
        return propSize;
    }
    
    public static DocGroupProperty deserialize(String serialized) {
        boolean reverse = false;
        if (serialized.length() > 0 && serialized.charAt(0) == '-') {
            reverse = true;
            serialized = serialized.substring(1);
        }
        DocGroupProperty result;
        if (serialized.equalsIgnoreCase("identity"))
            result = propIdentity;
        else
            result = propSize;
        if (reverse)
            result = result.reverse();
        return result;
    }

    protected DocGroupProperty(DocGroupProperty prop, boolean invert) {
        super(prop, invert);
    }
    
    protected DocGroupProperty() {
        super();
    }

    @Override
    public abstract PropertyValue get(DocGroup result);

    /**
     * Compares two groups on this property
     * 
     * @param a first group
     * @param b second group
     * @return 0 if equal, negative if a < b, positive if a > b.
     */
    @Override
    public abstract int compare(DocGroup a, DocGroup b);

    @Override
    public abstract String serialize();

    /**
     * Used by subclasses to add a dash for reverse when serializing
     * 
     * @return either a dash or the empty string
     */
    @Override
    protected String serializeReverse() {
        return reverse ? "-" : "";
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
    public abstract DocGroupProperty reverse();

    @Override
    public String toString() {
        return serialize();
    }

    @Override
    public List<String> getPropNames() {
        return Arrays.asList(getName());
    }

    @SuppressWarnings("unchecked")
    @Override
    public Results<DocGroup> sortResults(Results<DocGroup> results) {
        List<DocGroup> list = new ArrayList<>(results.resultsList());
        list.sort(this);
        return DocGroups.fromList(results.queryInfo(), list, ((ResultGroups<DocResult>)results).getGroupCriteria(), (SampleParameters)null, (WindowStats)null);
    }
}
