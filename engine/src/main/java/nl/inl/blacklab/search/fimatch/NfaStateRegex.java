package nl.inl.blacklab.search.fimatch;

import java.util.regex.Pattern;

public class NfaStateRegex extends NfaStateMultiTermPattern {

    Pattern p;

    public NfaStateRegex(String luceneField, String pattern, NfaState nextState) {
        super(luceneField, pattern, nextState);
        p = Pattern.compile(pattern);
    }

    @Override
    boolean matchesPattern(String tokenString) {
        return p.matcher(tokenString).matches();
    }

    @Override
    NfaStateMultiTermPattern copyNoNextState() {
        return new NfaStateRegex(luceneField, pattern, null);
    }

    @Override
    String getPatternType() {
        return "REGEX";
    }

}
