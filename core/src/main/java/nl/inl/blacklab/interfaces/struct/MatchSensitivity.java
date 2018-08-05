package nl.inl.blacklab.interfaces.struct;

import nl.inl.blacklab.index.complex.ComplexFieldUtil;

/**
 * Desired match sensitivity.
 * 
 * (Previously called "alternative" when talking about Lucene field names,
 * and "case/diacritics-sensitivity" when talking about matching, but
 * those are the same thing)
 */
public final class MatchSensitivity {
    
    public static final MatchSensitivity SENSITIVE = new MatchSensitivity(true, true);
    
    public static final MatchSensitivity INSENSITIVE = new MatchSensitivity(false, false);
    
    public static final MatchSensitivity CASE_INSENSITIVE = new MatchSensitivity(false, true);
    
    public static final MatchSensitivity DIACRITICS_INSENSITIVE = new MatchSensitivity(true, false);
    
    public static MatchSensitivity get(boolean caseSensitive, boolean diacriticsSensitive) {
        if (caseSensitive)
            return diacriticsSensitive ? SENSITIVE : DIACRITICS_INSENSITIVE;
        else
            return diacriticsSensitive ? CASE_INSENSITIVE : INSENSITIVE;
    }
    
    private boolean caseSensitive;
    
    private boolean diacriticsSensitive;
	
    private MatchSensitivity(boolean caseSensitive, boolean diacriticsSensitive) {
        super();
        this.caseSensitive = caseSensitive;
        this.diacriticsSensitive = diacriticsSensitive;
    }

    public boolean isCaseSensitive() {
        return caseSensitive;
    }
	
    public boolean isDiacriticsSensitive() {
        return diacriticsSensitive;
    }
    
	/** @return Suffix used for corresponding Lucene field */
	public String luceneFieldSuffix() {
	    if (caseSensitive)
	        return diacriticsSensitive ? ComplexFieldUtil.SENSITIVE_ALT_NAME : ComplexFieldUtil.DIACRITICS_INSENSITIVE_ALT_NAME;
	    else
	        return diacriticsSensitive ? ComplexFieldUtil.CASE_INSENSITIVE_ALT_NAME : ComplexFieldUtil.INSENSITIVE_ALT_NAME;
	}

}
