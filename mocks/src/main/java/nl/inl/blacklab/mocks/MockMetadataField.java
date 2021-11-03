package nl.inl.blacklab.mocks;

import java.util.List;
import java.util.Map;

import nl.inl.blacklab.search.indexmetadata.FieldType;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.indexmetadata.UnknownCondition;
import nl.inl.blacklab.search.indexmetadata.ValueListComplete;

public final class MockMetadataField implements MetadataField {
    
    private String name;

    public MockMetadataField(String name) {
        this.name = name;
    }
    
    @Override
    public String name() {
        return name;
    }

    @Override
    public String displayName() {
        return name + "_displayName";
    }

    @Override
    public String description() {
        return name + "_desc";
    }

    @Override
    public boolean hasContentStore() {
        return false;
    }

    @Override
    public String offsetsField() {
        return null;
    }

    @Override
    public String uiType() {
        return null;
    }

    @Override
    public FieldType type() {
        return null;
    }

    @Override
    public List<String> displayOrder() {
        return null;
    }

    @Override
    public String analyzerName() {
        return null;
    }

    @Override
    public String unknownValue() {
        return null;
    }

    @Override
    public UnknownCondition unknownCondition() {
        return null;
    }

    @Override
    public Map<String, Integer> valueDistribution() {
        return null;
    }

    @Override
    public ValueListComplete isValueListComplete() {
        return null;
    }

    @Override
    public Map<String, String> displayValues() {
        return null;
    }

    @Override
    public String group() {
        return null;
    }
}