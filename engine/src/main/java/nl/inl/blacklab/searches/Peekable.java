package nl.inl.blacklab.searches;

public interface Peekable<T> {
    /**
     * Peek at the result.
     * @return null if not supported, a peek at the result otherwise
     */
    default T peek() { return null; }
}
