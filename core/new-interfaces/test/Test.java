package nl.inl.blacklab.interfaces.test;

import java.util.HashMap;
import java.util.Map;

import nl.inl.blacklab.interfaces.MatchSensitivity;
import nl.inl.blacklab.interfaces.index.BlackLabIndex;
import nl.inl.blacklab.interfaces.results.Group;
import nl.inl.blacklab.interfaces.results.Hit;
import nl.inl.blacklab.interfaces.results.HitGroup;
import nl.inl.blacklab.interfaces.results.HitGroupGroups;
import nl.inl.blacklab.interfaces.results.SearchCache;
import nl.inl.blacklab.interfaces.results.SearchResult;
import nl.inl.blacklab.interfaces.search.SearchOperation;
import nl.inl.blacklab.interfaces.search.Search;
import nl.inl.blacklab.interfaces.struct.AnnotatedField;
import nl.inl.blacklab.interfaces.struct.Annotation;
import nl.inl.blacklab.interfaces.struct.IndexMetadata;
import nl.inl.blacklab.queryParser.corpusql.ParseException;
import nl.inl.blacklab.search.Kwic;

public class Test {

    /**
     * Our little cache implementation.
     * 
     * Never removes anything, I'm sure that will work out fine.
     */
    private static class MyCache implements SearchCache, SearchOperation {
        
        private Map<Search, SearchResult> cached = new HashMap<>();
        
        @Override
        public SearchResult get(Search operation) {
            return cached.get(operation);
        }

        @Override
        public SearchResult perform(Search operation, SearchResult result) {
            SearchResult saved = result.save();
            cached.put(operation, saved);
            return saved;
        }
    }

    @SuppressWarnings("null")
    public static void main(String[] args) throws ParseException {
        
        // Make a very simple cache
        final MyCache cache = new MyCache();
        
        // Open index, and make sure it will use our cache
        BlackLabIndex index = null; // = BlackLabIndex.open(dir, cache);
        
        // Find contents field
        IndexMetadata metadata = index.structure();
        AnnotatedField fieldContents = metadata.annotated().field("contents");
        
        // Execute a search 
        String cqlQuery = "[lemma=\"test\"]";
        HitGroupGroups groups = index
                .find(cqlQuery, fieldContents)       // find hits
                .custom(cache)                       // add to cache
                .groupByDocument(3)                  // group by document, collect 3 hits per doc
                .groupBy(DocPropertyAuthor.get(), 3) // group by author, collect 3 docs per author
                .sortBy(GroupPropertySize.get())     // sort by author
                .window(0, 20)                       // get 20 largest groups
                .execute();
        
        // Iterate over results
        System.out.println("Grouped document results:");
        SimpleContext context = SimpleContext.get(10);
        for (Group<HitGroup> group: groups) {
            System.out.println("Author: " + group.identity());
            for (HitGroup document: group.members()) {
                System.out.println("  Document: " + document.identity());
                for (Hit hit: document.members()) {
                    Kwic kwic = index.kwic(hit, fieldContents, context);
                    System.out.println("    " + kwic.toString());
                }
            }
            System.out.println("");
        }
        
        // Sample some hits
        System.out.println("Sampled hits:");
        index   .find(cqlQuery, fieldContents)       // find hits
                .sample(SamplePercentage.get(10))    // sample 10% of hits   
                .execute()
                .forEach(hit -> {
                    Kwic kwic = index.kwic(hit, fieldContents, context); // 10 words around hit
                    System.out.println("- " + kwic.toString());
                });
        
        // Collocations
        System.out.println("Term frequencies:");
        MatchSensitivity sensitive = MatchSensitivity.SENSITIVE;
        Annotation annotLemma = fieldContents.annotation("lemma");
        index   .find(cqlQuery, fieldContents, null)          // find hits
                .collocations(annotLemma, context, sensitive) // make lemma-collocations with a context of 10
                .execute()
                .forEach(tf -> System.out.println("  " + tf.term() + " (" + tf.frequency()));
        
        // Count hits
        System.out.println("Counted hits:" + index.find(cqlQuery, fieldContents).count().execute().number().total());
    }
}
