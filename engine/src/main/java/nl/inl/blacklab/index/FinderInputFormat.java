package nl.inl.blacklab.index;

/**
 * Can find input formats at runtime.
 */
public interface FinderInputFormat {

    /**
     * Find a format.
     *
     * Check isError() from the return value to make sure loading the format didn't fail.
     *
     * @return the format, or null if not found.
     */
    InputFormat find(String formatIdentifier);
}
