package nl.inl.blacklab;

public class Constants {

    /**
     * Safe maximum size for a Java array.
     *
     * This is JVM-dependent, but the consensus seems to be that
     * this is a safe limit. See e.g.
     * https://stackoverflow.com/questions/3038392/do-java-arrays-have-a-maximum-size
     */
    public static final int JAVA_MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;
    /**
     * Safe maximum size for a Java HashMap.
     *
     * This is JVM dependent, but the consensus seems to be that
     * this is a safe limit. See e.g.
     * https://stackoverflow.com/questions/25609840/java-hashmap-max-size-of-5770/25610054
     */
    public static final int JAVA_MAX_HASHMAP_SIZE = Integer.MAX_VALUE / 4;
}
