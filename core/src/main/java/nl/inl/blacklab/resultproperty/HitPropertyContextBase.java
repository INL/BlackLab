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
import nl.inl.blacklab.search.results.ContextSize;
import nl.inl.blacklab.search.results.Hits;

/**
 * Base class for HitPropertyHitText, LeftContext, RightContext, WordLeft and WordRight.
 */
public abstract class HitPropertyContextBase extends HitProperty {

    protected Terms terms;

    protected Annotation annotation;

    protected MatchSensitivity sensitivity;

    protected ContextSize contextSize;
    
    protected String name;
    
    protected String serializeName;

    public HitPropertyContextBase(String name, String serializeName, Hits hits, Annotation annotation, MatchSensitivity sensitivity, ContextSize contextSize) {
        super(hits);
        this.name = name;
        this.serializeName = serializeName;
        BlackLabIndex index = hits.queryInfo().index();
        this.annotation = annotation == null ? hits.queryInfo().field().mainAnnotation() : annotation;
        this.sensitivity = sensitivity == null ? index.defaultMatchSensitivity() : sensitivity;
        this.contextSize = contextSize == null ? index.defaultContextSize() : contextSize;
        this.terms = index.annotationForwardIndex(this.annotation).terms();
    }

    public HitPropertyContextBase(String name, String serializeName, BlackLabIndex index, Annotation annotation, MatchSensitivity sensitivity, ContextSize contextSize) {
        super();
        this.name = name;
        this.serializeName = serializeName;
        this.annotation = annotation == null ? index.mainAnnotatedField().mainAnnotation(): annotation;
        this.terms = index.annotationForwardIndex(this.annotation).terms();
        this.sensitivity = sensitivity == null ? index.defaultMatchSensitivity() : sensitivity;
        this.contextSize = contextSize == null ? index.defaultContextSize() : contextSize;
    }

    @Override
    public List<Annotation> needsContext() {
        return Arrays.asList(annotation);
    }
    
    @Override
    public ContextSize needsContextSize(BlackLabIndex index) {
        return contextSize;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public List<String> getPropNames() {
        return Arrays.asList(name + ": " + annotation.name());
    }

    @Override
    public String serialize() {
        return serializeReverse() + PropValSerializeUtil.combineParts(serializeName, annotation.name(), sensitivity.luceneFieldSuffix());
    }
    
    public static <T extends HitPropertyContextBase> T deserialize(Class<T> cls, Hits hits, String info) {
        String[] parts = PropValSerializeUtil.splitParts(info);
        AnnotatedField field = hits.queryInfo().field();
        String propName = parts[0];
        if (propName.length() == 0)
            propName = AnnotatedFieldNameUtil.getDefaultMainAnnotationName();
        MatchSensitivity sensitivity = parts.length > 1 ? MatchSensitivity.fromLuceneFieldSuffix(parts[1]) : MatchSensitivity.SENSITIVE;
        ContextSize contextSize = parts.length > 2 ? ContextSize.get(Integer.parseInt(parts[2])) : hits.queryInfo().index().defaultContextSize();
        Annotation annotation = field.annotation(propName);
        try {
            Constructor<T> ctor = cls.getConstructor(Hits.class, Annotation.class, MatchSensitivity.class, ContextSize.class);
            return ctor.newInstance(hits, annotation, sensitivity, contextSize);
        } catch (ReflectiveOperationException | SecurityException | IllegalArgumentException e) {
            throw new BlackLabRuntimeException("Couldn't deserialize hit property: " + cls.getName() + ":" + info, e);
        }
    }
}
