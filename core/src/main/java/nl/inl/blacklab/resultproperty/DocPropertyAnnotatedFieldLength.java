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

import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.results.DocResult;

/**
 * Retrieves the length of an annotated field (i.e. the main "contents" field) in
 * tokens.
 */
public class DocPropertyAnnotatedFieldLength extends DocProperty {

    public static DocPropertyAnnotatedFieldLength deserialize(String info) {
        return new DocPropertyAnnotatedFieldLength(info);
    }

    private String fieldName;
    
    private String friendlyName;

    DocPropertyAnnotatedFieldLength(DocPropertyAnnotatedFieldLength prop, boolean invert) {
        super(prop, invert);
        fieldName = prop.fieldName;
        friendlyName = prop.friendlyName;
    }

    public DocPropertyAnnotatedFieldLength(String fieldName, String friendlyName) {
        this.fieldName = AnnotatedFieldNameUtil.lengthTokensField(fieldName);
        this.friendlyName = friendlyName;
    }

    public DocPropertyAnnotatedFieldLength(String fieldName) {
        this(fieldName, fieldName + " length");
    }

    @Override
    public PropertyValueInt get(DocResult result) {
        try {
            int subtractClosingToken = 1;
            int length = Integer.parseInt(result.identity().luceneDoc().get(fieldName)) - subtractClosingToken;
            return new PropertyValueInt(length);
        } catch (NumberFormatException e) {
            return new PropertyValueInt(0);
        }
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
        try {
            int ia = Integer.parseInt(a.identity().luceneDoc().get(fieldName));
            int ib = Integer.parseInt(b.identity().luceneDoc().get(fieldName));
            return reverse ? ib - ia : ia - ib;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public String name() {
        return friendlyName;
    }

    @Override
    public String serialize() {
        return serializeReverse() + PropertySerializeUtil.combineParts("fieldlen", fieldName);
    }

    @Override
    public DocProperty reverse() {
        return new DocPropertyAnnotatedFieldLength(this, true);
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
        DocPropertyAnnotatedFieldLength other = (DocPropertyAnnotatedFieldLength) obj;
        if (fieldName == null) {
            if (other.fieldName != null)
                return false;
        } else if (!fieldName.equals(other.fieldName))
            return false;
        return true;
    }
    
    

}
