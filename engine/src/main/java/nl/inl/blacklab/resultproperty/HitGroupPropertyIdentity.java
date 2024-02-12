package nl.inl.blacklab.resultproperty;

import nl.inl.blacklab.search.results.HitGroup;

public class HitGroupPropertyIdentity extends HitGroupProperty {

    public static final String ID = "identity";

    HitGroupPropertyIdentity(HitGroupPropertyIdentity prop, boolean invert) {
        super(prop, invert);
    }
    
    public HitGroupPropertyIdentity() {
        super();
    }
    
    @Override
    public PropertyValue get(HitGroup result) {
        return result.identity();
    }

    @Override
    public int compare(HitGroup a, HitGroup b) {
        if (reverse)
            return b.identity().compareTo(a.identity());
        return a.identity().compareTo(b.identity());
    }

    @Override
    public String serialize() {
        return serializeReverse() + ID;
    }

    @Override
    public HitGroupPropertyIdentity reverse() {
        return new HitGroupPropertyIdentity(this, true);
    }

    @Override
    public String name() {
        return "group: identity";
    }
}
