package nl.inl.blacklab.search.indexmetadata;

/**
 * An object that can be frozen.
 * 
 * A frozen object may not be modified.
 */
public interface Freezable {
    
    void freeze();
    
    boolean isFrozen();
    
    default void ensureNotFrozen() {
        if (isFrozen())
            throw new UnsupportedOperationException("Tried to modify a frozen object");
    }

    default void ensureFrozen() {
        if (!isFrozen())
            throw new UnsupportedOperationException("Tried to read a non-frozen object");
    }
}
