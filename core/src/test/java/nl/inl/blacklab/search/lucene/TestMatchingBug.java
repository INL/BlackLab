package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.spans.SpanWeight.Postings;
import org.apache.lucene.search.spans.Spans;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import nl.inl.blacklab.TestIndexBug;
import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.queryParser.corpusql.CorpusQueryLanguageParser;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.lucene.SpanQueryPositionFilter.Operation;
import nl.inl.blacklab.search.lucene.SpansSequenceWithGap.Gap;
import nl.inl.blacklab.search.results.Hit;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.textpattern.TextPattern;

public class TestMatchingBug {
    
    TestIndexBug testIndex;
    
    BlackLabIndex index;
    
    @Before
    public void setUp() {
        testIndex = new TestIndexBug();
        index = testIndex.index();
    }
    
    @Test
    public void testFine() throws InvalidQuery {
        TextPattern tp = CorpusQueryLanguageParser.parse("[word='between'] [ner='org']+ [ner!='org']+ [word='and'] [ner='org']+ [ner!='org']");
        Hits hits = index.find(QueryInfo.create(index), tp, null, null);
        Assert.assertEquals(19, hits.get(0).start());
        Assert.assertEquals(2, hits.size());
    }
    
    @Test
    public void testBug() throws InvalidQuery {
        TextPattern tp = CorpusQueryLanguageParser.parse("[word='between'] [ner='org']+ [ner!='org']+ [word='and'] party:([ner='org']+) [ner!='org']");
        Hits hits = index.find(QueryInfo.create(index), tp, null, null);
        Assert.assertEquals(19, hits.get(0).start());
        Assert.assertEquals(2, hits.size());
    }

    @Ignore
    @Test
    public void testBugDetails() throws InvalidQuery, IOException {
        TextPattern tp = CorpusQueryLanguageParser.parse("[word='between'] [ner='org']+ [ner!='org']+ [word='and'] party:([ner='org']+) [ner!='org']");
        QueryInfo queryInfo = QueryInfo.create(index);
        BLSpanQuery spanQuery = index.createSpanQuery(queryInfo, tp, null);
        IndexReader indexReader = index.reader();
        BLSpanQuery optimize = spanQuery.optimize(indexReader);
        BLSpanQuery rewritten = optimize.rewrite(indexReader);
        BLSpanQuery sortedUnique = BLSpanQuery.ensureSortedUnique(rewritten);
        BLSpanWeight weight = sortedUnique.createWeight(index.searcher(), false);
        List<LeafReaderContext> atomicReaderContexts = indexReader.leaves();
        List<Hit> hits = new ArrayList<>();
        for (LeafReaderContext context: atomicReaderContexts) {
            BLSpans spans = (BLSpans) weight.getSpans(context, Postings.OFFSETS);
            int doc = -1;
            while (true) {
                doc = spans.nextDoc();
                if (doc == Spans.NO_MORE_DOCS)
                    break;
                while (true) {
                    int start = spans.nextStartPosition();
                    if (start == Spans.NO_MORE_POSITIONS)
                        break;
                    int end = spans.endPosition();
                    hits.add(Hit.create(doc, start, end));
                }
            }
        }
        Assert.assertEquals(19, hits.get(0).start());
        Assert.assertEquals(52, hits.get(0).end());
        Assert.assertEquals(2, hits.size());
        Assert.assertTrue(hits.get(1).start() > 52);
    }

    private BLSpans termSpans(LeafReaderContext ctx, String fieldName, String term) throws IOException {
        return (new BLSpanTermQuery(new Term(fieldName, term))).createWeight(index.searcher(), false).getSpans(ctx, Postings.OFFSETS);
    }

