package nl.inl.blacklab.interfaces.test;

import nl.inl.blacklab.interfaces.results.Hit;
import nl.inl.blacklab.interfaces.results.ResultProperty;
import nl.inl.blacklab.interfaces.results.ResultPropertyValue;

/** Property that looks at the document a hit occurs in */
public final class HitPropertyBlDoc implements ResultProperty<Hit> {
    
    private static HitPropertyBlDoc instance = new HitPropertyBlDoc();
    
    public static HitPropertyBlDoc get() {
        return instance;
    }
    
    @Override
    public ResultPropertyValue get(Hit result) {
        return ResultPropertyValueBlDoc.get(result.doc());
    }
    
    @Override
    public int compare(Hit a, Hit b) {
        return (isReverse() ? -1 : 1) * Integer.compare(a.doc().id(), b.doc().id());
    }

    @Override
    public String serialize() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isReverse() {
        return false;
    }

    @Override
    public String name() {
        return "doc";
    }
}