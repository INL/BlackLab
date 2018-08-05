package nl.inl.blacklab.search.indexmetadata;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import nl.inl.blacklab.search.indexmetadata.nint.MetadataField;
import nl.inl.blacklab.search.indexmetadata.nint.MetadataFields;
import nl.inl.blacklab.search.indexmetadata.nint.MetadataFields.MetadataFieldGroup;

/**
 * A named, ordered list of metadata fields.
 * 
 * Used to divide metadata into logical groups.
 */
public class MetadataFieldGroupImpl implements MetadataFieldGroup {

    private MetadataFields metadataFieldsAccessor;

    String name;

    List<String> fieldsInGroup;

    boolean addRemainingFields = false;

    public void setName(String name) {
        this.name = name;
    }

    public void setFields(List<String> fields) {
        this.fieldsInGroup = fields;
    }

    public MetadataFieldGroupImpl(MetadataFields metadataFieldsAccessor, String name, List<String> fields) {
        this.metadataFieldsAccessor = metadataFieldsAccessor;
        this.name = name;
        this.fieldsInGroup = new ArrayList<>(fields);
    }

    public String name() {
        return name;
    }

    public List<String> getFields() {
        return fieldsInGroup;
    }

    public boolean addRemainingFields() {
        return addRemainingFields;
    }

    public void setAddRemainingFields(boolean addRemainingFields) {
        this.addRemainingFields = addRemainingFields;
    }

    @Override
    public Iterator<MetadataField> iterator() {
        Iterator<String> it = fieldsInGroup.iterator();
        return new Iterator<MetadataField>() {
            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public MetadataField next() {
                return metadataFieldsAccessor.get(it.next());
            }
        };
    }

    @Override
    public Stream<MetadataField> stream() {
        return fieldsInGroup.stream().map(name -> metadataFieldsAccessor.get(name));
    }

}