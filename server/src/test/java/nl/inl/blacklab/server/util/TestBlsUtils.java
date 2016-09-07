package nl.inl.blacklab.server.util;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.WildcardQuery;
import org.junit.Assert;
import org.junit.Test;

import nl.inl.blacklab.search.Searcher;
import nl.inl.blacklab.search.TextPattern;
import nl.inl.blacklab.search.TextPatternProperty;
import nl.inl.blacklab.search.TextPatternRegex;
import nl.inl.blacklab.search.TextPatternWildcard;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;

public class TestBlsUtils {

	Searcher searcher = new MockSearcher();

	@Test
	public void testIsValidIndexName() {
		Assert.assertTrue(BlsUtils.isValidIndexName("user@example.com:my_index_name"));
		Assert.assertTrue(BlsUtils.isValidIndexName("user@example.com:my-index-name1"));
		Assert.assertTrue(BlsUtils.isValidIndexName("user@example.com:a1"));
		Assert.assertFalse(BlsUtils.isValidIndexName("user@example.com:"));
		Assert.assertFalse(BlsUtils.isValidIndexName("user@example.com:bla:bla"));
		Assert.assertFalse(BlsUtils.isValidIndexName(""));
		Assert.assertFalse(BlsUtils.isValidIndexName("0abd"));
		Assert.assertFalse(BlsUtils.isValidIndexName("a*b"));
		Assert.assertFalse(BlsUtils.isValidIndexName("a/b"));
	}

	@Test
	public void testParsePatt() throws BlsException {
		TextPattern pattThe = new TextPatternRegex("^the$");
		Assert.assertEquals(pattThe, BlsUtils.parsePatt(searcher, "\"the\"", "corpusql"));
		Assert.assertEquals(pattThe, BlsUtils.parsePatt(searcher, "\"the\"", "corpusql", true));
	}

	@Test
	public void testParsePattContextQL() throws BlsException {
		TextPattern pattThe = new TextPatternProperty("word", new TextPatternWildcard("the"));
		Assert.assertEquals(pattThe, BlsUtils.parsePatt(searcher, "\"the\"", "contextql"));
	}

	@Test(expected=BadRequest.class)
	public void testParsePattWrongLanguage() throws BlsException {
		BlsUtils.parsePatt(searcher, "\"the\"", "swahili");
	}

	@Test(expected=BadRequest.class)
	public void testParsePattNoPattern() throws BlsException {
		BlsUtils.parsePatt(searcher, "", "corpusql", true);
	}

	@Test
	public void testParseFilter() throws BlsException {
		Query f = new TermQuery(new Term("author", "me"));
		Assert.assertEquals(f, BlsUtils.parseFilter(searcher, "author:me", "luceneql"));
		Assert.assertEquals(f, BlsUtils.parseFilter(searcher, "author:me", "luceneql", true));
	}

	@Test
	public void testParseFilterContextQl() throws BlsException {
		Query f = new WildcardQuery(new Term("author", "me"));
		Assert.assertEquals(f, BlsUtils.parseFilter(searcher, "author = me", "contextql"));
	}

	@Test(expected=BadRequest.class)
	public void testParseFilterWrongLanguage() throws BlsException {
		BlsUtils.parseFilter(searcher, "author:me", "corpusql");
	}

	@Test(expected=BadRequest.class)
	public void testParseFilterNoFilter() throws BlsException {
		BlsUtils.parseFilter(searcher, "", "luceneql", true);
	}

}
