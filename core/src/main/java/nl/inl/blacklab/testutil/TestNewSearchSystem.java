package nl.inl.blacklab.testutil;

import java.io.File;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.TermQuery;

import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.HitPropertyHitText;
import nl.inl.blacklab.search.BlackLabIndex;

public class TestNewSearchSystem {

    public static void main(String[] args) throws ErrorOpeningIndex, InvalidQuery {
        File indexDir = new File(args[0]);
        try (BlackLabIndex index = BlackLabIndex.open(indexDir)) {

            System.out.println("First 20 hits for 'schip':");
            index.search()
                    .find("[lemma=\"schip\"]", null, index.maxSettings())
                    .window(0, 20)
                    .execute()
                    .forEach(hit -> System.out.println(hit));

            System.out.println("First 10 document results for 'schip':");
            index.search()
                    .find("[lemma=\"schip\"]", null, index.maxSettings())
                    .docs(3)
                    //.window(0, 10)
                    .execute()
                    .forEach(doc -> System.out.println(doc));

            System.out.println("First 10 document results for title:test :");
            index.search()
                    .find(new TermQuery(new Term("title", "test")))
                    .execute()
                    .forEach(doc -> System.out.println(doc));

            System.out.println("Count number of hits for 'schip': " + index
                    .search()
                    .find("[lemma=\"schip\"]", null, index.maxSettings())
                    .count()
                    .execute()
                    .value());

            System.out.println("Count different spellings for 'schip': " + index
                    .search()
                    .find("[lemma=\"schip\"]", null, index.maxSettings())
                    .group(new HitPropertyHitText(index), -1)
                    .count()
                    .execute()
                    .value());

            System.out.println("Collocations for 'schip': ");
            index.search()
                    .find("[lemma=\"schip\"]", null, index.maxSettings())
                    .collocations(null, null, null)
                    .execute()
                    .forEach(c -> System.out.println(c));
        }
    }

}
