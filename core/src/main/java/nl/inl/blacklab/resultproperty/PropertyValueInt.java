package nl.inl.blacklab.resultproperty;

import java.util.Arrays;
import java.util.List;

public class PropertyValueInt extends PropertyValue {
    int value;

    @Override
    public Integer value() {
        return value;
    }

    public PropertyValueInt(int value) {
        this.value = value;
    }

    @Override
    public int compareTo(Object o) {
        return value - ((PropertyValueInt) o).value;
    }

    @Override
    public int hashCode() {
        return ((Integer) value).hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (obj instanceof PropertyValueInt) {
            return value == ((PropertyValueInt) obj).value;
        }
        return false;
    }

    public static PropertyValue deserialize(String info) {
        int v;
        try {
            v = Integer.parseInt(info);
        } catch (NumberFormatException e) {
            v = 0;
        }
        return new PropertyValueInt(v);
    }

    @Override
    public String toString() {
        return Integer.toString(value);
    }

    @Override
    public String serialize() {
        return PropertySerializeUtil.combineParts("int", Integer.toString(value));
    }

    @Override
    public List<String> propValues() {
        return Arrays.asList(Integer.toString(value));
    }
}
