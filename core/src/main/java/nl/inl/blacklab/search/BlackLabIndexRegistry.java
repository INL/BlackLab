package nl.inl.blacklab.search;

import java.util.IdentityHashMap;
import java.util.Map;

import org.apache.lucene.index.IndexReader;

public class BlackLabIndexRegistry {
    
    final private static Map<IndexReader, BlackLabIndex> searcherFromIndexReader = new IdentityHashMap<>();

    public static BlackLabIndex fromIndexReader(IndexReader reader) {
        return searcherFromIndexReader.get(reader);
    }

    public static void registerSearcher(IndexReader reader, BlackLabIndex index) {
        searcherFromIndexReader.put(reader, index);
    }

    public static void removeSearcher(BlackLabIndex index) {
        searcherFromIndexReader.remove(index.reader());
    }


}
