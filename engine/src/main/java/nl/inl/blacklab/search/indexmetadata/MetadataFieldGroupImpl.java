package nl.inl.blacklab.search.indexmetadata;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A named, ordered list of metadata fields.
 *
 * Used to divide metadata into logical groups.
 */
public class MetadataFieldGroupImpl implements MetadataFieldGroup {

    static final Logger logger = LogManager.getLogger(MetadataFieldGroupImpl.class);

    private MetadataFields metadataFieldsAccessor;

    private String name;

    private List<String> fieldNamesInGroup;

    private List<MetadataField> fieldsInGroup = null;

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

    private synchronized void ensureFieldsFetched() {
        if (fieldsInGroup == null) {
            fieldsInGroup = new ArrayList<>();
            for (String fieldName: fieldNamesInGroup) {
                if (metadataFieldsAccessor.exists(fieldName))
                    fieldsInGroup.add(metadataFieldsAccessor.get(fieldName));
                else
                    logger.warn("Field '" + fieldName + "' in metadata group '" + name + "' does not exist!");
            }
        }
    }

    @Override
    public Iterator<MetadataField> iterator() {
        ensureFieldsFetched();
        return fieldsInGroup.iterator();
    }

    @Override
    public Stream<MetadataField> stream() {
        ensureFieldsFetched();
        return fieldsInGroup.stream();
    }

}