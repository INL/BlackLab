package nl.inl.blacklab.search.indexmetadata;

/**
 * Desired match sensitivity.
 * 
 * (Previously called "alternative" when talking about Lucene field names,
 * and "case/diacritics-sensitivity" when talking about matching, but
 * those are the same thing)
 */
public enum MatchSensitivity {
    
    SENSITIVE(true, true, AnnotatedFieldNameUtil.SENSITIVE_ALT_NAME),
    INSENSITIVE(false, false, AnnotatedFieldNameUtil.INSENSITIVE_ALT_NAME),
    CASE_INSENSITIVE(false, true, AnnotatedFieldNameUtil.CASE_INSENSITIVE_ALT_NAME),
    DIACRITICS_INSENSITIVE(true, false, AnnotatedFieldNameUtil.DIACRITICS_INSENSITIVE_ALT_NAME);
    
    public static MatchSensitivity get(boolean caseSensitive, boolean diacriticsSensitive) {
        if (caseSensitive)
            return diacriticsSensitive ? SENSITIVE : DIACRITICS_INSENSITIVE;
        else
            return diacriticsSensitive ? CASE_INSENSITIVE : INSENSITIVE;
    }
    
    public static MatchSensitivity caseAndDiacriticsSensitive(boolean b) {
        return b ? SENSITIVE : INSENSITIVE;
    }

    public static MatchSensitivity fromLuceneFieldSuffix(String code) {
        switch(code) {
        case AnnotatedFieldNameUtil.SENSITIVE_ALT_NAME:
            return SENSITIVE;
        case AnnotatedFieldNameUtil.INSENSITIVE_ALT_NAME:
            return INSENSITIVE;
        case AnnotatedFieldNameUtil.CASE_INSENSITIVE_ALT_NAME:
            return CASE_INSENSITIVE;
        case AnnotatedFieldNameUtil.DIACRITICS_INSENSITIVE_ALT_NAME:
            return DIACRITICS_INSENSITIVE;
        }
        throw new IllegalArgumentException("Unknown sensitivity field code: " + code);
    }

    private boolean caseSensitive;
    
    private boolean diacriticsSensitive;
    
    private String luceneFieldCode;
	
    MatchSensitivity(boolean caseSensitive, boolean diacriticsSensitive, String luceneFieldCode) {
        this.caseSensitive = caseSensitive;
        this.diacriticsSensitive = diacriticsSensitive;
        this.luceneFieldCode = luceneFieldCode;
    }

    public boolean isCaseSensitive() {
        return caseSensitive;
    }
	
    public boolean isDiacriticsSensitive() {
        return diacriticsSensitive;
    }
    
	/** @return Suffix used for corresponding Lucene field */
	public String luceneFieldSuffix() {
	    return luceneFieldCode;
	}
	
	@Override
	public String toString() {
	    return luceneFieldSuffix();
	}

}
