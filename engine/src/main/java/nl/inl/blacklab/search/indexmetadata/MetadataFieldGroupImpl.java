package nl.inl.blacklab.search.indexmetadata;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A named, ordered list of metadata fields.
 *
 * Used to divide metadata into logical groups.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class MetadataFieldGroupImpl implements MetadataFieldGroup {

    static final Logger logger = LogManager.getLogger(MetadataFieldGroupImpl.class);

    @XmlTransient
    private MetadataFields metadataFields;

    private final String name;

    private final List<String> fieldNamesInGroup;

    @XmlTransient
    private List<MetadataField> fieldsInGroup = null;

    boolean addRemainingFields;

    MetadataFieldGroupImpl(MetadataFields metadataFields, String name, List<String> fieldNames, boolean addRemainingFields) {
        this.metadataFields = metadataFields;
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
                if (metadataFields.exists(fieldName))
                    fieldsInGroup.add(metadataFields.get(fieldName));
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

    public void fixAfterDeserialization(MetadataFields metadataFields) {
        this.metadataFields = metadataFields;
        for (String fieldName: fieldNamesInGroup) {
            fieldsInGroup.add(metadataFields.get(fieldName));
        }
    }
}
