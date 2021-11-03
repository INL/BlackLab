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

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.results.Contexts;
import nl.inl.blacklab.search.results.Hits;

/**
 * Base class for HitPropertyHitText, LeftContext, RightContext, WordLeft and
 * WordRight.
 */
public abstract class HitPropertyContextBase extends HitProperty {

    protected static <T extends HitPropertyContextBase> T deserializeProp(Class<T> cls, BlackLabIndex index, AnnotatedField field, String info) {
        String[] parts = PropertySerializeUtil.splitParts(info);
        String propName = parts[0];
        if (propName.length() == 0)
            propName = AnnotatedFieldNameUtil.getDefaultMainAnnotationName();
        MatchSensitivity sensitivity = parts.length > 1 ? MatchSensitivity.fromLuceneFieldSuffix(parts[1])
                : MatchSensitivity.SENSITIVE;
//        ContextSize contextSize = parts.length > 2 ? ContextSize.get(Integer.parseInt(parts[2]))
//                : index.defaultContextSize();
        Annotation annotation = field.annotation(propName);
        try {
            Constructor<T> ctor = cls.getConstructor(BlackLabIndex.class, Annotation.class, MatchSensitivity.class);
            return ctor.newInstance(index, annotation, sensitivity);
        } catch (ReflectiveOperationException | SecurityException | IllegalArgumentException e) {
            throw new BlackLabRuntimeException("Couldn't deserialize hit property: " + cls.getName() + ":" + info, e);
        }
    }

    protected Terms terms;

    protected Annotation annotation;

    protected MatchSensitivity sensitivity;

//    protected ContextSize contextSize;

    protected String name;

    protected String serializeName;

    protected BlackLabIndex index;

    public HitPropertyContextBase(HitPropertyContextBase prop, Hits hits, Contexts contexts, boolean invert) {
        super(prop, hits, contexts, invert);
        this.terms = prop.terms;
        this.annotation = prop.annotation;
        if (hits != null && !hits.field().equals(this.annotation.field())) {
            throw new IllegalArgumentException(
                    "Hits passed to HitProperty must be in the field it was declared with! (declared with "
                            + this.annotation.field().name() + ", hits has " + hits.field().name() + "; class=" + getClass().getName() + ")");
        }
        this.sensitivity = prop.sensitivity;
//        this.contextSize = prop.contextSize;
        this.name = prop.name;
        this.serializeName = prop.serializeName;
        this.index = hits == null ? prop.index : hits.index();
    }

    public HitPropertyContextBase(String name, String serializeName, BlackLabIndex index, Annotation annotation,
            MatchSensitivity sensitivity/*, ContextSize contextSize*/) {
        super();
        this.name = name;
        this.serializeName = serializeName;
        this.index = index;
        this.annotation = annotation == null ? index.mainAnnotatedField().mainAnnotation() : annotation;
        this.terms = index.annotationForwardIndex(this.annotation).terms();
        this.sensitivity = sensitivity == null ? index.defaultMatchSensitivity() : sensitivity;
//        this.contextSize = contextSize == null ? index.defaultContextSize() : contextSize;
    }

    @Override
    public List<Annotation> needsContext() {
        return Arrays.asList(annotation);
    }
    
    @Override
    public List<MatchSensitivity> getSensitivities() {
        return Arrays.asList(sensitivity);
    }

//    @Override
//    public ContextSize needsContextSize(BlackLabIndex index) {
//        return contextSize;
//    }

    @Override
    public String name() {
        return name + ": " + annotation.name();
    }

    @Override
    public String serialize() {
        return serializeReverse()
                + PropertySerializeUtil.combineParts(serializeName, annotation.name(), sensitivity.luceneFieldSuffix());
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((annotation == null) ? 0 : annotation.hashCode());
//        result = prime * result + ((contextSize == null) ? 0 : contextSize.hashCode());
        result = prime * result + ((index == null) ? 0 : index.hashCode());
        result = prime * result + ((sensitivity == null) ? 0 : sensitivity.hashCode());
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
        HitPropertyContextBase other = (HitPropertyContextBase) obj;
        if (annotation == null) {
            if (other.annotation != null)
                return false;
        } else if (!annotation.equals(other.annotation))
            return false;
//        if (contextSize == null) {
//            if (other.contextSize != null)
//                return false;
//        } else if (!contextSize.equals(other.contextSize))
//            return false;
        if (index == null) {
            if (other.index != null)
                return false;
        } else if (!index.equals(other.index))
            return false;
        if (sensitivity != other.sensitivity)
            return false;
        return true;
    }
    
}
