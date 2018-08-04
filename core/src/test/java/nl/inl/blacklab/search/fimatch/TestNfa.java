package nl.inl.blacklab.search.fimatch;

import java.util.Arrays;
import java.util.HashSet;
import java.util.IdentityHashMap;

import org.apache.lucene.index.LeafReader;
import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.junit.Assert;
import org.junit.Test;

public class TestNfa {

    final class MockFiAccessor extends ForwardIndexAccessor {
        @Override
        public int numberOfProperties() {
            return 1;
        }

        @Override
        public void getTermNumbers(MutableIntSet results, int propertyNumber, String propertyValue,
                boolean caseSensitive,
                boolean diacSensitive) {
            if (propertyNumber != 0)
                throw new RuntimeException("only 0 is valid property");
            if (propertyValue.length() > 1)
                throw new RuntimeException("only words of length 1 are valid");
            results.add(propertyValue.charAt(0));
        }

        @Override
        public int getPropertyNumber(String propertyName) {
            if (!propertyName.equals("word"))
                throw new RuntimeException("only 'word' is valid property");
            return 0;
        }

        @Override
        public ForwardIndexAccessorLeafReader getForwardIndexAccessorLeafReader(LeafReader reader) {
            return null;
        }

        @Override
        public String getTermString(int propIndex, int termId) {
            if (propIndex != 0)
                throw new RuntimeException("only 0 is valid property");
            return Character.toString((char) termId);
        }

        @Override
        public boolean termsEqual(int propIndex, int[] termId, boolean caseSensitive, boolean diacSensitive) {
            if (propIndex != 0)
                throw new RuntimeException("only 0 is valid property");
            for (int i = 1; i < termId.length; i++) {
                if (termId[i] != termId[0])
                    return false;
            }
            return true;
        }
    }

    class ForwardIndexDocumentString extends ForwardIndexDocument {

        private String input;

        ForwardIndexDocumentString(String input) {
            this.input = input;
        }

        @Override
        public int getToken(int propIndex, int pos) {
            if (propIndex != 0)
                throw new RuntimeException("only 0 is valid property");
            if (!validPos(pos))
                return -1;
            return input.charAt(pos);
        }

        @Override
        public boolean validPos(int pos) {
            return pos >= 0 && pos < input.length();
        }

        @Override
        public String getTermString(int propIndex, int termId) {
            if (propIndex != 0)
                throw new RuntimeException("only 0 is valid property");
            return Character.toString((char) termId);
        }

        @Override
        public boolean termsEqual(int propIndex, int[] termId, boolean caseSensitive, boolean diacSensitive) {
            if (propIndex != 0)
                throw new RuntimeException("only 0 is valid property");
            for (int i = 1; i < termId.length; i++) {
                if (termId[i] != termId[0])
                    return false;
            }
            return true;
        }
    }

    @Test
    public void testNfaSimple() {
        // Test simple NFA matching ab|ba
        NfaState ab = NfaState.token("contents%word@i", "a", NfaState.token("contents%word@i", "b", null));
        NfaState ba = NfaState.token("contents%word@i", "b", NfaState.token("contents%word@i", "a", null));
        NfaState start = NfaState.or(false, Arrays.asList(ab, ba), true);
        start.finish(new HashSet<NfaState>());
        start.lookupPropertyNumbers(new MockFiAccessor(), new IdentityHashMap<NfaState, Boolean>());

        ForwardIndexDocumentString fiDoc = new ForwardIndexDocumentString("abatoir");
        Assert.assertTrue(start.matches(fiDoc, 0, 1));
        Assert.assertTrue(start.matches(fiDoc, 1, 1));
        Assert.assertFalse(start.matches(fiDoc, 2, 1));
        Assert.assertFalse(start.matches(fiDoc, 6, 1));
    }

    @Test
    public void testNfaRepetition() {
        // Test NFA matching ac*e
        NfaState c = NfaState.token("contents%word@i", "c", null);
        NfaState split = NfaState.or(true, Arrays.asList(c, NfaState.token("contents%word@i", "e", null)), false);
        NfaState start = NfaState.token("contents%word@i", "a", split);
        c.setNextState(0, split); // loopback
        start.finish(new HashSet<NfaState>());
        start.lookupPropertyNumbers(new MockFiAccessor(), new IdentityHashMap<NfaState, Boolean>());

        // Forward matching
        Assert.assertTrue(start.matches(new ForwardIndexDocumentString("access"), 0, 1));
        Assert.assertTrue(start.matches(new ForwardIndexDocumentString("aces"), 0, 1));
        Assert.assertTrue(start.matches(new ForwardIndexDocumentString("aether"), 0, 1));
        Assert.assertFalse(start.matches(new ForwardIndexDocumentString("acquire"), 0, 1));
        Assert.assertFalse(start.matches(new ForwardIndexDocumentString("cesium"), 0, 1));

        // Backward matching
        Assert.assertTrue(start.matches(new ForwardIndexDocumentString("ideaal"), 3, -1));
    }

}
