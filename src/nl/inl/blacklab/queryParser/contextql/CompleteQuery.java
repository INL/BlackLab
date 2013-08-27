package nl.inl.blacklab.queryParser.contextql;

import nl.inl.blacklab.search.TextPattern;
import nl.inl.blacklab.search.TextPatternDocLevelAnd;
import nl.inl.blacklab.search.TextPatternDocLevelAndNot;
import nl.inl.blacklab.search.TextPatternNot;
import nl.inl.blacklab.search.TextPatternOr;
import nl.inl.blacklab.search.TextPatternTerm;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;

public class CompleteQuery {

	public TextPattern contentsQuery;

	public Query filterQuery;

	public CompleteQuery(TextPattern contentsQuery, Query filterQuery) {
		this.contentsQuery = contentsQuery;
		this.filterQuery = filterQuery;
	}

	public static CompleteQuery term(String field, String value) {
		if (field == null || field.length() == 0 || field.equals("contents")) {
			return new CompleteQuery(new TextPatternTerm(value), null);
		}

		return new CompleteQuery(null, new TermQuery(new Term(field, value)));
	}

	public CompleteQuery and(CompleteQuery other) {
		TextPattern a, b, c;
		Query d, e, f;
		BooleanQuery bq;

		a = contentsQuery;
		b = other.contentsQuery;
		if (a != null && b != null)
			c = new TextPatternDocLevelAnd(a, b);
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
