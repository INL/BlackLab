package nl.inl.blacklab.interfaces.test;

import nl.inl.blacklab.interfaces.results.Group;
import nl.inl.blacklab.interfaces.results.ResultProperty;
import nl.inl.blacklab.interfaces.results.ResultPropertyValue;

/**
 * Property that looks at the group identity
 * 
 * @param <T> group type
 */
public final class GroupPropertyIdentity<T> implements ResultProperty<Group<T>> {

    public static <T> GroupPropertyIdentity<T> get() {
        return new GroupPropertyIdentity<T>();
    }

    private GroupPropertyIdentity() {
    }

    @Override
    public ResultPropertyValue get(Group<T> result) {
        return ResultPropertyValueInt.get(result.size());
    }

    @Override
    public int compare(Group<T> a, Group<T> b) {
        return (isReverse() ? -1 : 1) * a.identity().compareTo(b.identity());
    }

    @Override
    public boolean isReverse() {
        return true;
    }

    @Override
    public String serialize() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String name() {
        return "groupsize";
    }
}