    private BLSpans spansLeft() throws IOException {
        List<LeafReaderContext> atomicReaderContexts = index.reader().leaves();
        LeafReaderContext ctx = atomicReaderContexts.get(0);
        SpansNGrams ngrams = new SpansNGrams(ctx.reader(), "contents", 1, BLSpans.MAX_UNLIMITED);
        SpansInBuckets filter = new SpansInBucketsPerDocument(termSpans(ctx, "contents%ner@i", "org"));
        BLSpans posFilter = new SpansPositionFilter(ngrams, filter, true, Operation.CONTAINING, true, 0, 0);
        BLSpans between = termSpans(ctx, "contents%word@i", "between");
        BLSpans org = new SpansRepetition(termSpans(ctx, "contents%ner@i", "org"), 1, BLSpans.MAX_UNLIMITED);
        return new SpansSequenceWithGap(sortEnd(new SpansSequenceWithGap(between, Gap.NONE, org)), Gap.NONE, posFilter);
    }

    private BLSpans spansRight() throws IOException {
        List<LeafReaderContext> atomicReaderContexts = index.reader().leaves();
        LeafReaderContext ctx = atomicReaderContexts.get(0);
        BLSpans notSpans = new SpansNot(ctx.reader(), "contents", termSpans(ctx, "contents%ner@i", "org"));
        BLSpans and = termSpans(ctx, "contents%word@i", "and");
        BLSpans org = new SpansRepetition(termSpans(ctx, "contents%ner@i", "org"), 1, BLSpans.MAX_UNLIMITED);
        BLSpans seq = new SpansSequenceWithGap(sortEnd(new SpansSequenceWithGap(and, Gap.NONE, org)), Gap.NONE, notSpans);
        return new SpansCaptureGroup(seq, "party", 1, -1);
    }
    
    private BLSpans spansFull() throws IOException {
        return PerDocumentSortedSpans.get(new SpansSequenceWithGap(sortEnd(spansLeft()), Gap.NONE, sortStart(spansRight())), true, true);
    }
    
    private BLSpans sortEnd(BLSpans clause) throws IOException {
        return PerDocumentSortedSpans.get(clause, false, false);
    }
    
    private BLSpans sortStart(BLSpans clause) throws IOException {
        return PerDocumentSortedSpans.get(clause, true, false);
    }
    
    @Ignore
    @Test
    public void testBugPart1() throws InvalidQuery, IOException {
        BLSpans spans = spansLeft();
        List<Hit> hits = new ArrayList<>();
        int doc = -1;
        while (true) {
            doc = spans.nextDoc();
            if (doc == Spans.NO_MORE_DOCS)
                break;
            while (true) {
                int start = spans.nextStartPosition();
                if (start == Spans.NO_MORE_POSITIONS)
                    break;
                int end = spans.endPosition();
                hits.add(Hit.create(doc, start, end));
            }
        }
        for (int i = 24; i <= 48; i++) {
            Assert.assertEquals(19, hits.get(i - 24).start());
            Assert.assertEquals(i, hits.get(i - 24).end());
        }
    }
    
    @Ignore
    @Test
    public void testBugPart2() throws InvalidQuery, IOException {
        BLSpans spans = spansRight();
        List<Hit> hits = new ArrayList<>();
        int doc = -1;
        while (true) {
            doc = spans.nextDoc();
            if (doc == Spans.NO_MORE_DOCS)
                break;
            while (true) {
                int start = spans.nextStartPosition();
                if (start == Spans.NO_MORE_POSITIONS)
                    break;
                int end = spans.endPosition();
                hits.add(Hit.create(doc, start, end));
            }
        }
        Assert.assertEquals(48, hits.get(0).start());
        Assert.assertEquals(52, hits.get(0).end());
        Assert.assertTrue(hits.get(1).start() > 52);
    }
    
    @Ignore
    @Test
    public void testBugBothParts() throws InvalidQuery, IOException {
        BLSpans spans = spansFull();
        System.err.println(spans);
        List<Hit> hits = new ArrayList<>();
        int doc = -1;
        while (true) {
            doc = spans.nextDoc();
            if (doc == Spans.NO_MORE_DOCS)
                break;
            while (true) {
                int start = spans.nextStartPosition();
                if (start == Spans.NO_MORE_POSITIONS)
                    break;
                int end = spans.endPosition();
                hits.add(Hit.create(doc, start, end));
            }
        }
        Assert.assertEquals(19, hits.get(0).start());
        Assert.assertEquals(52, hits.get(0).end());
        Assert.assertEquals(2, hits.size());
        Assert.assertTrue(hits.get(1).start() > 52);
    }
}
