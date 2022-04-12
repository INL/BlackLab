package nl.inl.blacklab.mocks;

import java.io.File;

import org.eclipse.collections.api.set.primitive.MutableIntSet;

import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

public class MockTerms extends Terms {

    String[] words;

    public MockTerms(String... words) {
        this.words = words;
    }

    @Override
    public int indexOf(String term) {
        for (int i = 0; i < numberOfTerms(); i++) {
            if (get(i).equals(term))
                return i;
        }
        throw new IllegalArgumentException("Unknown term '" + term + "'");
    }

    @Override
    public void indexOf(MutableIntSet results, String term, MatchSensitivity sensitivity) {
        for (int i = 0; i < numberOfTerms(); i++) {
            if (sensitivity.isCaseSensitive()) {
                if (get(i).equals(term))
                    results.add(i);
            } else {
                if (get(i).equalsIgnoreCase(term))
                    results.add(i);
            }
        }
    }

    @Override
    public void clear() {
        //

    }

    @Override
    public void write(File termsFile) {
        //

    }

    @Override
    public String get(int id) {
        return words[id];
    }

    @Override
    public int numberOfTerms() {
        return words.length;
    }

    @Override
    public int idToSortPosition(int id, MatchSensitivity sensitivity) {
        //
        return id;
    }

    @Override
    protected void setBlockBasedFile(boolean useBlockBasedTermsFile) {
        //

    }

    @Override
    public boolean termsEqual(int[] termId, MatchSensitivity sensitivity) {
        throw new UnsupportedOperationException();
    }

}
