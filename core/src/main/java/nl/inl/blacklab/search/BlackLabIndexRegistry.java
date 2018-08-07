package nl.inl.blacklab.search;

import java.util.IdentityHashMap;
import java.util.Map;

import org.apache.lucene.index.IndexReader;

public class BlackLabIndexRegistry {
    
    final private static Map<IndexReader, BlackLabIndex> searcherFromIndexReader = new IdentityHashMap<>();

    public static BlackLabIndex fromIndexReader(IndexReader reader) {
        return searcherFromIndexReader.get(reader);
    }

    public static void registerSearcher(IndexReader reader, BlackLabIndex searcher) {
        searcherFromIndexReader.put(reader, searcher);
    }

    public static void removeSearcher(BlackLabIndex searcher) {
        searcherFromIndexReader.remove(searcher.reader());
    }


}
