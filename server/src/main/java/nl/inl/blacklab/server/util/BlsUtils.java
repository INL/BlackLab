package nl.inl.blacklab.server.util;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;

import nl.inl.blacklab.perdocument.DocResults;
import nl.inl.blacklab.queryParser.contextql.ContextualQueryLanguageParser;
import nl.inl.blacklab.queryParser.corpusql.CorpusQueryLanguageParser;
import nl.inl.blacklab.queryParser.corpusql.ParseException;
import nl.inl.blacklab.queryParser.corpusql.TokenMgrError;
import nl.inl.blacklab.search.CompleteQuery;
import nl.inl.blacklab.search.Searcher;
import nl.inl.blacklab.search.TextPattern;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.ServiceUnavailable;

public class BlsUtils {
	private static final Logger logger = LogManager.getLogger(BlsUtils.class);

	public static Query parseFilter(Searcher searcher, String filter,
			String filterLang) throws BlsException {
		return BlsUtils.parseFilter(searcher, filter, filterLang, false);
	}

	public static Query parseFilter(Searcher searcher, String filter,
			String filterLang, boolean required) throws BlsException {
		if (filter == null || filter.length() == 0) {
			if (required)
				throw new BadRequest("NO_FILTER_GIVEN",
						"Document filter required. Please specify 'filter' parameter.");
			return null; // not required
		}

		Analyzer analyzer = searcher.getAnalyzer();
		if (filterLang.equals("luceneql")) {
			try {
				QueryParser parser = new QueryParser("", analyzer);
				parser.setAllowLeadingWildcard(true);
				Query query = parser.parse(filter);
				return query;
			} catch (org.apache.lucene.queryparser.classic.ParseException e) {
				throw new BadRequest("FILTER_SYNTAX_ERROR",
						"Error parsing LuceneQL filter query: "
								+ e.getMessage());
			} catch (org.apache.lucene.queryparser.classic.TokenMgrError e) {
				throw new BadRequest("FILTER_SYNTAX_ERROR",
						"Error parsing LuceneQL filter query: "
								+ e.getMessage());
			}
		} else if (filterLang.equals("contextql")) {
			try {
				CompleteQuery q = ContextualQueryLanguageParser.parse(searcher,
						filter);
				return q.getFilterQuery();
			} catch (nl.inl.blacklab.queryParser.contextql.TokenMgrError e) {
				throw new BadRequest("FILTER_SYNTAX_ERROR",
						"Error parsing ContextQL filter query: "
								+ e.getMessage());
			} catch (nl.inl.blacklab.queryParser.contextql.ParseException e) {
				throw new BadRequest("FILTER_SYNTAX_ERROR",
						"Error parsing ContextQL filter query: "
								+ e.getMessage());
			}
		}

		throw new BadRequest("UNKNOWN_FILTER_LANG",
				"Unknown filter language '" + filterLang
						+ "'. Supported: luceneql, contextql.");
	}

	public static TextPattern parsePatt(Searcher searcher, String pattern,
			String language, boolean required) throws BlsException {
		if (pattern == null || pattern.length() == 0) {
			if (required)
				throw new BadRequest("NO_PATTERN_GIVEN",
						"Text search pattern required. Please specify 'patt' parameter.");
			return null; // not required, ok
		}

		if (language.equals("corpusql")) {
			try {
				return CorpusQueryLanguageParser.parse(pattern);
			} catch (ParseException e) {
				throw new BadRequest("PATT_SYNTAX_ERROR",
						"Syntax error in CorpusQL pattern: " + e.getMessage());
			} catch (TokenMgrError e) {
				throw new BadRequest("PATT_SYNTAX_ERROR",
						"Syntax error in CorpusQL pattern: " + e.getMessage());
			}
		} else if (language.equals("contextql")) {
			try {
				CompleteQuery q = ContextualQueryLanguageParser.parse(searcher,
						pattern);
				return q.getContentsQuery();
			} catch (nl.inl.blacklab.queryParser.contextql.TokenMgrError e) {
				throw new BadRequest("PATT_SYNTAX_ERROR",
						"Syntax error in ContextQL pattern: " + e.getMessage());
			} catch (nl.inl.blacklab.queryParser.contextql.ParseException e) {
				throw new BadRequest("PATT_SYNTAX_ERROR",
						"Syntax error in ContextQL pattern: " + e.getMessage());
			}
		}

		throw new BadRequest("UNKNOWN_PATT_LANG",
				"Unknown pattern language '" + language
						+ "'. Supported: corpusql, contextql, luceneql.");
	}

