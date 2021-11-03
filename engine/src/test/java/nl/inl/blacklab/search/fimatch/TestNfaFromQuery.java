package nl.inl.blacklab.search.fimatch;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.Term;
import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.junit.Assert;
import org.junit.Test;

import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.BLSpanOrQuery;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.BLSpanTermQuery;
import nl.inl.blacklab.search.lucene.SpanQueryAnd;
import nl.inl.blacklab.search.lucene.SpanQueryAnyToken;
import nl.inl.blacklab.search.lucene.SpanQueryExpansion;
import nl.inl.blacklab.search.lucene.SpanQueryExpansion.Direction;
import nl.inl.blacklab.search.lucene.SpanQueryNot;
import nl.inl.blacklab.search.lucene.SpanQueryRepetition;
import nl.inl.blacklab.search.lucene.SpanQuerySequence;

public class TestNfaFromQuery {

    static class MockForwardIndexAccessor extends ForwardIndexAccessor {

        int[] termIds;

        private final Map<String, Integer> terms = new HashMap<>();

        public MockForwardIndexAccessor(String... document) {
            this.termIds = new int[document.length];
            for (int i = 0; i < document.length; i++) {
                Integer termId = terms.get(document[i]);
                if (termId == null) {
                    termId = terms.size();
                    terms.put(document[i], termId);
                }
                termIds[i] = termId;
            }
        }

        @Override
        public int getAnnotationNumber(String annotationName) {
            if (annotationName.equals("word"))
                return 0;
            throw new IllegalArgumentException("Unknown annotation " + annotationName);
        }

        @Override
        public int getAnnotationNumber(Annotation annotation) {
            return getAnnotationNumber(annotation.name());
        }

        @Override
        public void getTermNumbers(MutableIntSet results, int annotNumber, String annotValue,
                MatchSensitivity sensitivity) {
            if (annotNumber != 0)
                throw new IllegalArgumentException("Unknown annotation " + annotNumber);
            if (sensitivity.isCaseSensitive()) {
                results.add(terms.get(annotValue));
                return;
            }
            for (Entry<String, Integer> e : terms.entrySet()) {
                if (e.getKey().equalsIgnoreCase(annotValue)) {
                    results.add(e.getValue());
                }
            }
        }

        @Override
        public int numberOfAnnotations() {
            return 1;
        }

        @Override
        public ForwardIndexAccessorLeafReader getForwardIndexAccessorLeafReader(LeafReader reader) {
            return new ForwardIndexAccessorLeafReader(reader) {

                @Override
                public ForwardIndexDocument getForwardIndexDoc(int docId) {
                    if (docId != 0)
                        throw new IllegalArgumentException("Unknown document " + docId);
                    return new ForwardIndexDocumentIntArray(termIds);
                }

                @Override
                public int getDocLength(int docId) {
                    if (docId != 0)
                        throw new IllegalArgumentException("Unknown document " + docId);
                    return termIds.length;
                }

                @Override
                public int[] getChunk(int annotIndex, int docId, int start, int end) {
                    if (annotIndex != 0)
                        throw new IllegalArgumentException("Unknown annotation " + annotIndex);
                    if (docId != 0)
                        throw new IllegalArgumentException("Unknown document " + docId);
                    return Arrays.copyOfRange(termIds, start, end);
                }

                @Override
                public int getFiid(int annotIndex, int docId) {
                    return 0;
                }

            };
        }

        @Override
        public String getTermString(int annotIndex, int termId) {
            for (Entry<String, Integer> e : terms.entrySet()) {
                if (e.getValue() == termId) {
                    return e.getKey();
                }
            }
            return null;
        }

        @Override
        public boolean termsEqual(int annotIndex, int[] termId, MatchSensitivity sensitivity) {
            throw new UnsupportedOperationException();
        }

    }

    static class ForwardIndexDocumentIntArray extends ForwardIndexDocument {

        private int[] input;

        ForwardIndexDocumentIntArray(int[] input) {
            this.input = input;
        }

        @Override
        public int getToken(int annotIndex, int pos) {
            if (!validPos(pos))
                return -1;
            return input[pos];
        }

        @Override
        public boolean validPos(int pos) {
            return pos >= 0 && pos < input.length;
        }

        @Override
        public String getTermString(int annotIndex, int termId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean termsEqual(int annotIndex, int[] termId, MatchSensitivity sensitivity) {
            throw new UnsupportedOperationException();
        }
    }

    private static final List<Integer> NO_MATCHES = Collections.emptyList();

