package nl.inl.blacklab.queryParser.contextql;

import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.WildcardQuery;

import nl.inl.blacklab.search.CompleteQuery;
import nl.inl.blacklab.search.Searcher;
import nl.inl.blacklab.search.TextPattern;
import nl.inl.blacklab.search.TextPatternProperty;
import nl.inl.blacklab.search.TextPatternSequence;
import nl.inl.blacklab.search.TextPatternWildcard;

public class ContextQlParseUtils {

	private ContextQlParseUtils() {
	}

	public static CompleteQuery clause(Searcher searcher, String index, String relation, String term, String defaultProperty) {
        if (relation == null)
            relation = "=";
        if (index == null)
            index = defaultProperty;

        if (relation.equals("=")) {
            return contains(searcher, index, term);
        } else if (relation.equals("any")) {
        	throw new UnsupportedOperationException("any not yet supported");
        } else if (relation.equals("all")) {
        	throw new UnsupportedOperationException("all not yet supported");
        } else if (relation.equals("exact")) {
        	throw new UnsupportedOperationException("exact not supported");
        } else if (relation.equals("<") || relation.equals(">") || relation.equals("<=") || relation.equals(">=") || relation.equals("<>")) {
        	throw new UnsupportedOperationException("Only contains (=) relation is supported!");
        } else {
            throw new UnsupportedOperationException("Unknown relation operator: " + relation);
        }
    }

    public static CompleteQuery combineClauses(CompleteQuery a, String op, CompleteQuery b) {
        if (op.equalsIgnoreCase("and")) {
            return a.and(b);
        } else if (op.equalsIgnoreCase("or")) {
            return a.or(b);
        } else if (op.equalsIgnoreCase("not")) {
            return a.not(b);
        } else if (op.equalsIgnoreCase("prox")) {
            throw new UnsupportedOperationException("prox is not yet supported!");
        }
        throw new UnsupportedOperationException("Unrecognized operator " + op);
    }

	/**
	 * Return a clause meaning "field contains value".
	 *
	 * Depending on the field name, this will contain a contents query
	 * or a filter query.
	 *
	 * @param searcher our index
	 * @param field the field name. If it starts with "contents.", it is a contents
	 * query. "contents" by itself means "contents.word". "word", "lemma" and "pos" by
	 * themselves are interpreted as being prefixed with "contents."
	 * @param value the value, optionally with wildcards, to search for
	 * @return the query
	 */
	public static CompleteQuery contains(Searcher searcher, String field, String value) {

		boolean isContentsSearch = false;
		String prop = "word";
		boolean isProperty;
		if (searcher != null && !searcher.getClass().getSimpleName().startsWith("Mock")) // ARGH...
			isProperty = searcher.getIndexStructure().getMainContentsField().getProperties().contains(field);
		else
			isProperty = field.equals("word") || field.equals("lemma") || field.equals("pos"); // common case
		if (isProperty) {
			isContentsSearch = true;
			prop = field;
		} else if (field.equals("contents")) {
			isContentsSearch = true;
		} else if (field.startsWith("contents.")) {
			isContentsSearch = true;
			prop = field.substring(9);
		}

		String[] parts = value.trim().split("\\s+");
		TextPattern tp = null;
		Query q = null;
		if (parts.length == 1) {
			// Single term, possibly with wildcards
			if (isContentsSearch)
				tp = new TextPatternWildcard(value.trim());
			else
				q = new WildcardQuery(new Term(field, value));
		} else {
			// Phrase query
			if (isContentsSearch) {
				List<TextPattern> clauses = new ArrayList<>();
				for (int i = 0; i < parts.length; i++) {
					clauses.add(new TextPatternWildcard(parts[i]));
				}
				tp = new TextPatternSequence(clauses.toArray(new TextPattern[0]));
			}
			else {
				PhraseQuery.Builder pb = new PhraseQuery.Builder();
				for (int i = 0; i < parts.length; i++) {
					pb.add(new Term(field, parts[i]));
				}
				q = pb.build();
			}
		}

		if (isContentsSearch)
			return new CompleteQuery(new TextPatternProperty(prop, tp), null);
		return new CompleteQuery(null, q);
	}


}
