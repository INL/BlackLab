package nl.inl.blacklab.searches;

/**
 * An interface to serialize debug cache information
 */
public interface CacheInfoDataStream {
    public  CacheInfoDataStream startMap();

    public  CacheInfoDataStream endMap();

    public  CacheInfoDataStream startEntry(String key);

    public  CacheInfoDataStream endEntry();
}
