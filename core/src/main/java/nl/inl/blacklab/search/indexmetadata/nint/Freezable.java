package nl.inl.blacklab.search.indexmetadata.nint;

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
}
