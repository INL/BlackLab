package nl.inl.blacklab.search.fimatch;

import org.junit.Assert;
import org.junit.Test;

public class TestNfa {

	class StringTokenSource extends TokenSource {

		private String input;

		StringTokenSource(String input) {
			this.input = input;
		}

		@Override
		public int getToken(int propIndex, int pos) {
			if (!validPos(pos))
				return -1;
			return input.charAt(pos);
		}

		public boolean validPos(int pos) {
			return pos >= 0 && pos < input.length();
		}
	}

	@Test
	public void testNfaSimple() {
		// Test simple NFA matching ab|ba
		NfaState ab = NfaState.token(0, 'a', NfaState.token(0, 'b', NfaState.match()));
		NfaState ba = NfaState.token(0, 'b', NfaState.token(0, 'a', NfaState.match()));
		NfaState start = NfaState.or(ab, ba);

		StringTokenSource tokenSource = new StringTokenSource("abatoir");
		Assert.assertTrue(start.matches(tokenSource, 0, 1));
		Assert.assertTrue(start.matches(tokenSource, 1, 1));
		Assert.assertFalse(start.matches(tokenSource, 2, 1));
		Assert.assertFalse(start.matches(tokenSource, 6, 1));
	}

	@Test
	public void testNfaRepetition() {
		// Test NFA matching ac*e
		NfaState c = NfaState.token(0, 'c', null);
		NfaState split = NfaState.or(c, NfaState.token(0, 'e', NfaState.match()));
		NfaState start = NfaState.token(0, 'a', split);
		c.setNextState(0, split); // loopback

		// Forward matching
		Assert.assertTrue(start.matches(new StringTokenSource("access"), 0, 1));
		Assert.assertTrue(start.matches(new StringTokenSource("aces"), 0, 1));
		Assert.assertTrue(start.matches(new StringTokenSource("aether"), 0, 1));
		Assert.assertFalse(start.matches(new StringTokenSource("acquire"), 0, 1));
		Assert.assertFalse(start.matches(new StringTokenSource("cesium"), 0, 1));

		// Backward matching
		Assert.assertTrue(start.matches(new StringTokenSource("ideaal"), 3, -1));
	}

}