    private static void test(BLSpanQuery q, ForwardIndexAccessor fiAccessor, int startPos, int direction, int tests,
            List<Integer> matches) {
        // The NFA
        Nfa frag = q.getNfa(fiAccessor, direction);
        frag.finish();
        frag.lookupAnnotationNumbers(fiAccessor, new IdentityHashMap<NfaState, Boolean>());
        //System.err.println(frag);
        NfaState start = frag.getStartingState(); //finish();

        ForwardIndexDocument fiDoc = fiAccessor.getForwardIndexAccessorLeafReader(null).getForwardIndexDoc(0);
        for (int i = 0; i < tests; i++) {
            Assert.assertEquals("Test " + i, matches.contains(i),
                    start.matches(fiDoc, startPos + direction * i, direction));
        }
    }

    private static SpanQueryRepetition rep(BLSpanQuery clause, int min, int max) {
        return new SpanQueryRepetition(clause, min, max);
    }

    private static BLSpanTermQuery term(String w) {
        return new BLSpanTermQuery(null, new Term("contents%word@i", w));
    }

    private static SpanQuerySequence seq(BLSpanQuery... clauses) {
        return new SpanQuerySequence(clauses);
    }

    private static BLSpanOrQuery or(BLSpanQuery... clauses) {
        return new BLSpanOrQuery(clauses);
    }

    private static SpanQueryAnd and(BLSpanQuery... clauses) {
        return new SpanQueryAnd(clauses);
    }

    private static SpanQueryAnyToken any(int min, int max) {
        return new SpanQueryAnyToken(null, min, max, "contents%word@i");
    }

    private static BLSpanQuery exp(BLSpanQuery clause, Direction direction, int min, int max) {
        return new SpanQueryExpansion(clause, direction, min, max);
    }

    private static SpanQueryNot not(BLSpanQuery clause) {
        return new SpanQueryNot(clause);
    }

    @Test
    public void testNfaSingleWord() {
        // The test document
        ForwardIndexAccessor fiAccessor = new MockForwardIndexAccessor("This", "is", "a", "test");

        // The query: "test"
        BLSpanQuery q = term("test");

        test(q, fiAccessor, 0, 1, 4, Arrays.asList(3));
        test(q, fiAccessor, 3, -1, 4, Arrays.asList(0));
    }

    @Test
    public void testNfaSequence() {
        // The test document
        ForwardIndexAccessor fiAccessor = new MockForwardIndexAccessor("This", "is", "a", "test");

        // The query: "a" "test"
        BLSpanQuery q = seq(term("a"), term("test"));

        test(q, fiAccessor, 0, 1, 4, Arrays.asList(2));
        test(q, fiAccessor, 3, -1, 4, Arrays.asList(0));
    }

    @Test
    public void testNfaRep1() {
        // The test document
        ForwardIndexAccessor fiAccessor = new MockForwardIndexAccessor("This", "is", "very", "very", "very", "fun");

        // The query: "very"+
        BLSpanQuery q = rep(term("very"), 1, BLSpanQuery.MAX_UNLIMITED);

        test(q, fiAccessor, 0, 1, 6, Arrays.asList(2, 3, 4));
        test(q, fiAccessor, 5, -1, 6, Arrays.asList(1, 2, 3));
    }

    @Test
    public void testNfaRep2() {
        // The test document
        ForwardIndexAccessor fiAccessor = new MockForwardIndexAccessor("This", "is", "very", "very", "very", "fun");

        // The query: "is" "very"{1,2}
        BLSpanQuery q = seq(term("is"), rep(term("very"), 1, 2));

        test(q, fiAccessor, 0, 1, 6, Arrays.asList(1));
        test(q, fiAccessor, 5, -1, 6, Arrays.asList(2, 3));
    }

    @Test
    public void testNfaOr() {
        // The test document
        ForwardIndexAccessor fiAccessor = new MockForwardIndexAccessor("This", "is", "very", "very", "very", "fun");

        // The query: "is" "very" | "very" "fun"
        BLSpanQuery q = or(seq(term("is"), term("very")), seq(term("very"), term("fun")));

        test(q, fiAccessor, 0, 1, 6, Arrays.asList(1, 4));
        test(q, fiAccessor, 5, -1, 6, Arrays.asList(0, 3));
    }

    @Test
    public void testNfaAny() {
        // The test document
        ForwardIndexAccessor fiAccessor = new MockForwardIndexAccessor("This", "is", "very", "very", "very", "fun");

        // The query: []{3,3}
        BLSpanQuery q = any(3, 3);

        test(q, fiAccessor, 0, 1, 6, Arrays.asList(0, 1, 2, 3));
        test(q, fiAccessor, 5, -1, 6, Arrays.asList(0, 1, 2, 3));
    }

