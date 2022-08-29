package nl.inl.blacklab.analysis;

import java.util.Objects;

import org.apache.lucene.util.AttributeImpl;
import org.apache.lucene.util.AttributeReflector;

public class IsPrimaryValueAttributeImpl extends AttributeImpl implements IsPrimaryValueAttribute, Cloneable {

    private boolean primary;

    public IsPrimaryValueAttributeImpl() {}

    public IsPrimaryValueAttributeImpl(boolean b) {
        this.primary = b;
    }

    @Override
    public boolean isPrimaryValue() {
        return primary;
    }

    @Override
    public void setPrimaryValue(boolean b) {
        this.primary = b;
    }

    @Override
    public void clear() {
        primary = false;
    }

    @Override
    public IsPrimaryValueAttributeImpl clone()  {
        IsPrimaryValueAttributeImpl clone = (IsPrimaryValueAttributeImpl) super.clone();
        clone.primary = primary;
        return clone;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof IsPrimaryValueAttributeImpl))
            return false;
        IsPrimaryValueAttributeImpl that = (IsPrimaryValueAttributeImpl) o;
        return primary == that.primary;
    }

    @Override
    public int hashCode() {
        return Objects.hash(primary);
    }

    @Override
    public void copyTo(AttributeImpl target) {
        IsPrimaryValueAttributeImpl t = (IsPrimaryValueAttributeImpl)target;
        t.primary = primary;
    }

    @Override
    public void reflectWith(AttributeReflector reflector) {
        reflector.reflect(IsPrimaryValueAttribute.class, "is-primary-value", primary);
    }

}
