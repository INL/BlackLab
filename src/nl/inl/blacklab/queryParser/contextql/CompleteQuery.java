package nl.inl.blacklab.queryParser.contextql;

import java.util.ArrayList;
import java.util.List;

import nl.inl.blacklab.search.TextPattern;
import nl.inl.blacklab.search.TextPatternAnd;
import nl.inl.blacklab.search.TextPatternDocLevelAndNot;
import nl.inl.blacklab.search.TextPatternNot;
import nl.inl.blacklab.search.TextPatternOr;
import nl.inl.blacklab.search.TextPatternProperty;
import nl.inl.blacklab.search.TextPatternWildcard;
import nl.inl.blacklab.search.sequences.TextPatternSequence;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.WildcardQuery;

public class CompleteQuery {

	public TextPattern contentsQuery;

	public Query filterQuery;

	public CompleteQuery(TextPattern contentsQuery, Query filterQuery) {
		this.contentsQuery = contentsQuery;
		this.filterQuery = filterQuery;
	}

	public static CompleteQuery clause(String field, String value) {

		boolean isContentsSearch = false;
		String prop = "word";
		if (field.equals("word") || field.equals("lemma") || field.equals("pos")) { // hack for common case...
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
				List<TextPattern> clauses = new ArrayList<TextPattern>();
				for (int i = 0; i < parts.length; i++) {
					clauses.add(new TextPatternWildcard(parts[i]));
				}
				tp = new TextPatternSequence(clauses.toArray(new TextPattern[0]));
			}
			else {
				PhraseQuery pq = new PhraseQuery();
				for (int i = 0; i < parts.length; i++) {
					pq.add(new Term(field, parts[i]));
				}
				q = pq;
			}
		}

		if (isContentsSearch)
			return new CompleteQuery(new TextPatternProperty(prop, tp), null);
		return new CompleteQuery(null, q);
	}

	public CompleteQuery and(CompleteQuery other) {
		TextPattern a, b, c;
		Query d, e, f;
		BooleanQuery bq;

		a = contentsQuery;
		b = other.contentsQuery;
		if (a != null && b != null)
			c = new TextPatternAnd(a, b); // NOTE: token-level and!
		else
			c = a == null ? b : a;

		d = filterQuery;
		e = other.filterQuery;
		if (d != null && e != null) {
			bq = new BooleanQuery();
			bq.add(d, Occur.MUST);
			bq.add(e, Occur.MUST);
			f = bq;
		}
		else
			f = d == null ? e : d;

		return new CompleteQuery(c, f);
	}

	public CompleteQuery or(CompleteQuery other) {
		TextPattern a, b, c;
		Query d, e, f;
		BooleanQuery bq;

		a = contentsQuery;
		b = other.contentsQuery;
		d = filterQuery;
		e = other.filterQuery;

		if ( (a == null) != (b == null) ||
			 (d == null) != (e == null)) {
			throw new UnsupportedOperationException("or can only be used to combine contents clauses or metadata clauses; you can't combine the two with eachother with or");
		}

		if (a != null && b != null)
			c = new TextPatternOr(a, b);
		else
			c = a == null ? b : a;

		if (d != null && e != null) {
			bq = new BooleanQuery();
			bq.add(d, Occur.SHOULD);
			bq.add(e, Occur.SHOULD);
			f = bq;
		}
		else
			f = d == null ? e : d;

		return new CompleteQuery(c, f);
	}

	public CompleteQuery not(CompleteQuery other) {
		TextPattern a, b, c;
		Query d, e, f;
		BooleanQuery bq;

		a = contentsQuery;
		b = other.contentsQuery;
		if (a != null && b != null)
			c = new TextPatternDocLevelAndNot(a, b);
		else
			c = a == null ? new TextPatternNot(b) : a;

		d = filterQuery;
		e = other.filterQuery;
		if (d != null && e != null) {
			bq = new BooleanQuery();
			bq.add(d, Occur.MUST);
			bq.add(e, Occur.MUST_NOT);
			f = bq;
		}
		else {
			if (e != null && d == null)
				throw new UnsupportedOperationException("Cannot have not without positive clause first!");
			f = d;
		}

		return new CompleteQuery(c, f);
	}

	public CompleteQuery prox(CompleteQuery other) {
		throw new UnsupportedOperationException("prox not supported");
	}
}
