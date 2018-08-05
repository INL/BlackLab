package nl.inl.blacklab.interfaces.test;

import nl.inl.blacklab.interfaces.index.Doc;
import nl.inl.blacklab.interfaces.results.ResultPropertyValue;

/** Property value that represents a BlackLab document. */
final class ResultPropertyValueBlDoc implements ResultPropertyValue {
    
    private Doc doc;
    
    public static ResultPropertyValueBlDoc get(Doc doc) {
        return new ResultPropertyValueBlDoc(doc);
    }
    
    private ResultPropertyValueBlDoc(Doc doc) {
        this.doc = doc;
    }

    @Override
    public int compareTo(ResultPropertyValue arg0) {
        if (arg0 instanceof ResultPropertyValueBlDoc) {
            return Integer.compare(doc.id(), ((ResultPropertyValueBlDoc) arg0).doc.id());
        }
        throw new IllegalArgumentException("Incompatible types");
    }

    @Override
    public String serialize() {
        throw new UnsupportedOperationException();
    }

    public Doc doc() {
        return doc;
    }
}