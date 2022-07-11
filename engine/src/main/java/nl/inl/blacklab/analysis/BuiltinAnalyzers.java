package nl.inl.blacklab.analysis;

import org.apache.lucene.analysis.Analyzer;

/**
 * Instances of our builtin analyzers.
 */
public enum BuiltinAnalyzers {
    WHITESPACE(new BLWhitespaceAnalyzer()),
    DUTCH(new BLDutchAnalyzer()),
    STANDARD(new BLStandardAnalyzer()),
    NONTOKENIZING(new BLNonTokenizingAnalyzer()),
    UNKNOWN(null);

    /**
     * Gets analyzer based on an analyzer alias.
     *
     * @param analyzerName type of analyzer
     *         (default|whitespace|standard|nontokenizing)
     * @return the analyzer, or null if the name wasn't recognized
     */
    public static BuiltinAnalyzers fromString(String analyzerName) {
        analyzerName = analyzerName.toLowerCase();
        if (analyzerName.equals("whitespace")) {
            return WHITESPACE;
        } else if (analyzerName.matches("default|dutch")) {
            return DUTCH;
        } else if (analyzerName.equals("standard")) {
            return STANDARD;
        } else if (analyzerName.matches("(non|un)tokeniz(ing|ed)")) {
            return NONTOKENIZING;
        }
        return UNKNOWN;
    }

    private final Analyzer analyzer;

    BuiltinAnalyzers(Analyzer analyzer) {
        this.analyzer = analyzer;
    }

    public Analyzer getAnalyzer() {
        return analyzer;
    }
}
