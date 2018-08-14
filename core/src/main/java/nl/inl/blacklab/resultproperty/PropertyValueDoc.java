package nl.inl.blacklab.resultproperty;

import java.util.Arrays;
import java.util.List;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.Doc;

/** Property value that represents a BlackLab document */
public class PropertyValueDoc extends PropertyValue {
    Doc value;

    public Doc getValue() {
        return value;
    }

    public PropertyValueDoc(Doc doc) {
        this.value = doc;
    }

    @Override
    public int compareTo(Object o) {
        return value.id() - ((PropertyValueDoc) o).value.id();
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (obj instanceof PropertyValueDoc) {
            return value == ((PropertyValueDoc) obj).value;
        }
        return false;
    }

    public static PropertyValue deserialize(BlackLabIndex index, String info) {
        Doc v;
        try {
            v = index.doc(Integer.parseInt(info));
        } catch (NumberFormatException e) {
            v = null;
        }
        return new PropertyValueDoc(v);
    }

    @Override
    public String toString() {
        return Integer.toString(value.id());
    }

    @Override
    public String serialize() {
        return PropValSerializeUtil.combineParts("doc", Integer.toString(value.id()));
    }

    @Override
    public List<String> getPropValues() {
        return Arrays.asList(Integer.toString(value.id()));
    }
}
