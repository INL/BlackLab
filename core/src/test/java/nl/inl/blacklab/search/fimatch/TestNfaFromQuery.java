package nl.inl.blacklab.search.fimatch;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.index.Term;
import org.junit.Assert;
import org.junit.Test;

import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.BLSpanTermQuery;
import nl.inl.blacklab.search.lucene.SpanQueryRepetition;
import nl.inl.blacklab.search.lucene.SpanQuerySequence;

public class TestNfaFromQuery {

	static class MockTokenPropMapper extends TokenPropMapper {

		private int[] termIds;

		private final Map<String, Integer> terms = new HashMap<>();

		public MockTokenPropMapper(String[] document) {
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
		public int getPropertyNumber(String propertyName) {
			if (propertyName.equals("word"))
				return 0;
			throw new IllegalArgumentException("Unknown property " + propertyName);
		}

		@Override
		public int getTermNumber(int propertyNumber, String propertyValue) {
			if (propertyNumber != 0)
				throw new IllegalArgumentException("Unknown property " + propertyNumber);
			Integer termId = terms.get(propertyValue);
			if (termId == null)
				throw new IllegalArgumentException("Unknown word " + propertyValue);
			return termId;
		}

		@Override
		public TokenSource tokenSource(int fiid, int startingPosition, int direction) {
			if (fiid != 0)
				throw new IllegalArgumentException("Unknown document " + fiid);
			return new IntArrayTokenSource(termIds, startingPosition, direction);
		}

	}

	static class IntArrayTokenSource extends TokenSource {

		private int[] input;

		IntArrayTokenSource(int[] input, int pos, int dir) {
			super(pos, dir);
			this.input = input;
		}

		@Override
		public int getToken(int propIndex, int pos) {
			if (!validPos(pos))
				return -1;
			return input[startingPosition + pos * direction];
		}

		@Override
		public boolean validPos(int pos) {
			int p = startingPosition + pos * direction;
			return p >= 0 && p < input.length;
		}
	}

	private static final List<Integer> NO_MATCHES = Collections.emptyList();

	private static void test(BLSpanQuery q, TokenPropMapper propMapper, int startPos, int direction, int tests, List<Integer> matches) {
		// The NFA
		NfaFragment frag = q.getNfa(propMapper);
		frag.append(new NfaFragment(NfaState.match(), null)); // finish NFA
		NfaState start = frag.getStartingState();

		// Forward matching
		TokenSource tokenSource = propMapper.tokenSource(0, startPos, direction);
		for (int i = 0; i < tests; i++) {
			Assert.assertEquals(matches.contains(i), start.matches(tokenSource, i));
		}
	}

	private static SpanQueryRepetition rep(BLSpanTermQuery clause, int min, int max) {
		return new SpanQueryRepetition(clause, min, max);
	}

	private static BLSpanTermQuery term(String w) {
		return new BLSpanTermQuery(new Term("contents%word", w));
	}

	private static SpanQuerySequence seq(BLSpanQuery... clauses) {
		return new SpanQuerySequence(clauses);
	}

	@Test
	public void testNfaSingleWord() {
		// The test document
		TokenPropMapper propMapper = new MockTokenPropMapper(new String[] {"This", "is", "a", "test"});

		// The query
		BLSpanQuery q = term("test");

		test(q, propMapper, 0,  1, 5, Arrays.asList(3));
		test(q, propMapper, 3, -1, 5, Arrays.asList(0));
	}

	@Test
	public void testNfaSequence() {
		// The test document
		TokenPropMapper propMapper = new MockTokenPropMapper(new String[] {"This", "is", "a", "test"});

		// The query
		BLSpanQuery q = seq(term("a"), term("test"));

		test(q, propMapper, 0,  1, 5, Arrays.asList(2));
		test(q, propMapper, 3, -1, 5, NO_MATCHES);
	}

	@Test
	public void testNfaRep1() {
		// The test document
		TokenPropMapper propMapper = new MockTokenPropMapper(new String[] {"This", "is", "very", "very", "very", "fun"});

		// The query
		BLSpanQuery q = rep(term("very"), 1, -1);

		test(q, propMapper, 0,  1, 6, Arrays.asList(2, 3, 4));
		test(q, propMapper, 5, -1, 6, Arrays.asList(1, 2, 3));
	}

	@Test
	public void testNfaRep2() {
		// The test document
		TokenPropMapper propMapper = new MockTokenPropMapper(new String[] {"This", "is", "very", "very", "very", "fun"});

		// The query
		BLSpanQuery q = seq(term("is"), rep(term("very"), 1, 2));

		test(q, propMapper, 0,  1, 6, Arrays.asList(1));
		test(q, propMapper, 5, -1, 6, NO_MATCHES);
	}
}
