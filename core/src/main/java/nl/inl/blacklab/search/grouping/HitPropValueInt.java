package nl.inl.blacklab.search.grouping;

import java.util.Arrays;
import java.util.List;

public class HitPropValueInt extends HitPropValue {
    int value;

    public int getValue() {
        return value;
    }

    public HitPropValueInt(int value) {
        this.value = value;
    }

    @Override
    public int compareTo(Object o) {
        return value - ((HitPropValueInt) o).value;
    }

    @Override
    public int hashCode() {
        return ((Integer) value).hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (obj instanceof HitPropValueInt) {
            return value == ((HitPropValueInt) obj).value;
        }
        return false;
    }

    public static HitPropValue deserialize(String info) {
        int v;
        try {
            v = Integer.parseInt(info);
        } catch (NumberFormatException e) {
            v = 0;
        }
        return new HitPropValueInt(v);
    }

    @Override
    public String toString() {
        return Integer.toString(value);
    }

    @Override
    public String serialize() {
        return PropValSerializeUtil.combineParts("int", Integer.toString(value));
    }

    @Override
    public List<String> getPropValues() {
        return Arrays.asList(Integer.toString(value));
    }
}