	public static TextPattern parsePatt(Searcher searcher, String pattern,
			String language) throws BlsException {
		return parsePatt(searcher, pattern, language, true);
	}

	/**
	 * Get the Lucene Document id given the pid
	 *
	 * @param searcher
	 *            our index
	 * @param pid
	 *            the pid string (or Lucene doc id if we don't use a pid)
	 * @return the document id, or -1 if it doesn't exist
	 */
	public static int getLuceneDocIdFromPid(Searcher searcher, String pid) {
		String pidField = searcher.getIndexStructure().pidField();
		if (pidField == null || pidField.length() == 0) {
			int luceneDocId;
			try {
				luceneDocId = Integer.parseInt(pid);
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException(
						"Pid must be a Lucene doc id, but it's not a number: "
								+ pid);
			}
			return luceneDocId;
		}
		boolean lowerCase = false; // HACK in case pid field is incorrectly
									// lowercased
		DocResults docResults;
		while (true) {
			String p = lowerCase ? pid.toLowerCase() : pid;
			TermQuery documentFilterQuery = new TermQuery(new Term(pidField, p));
			docResults = searcher.queryDocuments(documentFilterQuery);
			if (docResults.size() > 1) {
				// Should probably throw a fatal exception, but sometimes
				// documents accidentally occur twice in a dataset...
				// Make configurable whether or not a fatal exception is thrown
				logger.error("Pid must uniquely identify a document, but it has " + docResults.size() + " hits: " + pid);
			}
			if (docResults.size() == 0) {
				if (lowerCase)
					return -1; // tried with and without lowercasing; doesn't
								// exist
				lowerCase = true; // try lowercase now
			} else {
				// size == 1, found!
				break;
			}
		}
		return docResults.get(0).getDocId();
	}

	// Copied from Apache Commons
	// (as allowed under the Apache License 2.0)
	public static boolean isSymlink(File file) throws IOException {
		if (file == null)
			throw new NullPointerException("File must not be null");
		File canon;
		if (file.getParent() == null) {
			canon = file;
		} else {
			File canonDir = file.getParentFile().getCanonicalFile();
			canon = new File(canonDir, file.getName());
		}
		return !canon.getCanonicalFile().equals(canon.getAbsoluteFile());
	}

	/**
	 * Delete an entire tree with files, subdirectories, etc.
	 *
	 * CAREFUL, DANGEROUS!
	 *
	 * @param root
	 *            the directory tree to delete
	 */
	public static void delTree(File root) {
		if (!root.isDirectory())
			throw new IllegalArgumentException("Not a directory: " + root);
		for (File f : root.listFiles()) {
			if (f.isDirectory())
				delTree(f);
			else
				f.delete();
		}
		root.delete();
	}

	static void debugWait() throws BlsException {
		// Fake extra search time
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			throw new ServiceUnavailable("Debug wait interrupted");
		}
	}

	/**
	 * A file filter that returns readable directories only; used for scanning
	 * collections dirs
	 */
	public static FileFilter readableDirFilter = new FileFilter() {
		@Override
		public boolean accept(File f) {
			return f.isDirectory() && f.canRead();
		}
	};

	/**
	 * Check the index name part (not the user id part, if any)
	 * of the specified index name.
	 *
	 * @param indexName the index name, possibly including user id prefix
	 * @return whether or not the index name part is valid
	 */
	public static boolean isValidIndexName(String indexName) {
		if (indexName.contains(":")) {
			String[] parts = indexName.split(":", 2);
			indexName = parts[1];
		}
		return indexName.matches("[a-zA-Z][a-zA-Z0-9_\\-]*");
	}

}
