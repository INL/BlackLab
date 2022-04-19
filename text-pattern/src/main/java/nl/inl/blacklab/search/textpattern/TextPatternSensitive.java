package nl.inl.blacklab.search.textpattern;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.BLSpanQuery;

/**
 * TextPattern for wrapping another TextPattern so that it applies to a certain
 * word annotation.
 *
 * For example, to find lemmas starting with "bla": <code>
 * TextPattern tp = new TextPatternProperty("lemma", new TextPatternWildcard("bla*"));
 * </code>
 */
public class TextPatternSensitive extends TextPattern {

    private final TextPattern input;

    private final MatchSensitivity sensitivity;

    /**
     * Indicate that we want to use a different list of alternatives for this part
     * of the query.
     * 
     * @param sensitivity search case-/diacritics-sensitively?
     * @param input
     */
    public TextPatternSensitive(MatchSensitivity sensitivity, TextPattern input) {
        this.sensitivity = sensitivity;
        this.input = input;
    }

    @Override
    public BLSpanQuery translate(QueryExecutionContext context) throws InvalidQuery {
        return input.translate(context.withSensitive(sensitivity));
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TextPatternSensitive) {
            TextPatternSensitive tp = ((TextPatternSensitive) obj);
            return sensitivity == tp.sensitivity &&
                    input.equals(tp.input);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return sensitivity.hashCode() + input.hashCode();
    }

    @Override
    public String toString() {
        String sett = sensitivity.toString();
        return "SENSITIVE(" + sett + ", " + input.toString() + ")";
    }
}
