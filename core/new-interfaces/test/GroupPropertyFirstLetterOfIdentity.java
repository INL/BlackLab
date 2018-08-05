package nl.inl.blacklab.interfaces.test;

import nl.inl.blacklab.interfaces.results.Group;
import nl.inl.blacklab.interfaces.results.ResultProperty;
import nl.inl.blacklab.interfaces.results.ResultPropertyValue;

/**
 * Property that looks at first letter of group identity
 * 
 * @param <T> group type
 */
public final class GroupPropertyFirstLetterOfIdentity<T> implements ResultProperty<Group<T>> {

    public static <T> GroupPropertyFirstLetterOfIdentity<T> get() {
        return new GroupPropertyFirstLetterOfIdentity<T>();
    }

    private char getFirstLetter(Group<T> result) {
        return result.identity().toString().charAt(0);
    }

    @Override
    public ResultPropertyValue get(Group<T> result) {
        return ResultPropertyValueString.get(Character.toString(getFirstLetter(result)));
    }

    @Override
    public int compare(Group<T> a, Group<T> b) {
        return Character.compare(getFirstLetter(a), getFirstLetter(b));
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
        return "firstletter";
    }
}