    @Test
    public void testNfaAnd() {
        // The test document
        ForwardIndexAccessor fiAccessor = new MockForwardIndexAccessor("This", "is", "very", "very", "very", "fun");

        // The query: ("very" "very" []) & ([] "very" "very")
        BLSpanQuery q = and(seq(term("very"), term("very"), any(1, 1)), seq(any(1, 1), term("very"), term("very")));

        test(q, fiAccessor, 0, 1, 6, Arrays.asList(2));
        test(q, fiAccessor, 5, -1, 6, Arrays.asList(1));
    }

    @Test
    public void testNfaExpansionLeft() {
        // The test document
        ForwardIndexAccessor fiAccessor = new MockForwardIndexAccessor("This", "is", "very", "very", "very", "fun");

        // The query: []{1,2} very
        BLSpanQuery q = exp(term("very"), Direction.LEFT, 1, 2);

        test(q, fiAccessor, 0, 1, 6, Arrays.asList(0, 1, 2, 3));
        test(q, fiAccessor, 5, -1, 6, Arrays.asList(1, 2, 3));
    }

    @Test
    public void testNfaExpansionRight() {
        // The test document
        ForwardIndexAccessor fiAccessor = new MockForwardIndexAccessor("This", "is", "very", "very", "very", "fun");

        // The query: "is" []{0,3}
        BLSpanQuery q = exp(term("is"), Direction.RIGHT, 0, 3);

        test(q, fiAccessor, 0, 1, 6, Arrays.asList(1));
        test(q, fiAccessor, 5, -1, 6, Arrays.asList(1, 2, 3, 4));
    }

    @Test
    public void testNfaNot() {
        // The test document
        ForwardIndexAccessor fiAccessor = new MockForwardIndexAccessor("This", "is", "very", "very", "very", "fun");

        // The query: [word != "very"]
        BLSpanQuery q = not(term("very"));

        test(q, fiAccessor, 0, 1, 6, Arrays.asList(0, 1, 5));
        test(q, fiAccessor, 5, -1, 6, Arrays.asList(0, 4, 5));
    }

    @Test
    public void testNfaComplex0() {
        // The test document
        ForwardIndexAccessor fiAccessor = new MockForwardIndexAccessor("This", "is", "lots", "and", "lots", "and",
                "lots", "of", "fun");

        // The query: [word != "lots"] "of"
        BLSpanQuery q = seq(not(term("lots")), term("of"));

        test(q, fiAccessor, 0, 1, 9, NO_MATCHES);
        test(q, fiAccessor, 8, -1, 9, NO_MATCHES);
    }

    @Test
    public void testNfaComplex1() {
        // The test document
        ForwardIndexAccessor fiAccessor = new MockForwardIndexAccessor("This", "is", "lots", "and", "lots", "and",
                "lots", "of", "fun");

        // The query: []? "is" ("lots" "and"){1,3} [word != "lots"] "of"
        BLSpanQuery q = seq(exp(term("is"), Direction.LEFT, 0, 1), rep(seq(term("lots"), term("and")), 1, 3), not(term("lots")),
                term("of"));

        test(q, fiAccessor, 0, 1, 9, NO_MATCHES);
        test(q, fiAccessor, 8, -1, 9, NO_MATCHES);
    }

    @Test
    public void testNfaComplex2() {
        // The test document
        ForwardIndexAccessor fiAccessor = new MockForwardIndexAccessor("This", "is", "lots", "and", "lots", "and",
                "lots", "of", "fun");

        // The query: []? "is" ("lots" "and"){1,3} "lots" "of"
        BLSpanQuery q = seq(exp(term("is"), Direction.LEFT, 0, 1), rep(seq(term("lots"), term("and")), 1, 3), term("lots"),
                term("of"));

        test(q, fiAccessor, 0, 1, 9, Arrays.asList(0, 1));
        test(q, fiAccessor, 8, -1, 9, Arrays.asList(1));
    }

    @Test
    public void testNfaRepeatedNot() {
        // The test document
        ForwardIndexAccessor fiAccessor = new MockForwardIndexAccessor("This", "is", "lots", "and", "lots", "and",
                "lots", "of", "fun");

        // The query: "and" [word != "of"]{2}
        BLSpanQuery q = seq(term("and"), rep(not(term("of")), 2, 2));

        test(q, fiAccessor, 0, 1, 9, Arrays.asList(3));
        test(q, fiAccessor, 8, -1, 9, Arrays.asList(3));
    }
}
