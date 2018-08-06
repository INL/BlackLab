package nl.inl.blacklab.search.indexmetadata;

import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import nl.inl.blacklab.search.indexmetadata.nint.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.nint.AnnotatedFields;

final class AnnotatedFieldsImpl implements AnnotatedFields {
    /** The complex fields in our index */
    private Map<String, AnnotatedFieldImpl> complexFields;
    
    /**
     * The main contents field in our index. This is either the complex field with
     * the name "contents", or if that doesn't exist, the first complex field found.
     */
    private AnnotatedFieldImpl mainContentsField;

    public AnnotatedFieldsImpl() {
        complexFields = new TreeMap<>();
    }

    @Override
    public AnnotatedField main() {
        return mainContentsField;
    }

    @Override
    public Iterator<AnnotatedField> iterator() {
        Iterator<AnnotatedFieldImpl> it = complexFields.values().iterator();
        return new Iterator<AnnotatedField>() {
            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public AnnotatedField next() {
                return it.next();
            }
        };
    }

    @Override
    public Stream<AnnotatedField> stream() {
        return complexFields.values().stream().map(f -> (AnnotatedField)f);
    }

    @Override
    public AnnotatedField get(String fieldName) {
        if (!complexFields.containsKey(fieldName))
            throw new IllegalArgumentException("Complex field '" + fieldName + "' not found!");
        return complexFields.get(fieldName);
    }

    @Override
    public boolean exists(String fieldName) {
        return complexFields.containsKey(fieldName);
    }

    public void freeze() {
        complexFields.values().forEach(f -> f.freeze());
    }

    public void put(String fieldName, AnnotatedFieldImpl fieldDesc) {
        complexFields.put(fieldName, fieldDesc);
    }

    public void setMainContentsField(AnnotatedFieldImpl mainContentsField) {
        this.mainContentsField = mainContentsField;
    }
}