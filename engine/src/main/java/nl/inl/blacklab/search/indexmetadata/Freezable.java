package nl.inl.blacklab.search.indexmetadata;

/**
 * An object that can be frozen.
 * 
 * A frozen object may not be modified.
 * 
 * @param <T> class we're freezing (will be returned by freeze())
 */
public interface Freezable<T> {
    
    T freeze();
    
    boolean isFrozen();
    
    default void ensureNotFrozen() {
        if (isFrozen())
            throw new UnsupportedOperationException("Tried to modify a frozen object");
    }
}
