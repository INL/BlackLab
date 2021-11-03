package nl.inl.blacklab.resultproperty;

public class PropertyValueInt extends PropertyValue {
    long value;

    @Override
    public Long value() {
        return value;
    }

    public PropertyValueInt(long value) {
        this.value = value;
    }

    @Override
    public int compareTo(Object o) {
        long ovalue = ((PropertyValueInt) o).value;
        return value == ovalue ? 0 : (value > ovalue ? 1 : -1);
    }

    @Override
    public int hashCode() {
        return Long.hashCode(value);
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
        return Long.toString(value);
    }

    @Override
    public String serialize() {
        return PropertySerializeUtil.combineParts("int", Long.toString(value));
    }
}
