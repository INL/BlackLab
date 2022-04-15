package nl.inl.blacklab.server.util;

/**
 * Utilities to do with JVM memory.
 */
public class MemoryUtil {
    /** Handle to interface with the Java VM environment */
    private static Runtime runtime = Runtime.getRuntime();

    private MemoryUtil() {
    }

    /**
     * Returns the amount of memory that can still be allocated before we get the
     * OutOfMemory exception.
     *
     * @return the amount of memory that can still be allocated
     */
    public static long getFree() {
        return runtime.freeMemory() + (runtime.maxMemory() - runtime.totalMemory());
    }

}
