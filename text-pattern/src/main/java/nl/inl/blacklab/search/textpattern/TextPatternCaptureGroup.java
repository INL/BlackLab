package nl.inl.blacklab.search.textpattern;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryCaptureGroup;

/**
 * TextPattern for capturing a subclause as a named "group".
 */
public class TextPatternCaptureGroup extends TextPattern {

    private final TextPattern input;

    private final String groupName;

    /**
     * Indicate that we want to use a different list of alternatives for this part
     * of the query.
     * 
     * @param input the clause to tag with this name
     * @param groupName the tag name
     */
    public TextPatternCaptureGroup(TextPattern input, String groupName) {
        this.input = input;
        this.groupName = groupName;
    }

    @Override
    public BLSpanQuery translate(QueryExecutionContext context) throws InvalidQuery {
        return new SpanQueryCaptureGroup(input.translate(context), groupName, 0, 0);
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((groupName == null) ? 0 : groupName.hashCode());
        result = prime * result + ((input == null) ? 0 : input.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        TextPatternCaptureGroup other = (TextPatternCaptureGroup) obj;
        if (groupName == null) {
            if (other.groupName != null)
                return false;
        } else if (!groupName.equals(other.groupName))
            return false;
        if (input == null) {
            if (other.input != null)
                return false;
        } else if (!input.equals(other.input))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "CAPTURE(" + input.toString() + ", " + groupName + ")";
    }

}
