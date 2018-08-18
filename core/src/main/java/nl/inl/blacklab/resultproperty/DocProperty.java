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
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.search.results.DocResult;

/**
 * Abstract base class for criteria on which to group DocResult objects.
 * Subclasses implement specific grouping criteria (number of hits, the value of
 * a stored field in the Lucene document, ...)
 */
public abstract class DocProperty implements ResultProperty<DocResult> {
    protected static final Logger logger = LogManager.getLogger(DocProperty.class);

    /** Reverse comparison result or not? */
    protected boolean reverse = false;
    
    protected DocProperty(DocProperty prop, boolean invert) {
        reverse = invert ? !prop.reverse : prop.reverse;
    }
    
    protected DocProperty() {
    }

    /**
     * Get the desired grouping/sorting property from the DocResult object
     *
     * @param result the result to get the grouping property for
     * @return the grouping property. e.g. this might be "Harry Mulisch" when
     *         grouping on author.
     */
    @Override
    public abstract PropertyValue get(DocResult result);

    /**
     * Compares two docs on this property
     * 
     * @param a first doc
     * @param b second doc
     * @return 0 if equal, negative if a < b, positive if a > b.
     */
    @Override
    public int compare(DocResult a, DocResult b) {
        return get(a).compareTo(get(b));
    }

    @Override
    public abstract String name();

    @Override
    public abstract String serialize();

    /**
     * Used by subclasses to add a dash for reverse when serializing
     * 
     * @return either a dash or the empty string
     */
    protected String serializeReverse() {
        return reverse ? "-" : "";
    }

    public static DocProperty deserialize(String serialized) {
        if (PropertySerializeUtil.isMultiple(serialized)) {
            boolean reverse = false;
            if (serialized.startsWith("-(") && serialized.endsWith(")")) {
                reverse = true;
                serialized = serialized.substring(2, serialized.length() - 1);
            }
            DocProperty result = DocPropertyMultiple.deserialize(serialized);
            if (reverse)
                result = result.reverse();
            return result;
        }

        boolean reverse = false;
        if (serialized.length() > 0 && serialized.charAt(0) == '-') {
            reverse = true;
            serialized = serialized.substring(1);
        }

        String[] parts = PropertySerializeUtil.splitPartFirstRest(serialized);
        String type = parts[0].toLowerCase();
        String info = parts.length > 1 ? parts[1] : "";
        List<String> types = Arrays.asList("decade", "numhits", "field", "fieldlen");
        int typeNum = types.indexOf(type);
        DocProperty result;
        switch (typeNum) {
        case 0:
            result = DocPropertyDecade.deserialize(info);
            break;
        case 1:
            result = DocPropertyNumberOfHits.deserialize();
            break;
        case 2:
            result = DocPropertyStoredField.deserialize(info);
            break;
        case 3:
            result = DocPropertyAnnotatedFieldLength.deserialize(info);
            break;
        default:
            logger.debug("Unknown DocProperty '" + type + "'");
            return null;
        }
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
     * @return document property with the comparison reversed
     */
    @Override
    public abstract DocProperty reverse();

    @Override
    public String toString() {
        return serialize();
    }

    /**
     * Get the names of all (sub-)properties separately.
     * 
     * @return the list
     */
    @Override
    public abstract List<String> propNames();

    public static void getFacetsUrlParam(Map<String, String> param, List<DocProperty> facets) {
        DocPropertyMultiple f = new DocPropertyMultiple(facets.toArray(new DocProperty[0]));
        param.put("facets", f.serialize());
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (reverse ? 1231 : 1237);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        DocProperty other = (DocProperty) obj;
        if (reverse != other.reverse)
            return false;
        return true;
    }
    
}
