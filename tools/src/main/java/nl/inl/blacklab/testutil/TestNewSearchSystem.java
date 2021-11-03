package nl.inl.blacklab.testutil;

import java.io.File;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.TermQuery;

import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.queryParser.corpusql.CorpusQueryLanguageParser;
import nl.inl.blacklab.resultproperty.HitPropertyHitText;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.Kwic;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.results.ContextSize;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.Kwics;
import nl.inl.blacklab.search.results.QueryInfo;

public class TestNewSearchSystem {

    public static void main(String[] args) throws ErrorOpeningIndex, InvalidQuery {
        if (args.length == 0) {
            System.err.println("Please specify index directory.");
            System.exit(1);
        }
        File indexDir = new File(args[0]);
        System.out.println("Opening index " + indexDir + "...");
        try (BlackLabIndex index = BlackLab.open(indexDir)) {

            FutureSearchResultCache cache = new FutureSearchResultCache();
            cache.setTrace(true);
            index.setCache(cache); //new SearchCacheDebug());

            String cqlLemmaSchip = "[lemma=\"schip\"]";
            Annotation annotLemma = index.mainAnnotatedField().annotation("lemma");
            MetadataField titleField = index.metadata().metadataFields().special("title");

            System.out.println("\nFirst 20 hits for 'schip':");
            Hits hits = index.search().find(CorpusQueryLanguageParser.parse(cqlLemmaSchip).toQuery(QueryInfo.create(index)), index.searchSettings())
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
            index.search().find(CorpusQueryLanguageParser.parse(cqlLemmaSchip).toQuery(QueryInfo.create(index)), index.searchSettings())
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
                    .findDocuments(new TermQuery(new Term("title", "test")))
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
                    .findDocuments(new TermQuery(new Term("title", "test")))
                    .window(0, 10)
                    .execute()
                    .forEach(doc -> {
                        Document document = doc.identity().luceneDoc();
                        String title = document.get(titleField.name());
                        System.out.println("- " + title + " (" + doc + ")");
                    });
            System.out.flush();

            BLSpanQuery q = CorpusQueryLanguageParser.parse(cqlLemmaSchip).toQuery(QueryInfo.create(index));
            System.out.println("\nCount number of hits for 'schip': " +
                    index.search().find(q, index.searchSettings())
                    .count()
                    .execute()
                    .processedTotal());

            System.out.println("\nCount different spellings for 'schip': ");
            q = CorpusQueryLanguageParser.parse(cqlLemmaSchip).toQuery(QueryInfo.create(index));
            index.search().find(q, index.searchSettings())
                    .group(new HitPropertyHitText(index), 3)
                    .execute()
                    .forEach(group -> {
                        System.out.println("- " + group.identity() + " (stored " + group.numberOfStoredResults() + " of " + group.size() + ") ");
                    });

            System.out.println("\nCollocations for 'waterval': ");
            q = CorpusQueryLanguageParser.parse("[lemma=\"waterval\"]").toQuery(QueryInfo.create(index));
            TermFrequencyList colls = index.search().find(q, index.searchSettings())
                    .collocations(annotLemma, ContextSize.get(10), MatchSensitivity.INSENSITIVE)
                    //.window(0, 10)
                    .execute();
            for (int i = 0; i < 10 && i < colls.size(); i++) {
                System.out.println("- " + colls.get(i));
            }
        }
    }

}
