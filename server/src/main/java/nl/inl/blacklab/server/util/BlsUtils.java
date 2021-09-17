package nl.inl.blacklab.server.util;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.Files;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.queryParser.contextql.ContextualQueryLanguageParser;
import nl.inl.blacklab.queryParser.corpusql.CorpusQueryLanguageParser;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.CompleteQuery;
import nl.inl.blacklab.search.indexmetadata.FieldType;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.indexmetadata.MetadataFields;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.ServiceUnavailable;

public class BlsUtils {
    private static final Logger logger = LogManager.getLogger(BlsUtils.class);

    public static Query parseFilter(BlackLabIndex index, String filter,
            String filterLang) throws BlsException {
        return BlsUtils.parseFilter(index, filter, filterLang, false);
    }

    public static Query parseFilter(BlackLabIndex index, String filter,
            String filterLang, boolean required) throws BlsException {
        if (filter == null || filter.length() == 0) {
            if (required)
                throw new BadRequest("NO_FILTER_GIVEN",
                        "Document filter required. Please specify 'filter' parameter.");
            return null; // not required
        }

        Analyzer analyzer = index.analyzer();
        if (filterLang.equals("luceneql")) {
            try {
                // We need to override a couple of query implementations to allow searching on numeric fields
                // By default lucene will interpret everything as text, and thus not return any matches when
                // a query touches a field that is actually numeric.
                QueryParser parser = new QueryParser("", analyzer) {
                    @Override
                    protected org.apache.lucene.search.Query newRangeQuery(String field, String part1, String part2, boolean startInclusive, boolean endInclusive) {

                        MetadataField mf = index.metadata() != null ? index.metadataFields() != null ? index.metadataField(field) : null : null;
                        if (mf != null && FieldType.NUMERIC.equals(mf.type())) {
                            try {
                                return NumericRangeQuery.newIntRange(field, Integer.parseInt(part1), Integer.parseInt(part2), startInclusive, endInclusive);
                            } catch (NumberFormatException e) {
                                // there is nothing we can do here, just return the default implementation, which will likely return no results
                            }
                        }
                        return super.newRangeQuery(field, part1, part2, startInclusive, endInclusive);
                    }

                    @Override
                    protected org.apache.lucene.search.Query newFieldQuery(Analyzer analyzer, String field, String queryText, boolean quoted) throws ParseException {
                        MetadataField mf = index.metadata() != null ? index.metadataFields() != null ? index.metadataField(field) : null : null;
                        if (mf != null && FieldType.NUMERIC.equals(mf.type())) {
                            return newRangeQuery(field, queryText, queryText, true, true);
                        }

                        return super.newFieldQuery(analyzer, field, queryText, quoted);
                    }
                };
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
                CompleteQuery q = ContextualQueryLanguageParser.parse(index, filter);
                return q.filter();
            } catch (InvalidQuery e) {
                throw new BadRequest("FILTER_SYNTAX_ERROR",
                        "Error parsing ContextQL filter query: "
                                + e.getMessage());
            }
        }

        throw new BadRequest("UNKNOWN_FILTER_LANG",
                "Unknown filter language '" + filterLang
                        + "'. Supported: luceneql, contextql.");
    }

    public static TextPattern parsePatt(BlackLabIndex index, String pattern,
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
            } catch (InvalidQuery e) {
                throw new BadRequest("PATT_SYNTAX_ERROR",
                        "Syntax error in CorpusQL pattern: " + e.getMessage());
            }
        } else if (language.equals("contextql")) {
            try {
                CompleteQuery q = ContextualQueryLanguageParser.parse(index,
                        pattern);
                return q.pattern();
            } catch (InvalidQuery e) {
                throw new BadRequest("PATT_SYNTAX_ERROR",
                        "Syntax error in ContextQL pattern: " + e.getMessage());
            }
        }

        throw new BadRequest("UNKNOWN_PATT_LANG",
                "Unknown pattern language '" + language
                        + "'. Supported: corpusql, contextql, luceneql.");
    }

    public static TextPattern parsePatt(BlackLabIndex index, String pattern,
            String language) throws BlsException {
        return parsePatt(index, pattern, language, true);
    }

    /**
     * Get the Lucene Document id given the pid
     *
     * @param index our index
     * @param pid the pid string (or Lucene doc id if we don't use a pid)
     * @return the document id, or -1 if it doesn't exist
     */
    public static int getDocIdFromPid(BlackLabIndex index, String pid) {
        MetadataField pidField = index.metadataFields().special(MetadataFields.PID);
        if (pidField == null) {
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
            TermQuery documentFilterQuery = new TermQuery(new Term(pidField.name(), p));
            docResults = index.queryDocuments(documentFilterQuery);
            if (docResults.size() > 1) {
                // Should probably throw a fatal exception, but sometimes
                // documents accidentally occur twice in a dataset...
                // Make configurable whether or not a fatal exception is thrown
                logger.error(
                        "Pid must uniquely identify a document, but it has " + docResults.size() + " hits: " + pid);
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
        return docResults.get(0).identity().id();
    }

    // Copied from Apache Commons
    // (as allowed under the Apache License 2.0)
    public static boolean isSymlink(File file) throws IOException {
        if (file == null)
            throw new IOException("File must not be null");
        File canon;
        if (file.getParent() == null) {
            canon = file;
        } else {
            File canonDir = file.getAbsoluteFile().getParentFile().getCanonicalFile();
            canon = new File(canonDir, file.getName());
        }
        return !canon.getCanonicalFile().equals(canon.getAbsoluteFile());
    }

    /**
     * Delete an entire tree with files, subdirectories, etc.
     *
     * CAREFUL, DANGEROUS!
     *
     * @param root the directory tree to delete
     */
    public static void delTree(File root) {
        if (!root.isDirectory())
            throw new IllegalArgumentException("Not a directory: " + root);
        for (File f : root.listFiles()) {
            if (f.isDirectory())
                delTree(f);
            else
                try {
                    Files.delete(f.toPath());
                } catch (IOException e) {
                    logger.error(e.getMessage());
                }
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
     * Convert a number of seconds to a M:SS string.
     *
     * @param sec number of seconds
     * @return a string of the form M:SS, e.g. 1s, 5m or 12m34s
     */
    public static String describeIntervalSec(int sec) {
        int min = sec / 60;
        sec = sec % 60;
        if (min == 0)
            return sec + "s";
        if (sec == 0)
            return min + "m";
        return String.format("%dm%02ds", min, sec);
    }
}
