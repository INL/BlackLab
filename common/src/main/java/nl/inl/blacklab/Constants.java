package nl.inl.blacklab;

/**
 * Constant values used in various places throughout the project.
 */
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

    /** Used as a default value if no name has been specified (legacy indexers only) */
    public static final String DEFAULT_MAIN_ANNOT_NAME = "word";

    /** Key in Solr response that contains the BlackLab response
        (also used by the proxy to retrieve the BlackLab response from the Solr response) */
    public static final String SOLR_BLACKLAB_SECTION_NAME = "blacklab";
}
