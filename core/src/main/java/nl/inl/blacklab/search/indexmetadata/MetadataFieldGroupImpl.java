package nl.inl.blacklab.search.indexmetadata;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import nl.inl.blacklab.search.indexmetadata.nint.MetadataField;
import nl.inl.blacklab.search.indexmetadata.nint.MetadataFieldGroup;
import nl.inl.blacklab.search.indexmetadata.nint.MetadataFields;

/**
 * A named, ordered list of metadata fields.
 * 
 * Used to divide metadata into logical groups.
 */
public class MetadataFieldGroupImpl implements MetadataFieldGroup {

    private MetadataFields metadataFieldsAccessor;

    private String name;

    private List<String> fieldNamesInGroup;

    boolean addRemainingFields = false;

    MetadataFieldGroupImpl(MetadataFields metadataFieldsAccessor, String name, List<String> fieldNames, boolean addRemainingFields) {
        this.metadataFieldsAccessor = metadataFieldsAccessor;
        this.name = name;
        this.fieldNamesInGroup = new ArrayList<>(fieldNames);
        this.addRemainingFields = addRemainingFields;
    }

    @Override
    public String name() {
        return name;
    }

    public List<String> getFields() {
        return fieldNamesInGroup;
    }

    @Override
    public boolean addRemainingFields() {
        return addRemainingFields;
    }

    @Override
    public Iterator<MetadataField> iterator() {
        Iterator<String> it = fieldNamesInGroup.iterator();
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
        return fieldNamesInGroup.stream().map(name -> metadataFieldsAccessor.get(name));
    }

}