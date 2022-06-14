package nl.inl.blacklab.resultproperty;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.util.PropertySerializeUtil;
import nl.inl.util.ASVUtil;

/** Property value that represents a BlackLab document */
public class PropertyValueDoc extends PropertyValue {

    private final int docId;

    @Override
    public Integer value() {
        return docId;
    }

    @Override
    public String getApproximateSortValue() {
        return ASVUtil.encode(docId);
    }

    /*public Document luceneDoc() {
        return index.luceneDoc(docId);
    }*/

    public PropertyValueDoc(BlackLabIndex index, int id) {
        this.docId = id;
    }

    @Override
    public int compareTo(Object o) {
        return docId - ((PropertyValueDoc) o).docId;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(docId);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (obj instanceof PropertyValueDoc) {
            return docId == ((PropertyValueDoc) obj).docId;
        }
        return false;
    }

    public static PropertyValue deserialize(BlackLabIndex index, String info) {
        int id;
        try {
            id = Integer.parseInt(info);
        } catch (NumberFormatException e) {
            logger.warn("PropertyValueDoc.deserialize(): '" + info + "' is not a valid integer.");
            id = -1;
        }
        return new PropertyValueDoc(index, id);
    }

    @Override
    public String toString() {
        return Integer.toString(docId);
    }

    @Override
    public String serialize() {
        return PropertySerializeUtil.combineParts("doc", Integer.toString(docId));
    }
}
