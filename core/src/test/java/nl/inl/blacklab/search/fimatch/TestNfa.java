package nl.inl.blacklab.search.fimatch;

import org.junit.Assert;
import org.junit.Test;

public class TestNfa {

	class StringTokenSource extends TokenSource {

		private String input;

		StringTokenSource(String input, int pos, int dir) {
			super(pos, dir);
			this.input = input;
		}

		@Override
		public int getToken(int propIndex, int pos) {
			if (!validPos(pos))
				return -1;
			return input.charAt(startingPosition + pos * direction);
		}

		@Override
		public boolean validPos(int pos) {
			int p = startingPosition + pos * direction;
			return p >= 0 && p < input.length();
		}
	}

	@Test
	public void testNfaSimple() {
		// Test simple NFA matching ab|ba
		NfaState ab = NfaState.token(0, 'a', NfaState.token(0, 'b', NfaState.match()));
		NfaState ba = NfaState.token(0, 'b', NfaState.token(0, 'a', NfaState.match()));
		NfaState start = NfaState.or(ab, ba);

		// Forward matching
		StringTokenSource tokenSource = new StringTokenSource("abatoir", 0, 1);
		Assert.assertTrue(start.matches(tokenSource, 0));
		Assert.assertTrue(start.matches(tokenSource, 1));
		Assert.assertFalse(start.matches(tokenSource, 2));
		Assert.assertFalse(start.matches(tokenSource, 6));

		// Backward matching
		tokenSource = new StringTokenSource("abatoir", 3, -1);
		Assert.assertFalse(start.matches(tokenSource, 0));
		Assert.assertTrue(start.matches(tokenSource, 1));
		Assert.assertTrue(start.matches(tokenSource, 2));
		Assert.assertFalse(start.matches(tokenSource, 3));
	}

	@Test
	public void testNfaRepetition() {
		// Test NFA matching ac*e
		NfaState c = NfaState.token(0, 'c', null);
		NfaState split = NfaState.or(c, NfaState.token(0, 'e', NfaState.match()));
		NfaState start = NfaState.token(0, 'a', split);
		c.setNextState(0, split); // loopback

		// Forward matching
		Assert.assertTrue(start.matches(new StringTokenSource("access", 0, 1), 0));
		Assert.assertTrue(start.matches(new StringTokenSource("aces", 0, 1), 0));
		Assert.assertTrue(start.matches(new StringTokenSource("aether", 0, 1), 0));
		Assert.assertFalse(start.matches(new StringTokenSource("acquire", 0, 1), 0));
		Assert.assertFalse(start.matches(new StringTokenSource("cesium", 0, 1), 0));
	}

}
