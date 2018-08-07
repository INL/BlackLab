package nl.inl.blacklab.search.fimatch;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import nl.inl.blacklab.search.BlackLabException;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.util.StringUtil;

/**
 * A regex, wildcard or prefix clause.
 */
public abstract class NfaStateMultiTermPattern extends NfaState {

    /** What annotation we're trying to match */
    protected String luceneField;

    /**
     * Index of the annotation we're trying to match. Only valid after
     * lookupPropertyNumber() called.
     */
    private int propertyNumber = -1;

    /** The pattern this state accepts. */
    protected String pattern;

    /** The next state if a matching token was found. */
    protected NfaState nextState;

    /** Match case-sensitively? */
    private boolean caseSensitive;

    /** Match diacritics-sensitively? */
    private boolean diacSensitive;

    public NfaStateMultiTermPattern(String luceneField, String pattern, NfaState nextState) {
        this.luceneField = luceneField;
        this.caseSensitive = AnnotatedFieldNameUtil.isCaseSensitive(luceneField);
        this.diacSensitive = AnnotatedFieldNameUtil.isDiacriticsSensitive(luceneField);
        this.pattern = pattern;
        this.nextState = nextState;
    }

    /**
     * Find all matches for this NFA in the token source.
     *
     * @param fiDoc where to read tokens from
     * @param pos current matching position
     * @param matchEnds where to collect the matches found, or null if we don't want
     *            to collect them
     * @return true if any (new) matches were found, false if not
     */
    @Override
    public boolean findMatchesInternal(ForwardIndexDocument fiDoc, int pos, int direction, Set<Integer> matchEnds) {
        // Token state. Check if it matches token from token source, and if so, continue.
        int actualToken = fiDoc.getToken(propertyNumber, pos);
        if (actualToken >= 0) {
            String tokenString = fiDoc.getTermString(propertyNumber, actualToken);
            if (matchesPattern(desensitize(tokenString))) {
                return nextState.findMatchesInternal(fiDoc, pos + direction, direction, matchEnds);
            }
        }
        return false;
    }

    private String desensitize(String tokenString) {
        if (!caseSensitive)
            tokenString = tokenString.toLowerCase();
        if (!diacSensitive)
            tokenString = StringUtil.stripAccents(tokenString);
        return tokenString;
    }

    abstract boolean matchesPattern(String tokenString);

    @Override
    void fillDangling(NfaState state) {
        if (nextState == null)
            nextState = state;
    }

    @Override
    NfaStateMultiTermPattern copyInternal(Collection<NfaState> dangling, Map<NfaState, NfaState> copiesMade) {
        NfaStateMultiTermPattern copy = copyNoNextState();
        copiesMade.put(this, copy);
        NfaState nextStateCopy = nextState == null ? null : nextState.copy(dangling, copiesMade);
        copy.nextState = nextStateCopy;
        if (nextState == null && dangling != null)
            dangling.add(copy);
        return copy;
    }

    /**
     * Copy this state without a next state. Used by copyInternal().
     * 
     * @return the copy
     */
    abstract NfaStateMultiTermPattern copyNoNextState();

    @Override
    public void setNextState(int i, NfaState state) {
        if (i != 0)
            throw new BlackLabException("Token state only has one next state");
        nextState = state;
    }

    @Override
    public boolean matchesEmptySequence(Set<NfaState> statesVisited) {
        return false;
    }

    @Override
    public boolean hitsAllSameLength(Set<NfaState> statesVisited) {
        return true;
    }

    @Override
    public int hitsLengthMin(Set<NfaState> statesVisited) {
        return 1;
    }

    @Override
    public int hitsLengthMax(Set<NfaState> statesVisited) {
        return 1;
    }

    @Override
    protected String dumpInternal(Map<NfaState, Integer> stateNrs) {
        String name = getPatternType();
        return name + "(" + pattern + "," + dump(nextState, stateNrs) + ")";
    }

    /**
     * Returns the pattern type, REGEX, WILDCARD or PREFIX, for use by toString().
     * 
     * @return pattern type
     */
    abstract String getPatternType();

    @Override
    public void lookupPropertyNumbersInternal(ForwardIndexAccessor fiAccessor, Map<NfaState, Boolean> statesVisited) {
        String[] comp = AnnotatedFieldNameUtil.getNameComponents(luceneField);
        String propertyName = comp[1];
        propertyNumber = fiAccessor.getAnnotationNumber(propertyName);
        if (nextState != null)
            nextState.lookupPropertyNumbers(fiAccessor, statesVisited);
    }

    @Override
    protected void finishInternal(Set<NfaState> visited) {
        if (nextState == null)
            nextState = match();
        else
            nextState.finish(visited);
    }

}
