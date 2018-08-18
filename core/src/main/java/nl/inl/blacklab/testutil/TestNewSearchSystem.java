package nl.inl.blacklab.testutil;

import java.io.File;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.TermQuery;

import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.HitPropertyHitText;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.Kwic;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.Kwics;
import nl.inl.blacklab.searches.SearchCacheDebug;

public class TestNewSearchSystem {
    
    public static void main(String[] args) throws ErrorOpeningIndex, InvalidQuery {
        File indexDir = new File(args[0]);
        System.out.println("Opening index " + indexDir + "...");
        try (BlackLabIndex index = BlackLabIndex.open(indexDir)) {
            
            index.setCache(new SearchCacheDebug());
            
            System.out.println("\nFirst 20 hits for 'schip':");
            Hits hits = index.search()
                    .find("[lemma=\"schip\"]", null, index.maxSettings())
                    .window(0, 20)
                    .execute();
            Kwics kwics = hits.kwics(null);
            Annotation word = hits.field().annotation("word");
            hits.forEach(hit -> {
                Kwic kwic = kwics.get(hit);
                String left = StringUtils.join(kwic.left(word), " ");
                String match = StringUtils.join(kwic.match(word), " ");
                String right = StringUtils.join(kwic.right(word), " ");
                System.out.println("- " + left + " [" + match + "] " + right);
            });

            System.out.println("\nFirst 10 document results for 'schip':");
            MetadataField titleField = index.metadata().metadataFields().special("title");
            index.search()
                    .find("[lemma=\"schip\"]", null, index.maxSettings())
                    .docs(3)
                    .window(0, 10)
                    .execute()
                    .forEach(doc -> {
                        Document document = doc.identity().luceneDoc();
                        String title = document.get(titleField.name());
                        System.out.println("- " + title + " (" + doc + ")"); 
                    });
            System.out.flush();

            System.out.println("\nFirst 10 document results for title:test :");
            index.search()
                    .find(new TermQuery(new Term("title", "test")))
                    .window(0, 10)
                    .execute()
                    .forEach(doc -> {
                        Document document = doc.identity().luceneDoc();
                        String title = document.get(titleField.name());
                        System.out.println("- " + title + " (" + doc + ")"); 
                    });
            System.out.flush();

            System.out.println("\nFirst 10 document results for title:test :");
            index.search()
                    .find(new TermQuery(new Term("title", "test")))
                    .window(0, 10)
                    .execute()
                    .forEach(doc -> {
                        Document document = doc.identity().luceneDoc();
                        String title = document.get(titleField.name());
                        System.out.println("- " + title + " (" + doc + ")"); 
                    });
            System.out.flush();

            System.out.println("\nCount number of hits for 'schip': " + index
                    .search()
                    .find("[lemma=\"schip\"]", null, index.maxSettings())
                    .count()
                    .execute()
                    .value());

            System.out.println("\nCount different spellings for 'schip': ");
            index
                    .search()
                    .find("[lemma=\"schip\"]", null, index.maxSettings())
                    .group(new HitPropertyHitText(index), 3)
                    .execute()
                    .forEach(group -> {
                        System.out.println("- " + group.identity() + " (stored " + group.numberStored() + " of " + group.size() + ") ");
                    });

            System.out.println("\nCollocations for 'waterval': ");
            TermFrequencyList colls = index.search()
                    .find("[lemma=\"waterval\"]", null, index.maxSettings())
                    .collocations(null, null, null)
                    .execute();
            for (int i = 0; i < 10; i++) {
                System.out.println("- " + colls.get(i));
            }
        }
    }

}
