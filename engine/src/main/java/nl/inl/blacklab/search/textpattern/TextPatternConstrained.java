package nl.inl.blacklab.search.textpattern;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryConstrained;
import nl.inl.blacklab.search.matchfilter.MatchFilter;

public class TextPatternConstrained extends TextPatternCombiner {

    MatchFilter constraint;

    public TextPatternConstrained(TextPattern clause, MatchFilter constraint) {
        super(clause);
        this.constraint = constraint;
    }

    @Override
    public BLSpanQuery translate(QueryExecutionContext context) throws InvalidQuery {
        BLSpanQuery translate = clauses.get(0).translate(context);
        ForwardIndexAccessor fiAccessor = ForwardIndexAccessor.fromIndex(context.index(),
                translate.getField());
        return new SpanQueryConstrained(translate, constraint, fiAccessor);
    }

    @Override
    public String toString() {
        String producer = clauses.get(0).toString();
        String filter = constraint.toString();
        return "CONSTRAINT(" + producer + ", " + filter + ")";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((constraint == null) ? 0 : constraint.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        TextPatternConstrained other = (TextPatternConstrained) obj;
        if (constraint == null) {
            if (other.constraint != null)
                return false;
        } else if (!constraint.equals(other.constraint))
            return false;
        return true;
    }

}
